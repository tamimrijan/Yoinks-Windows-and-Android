package com.tamimrijan.yoinks.engine

import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.os.Environment
import com.tamimrijan.yoinks.data.History
import com.tamimrijan.yoinks.data.HistoryEntry
import com.tamimrijan.yoinks.data.Prefs
import com.tamimrijan.yoinks.util.Format
import com.tamimrijan.yoinks.util.Platforms
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The yt-dlp side of Yoinks (port of the original yoinks' lib/ytdlp.ts):
 * probe a link, offer resolutions + mp3, download with live progress, cancel cleanly.
 * One yoink at a time, like the original.
 */
class Engine(
    private val context: Context,
    private val prefs: Prefs,
    private val history: History,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _phase = MutableStateFlow<Phase>(Phase.Input())
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Starting)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    @Volatile private var ready = CompletableDeferred<Unit>()
    private val updater = YtDlpUpdater(context)
    private val updateLock = kotlinx.coroutines.sync.Mutex()
    private var job: Job? = null
    private var infoJson: File? = null
    private var currentUrl = ""
    private var currentProcess: String? = null

    val downloadDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Yoinks")

    val isBusy get() = _phase.value is Phase.Probing || _phase.value is Phase.Downloading

    /** Unpacks Python, yt-dlp, FFmpeg and QuickJS on first launch (a few seconds), then stays ready. */
    fun start() {
        // after a failed start, a new attempt gets a fresh "ready" signal
        if (ready.isCompleted && _status.value is EngineStatus.Failed) ready = CompletableDeferred()
        if (_status.value is EngineStatus.Ready) return
        _status.value = EngineStatus.Starting
        val signal = ready
        scope.launch {
            try {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                _status.value = EngineStatus.Ready
                signal.complete(Unit)
                _version.value = updater.installedVersion()
                // YouTube breaks old yt-dlp versions often: check for a new one once a day
                if (System.currentTimeMillis() - updater.lastCheck > 20 * 60 * 60 * 1000L) {
                    runCatching { updateYtDlp() }.onFailure { android.util.Log.w("Yoinks", "auto update failed", it) }
                }
            } catch (t: Throwable) {
                android.util.Log.e("Yoinks", "engine failed to start", t)
                _status.value = EngineStatus.Failed(describe(t))
                signal.completeExceptionally(t)
            }
        }
    }

    /** For screenshot tests and previews. */
    fun showForPreview(phase: Phase, version: String = "2025.11.12") {
        _phase.value = phase
        _status.value = EngineStatus.Ready
        _version.value = version
    }

    // ---------------------------------------------------------------- probe

    fun submit(raw: String) {
        val url = Platforms.extractUrl(raw)
        if (url == null || !Platforms.isProbablyUrl(url)) {
            _phase.value = Phase.Input(
                if (raw.isBlank()) "Paste a link first." else "That doesn't look like a link. Paste the full address, starting with https://"
            )
            return
        }
        probe(url)
    }

    private fun probe(url: String) {
        cancelJob()
        if (_status.value is EngineStatus.Failed) start() // try the engine again instead of staying broken
        currentUrl = url
        val platform = Platforms.detect(url)
        _phase.value = Phase.Probing(url, platform, "warming up…")
        job = scope.launch {
            try {
                ready.await()
                _phase.value = Phase.Probing(url, platform, "fetching video info…")
                val id = "probe-${System.nanoTime()}"
                currentProcess = id
                val request = YoutubeDLRequest(url).apply {
                    addOption("-J")
                    addOption("--no-playlist")
                    addOption("--no-warnings")
                }
                val response = YoutubeDL.getInstance().execute(request, id)
                val info = Choices.parseInfo(response.out)
                infoJson?.delete()
                infoJson = File(context.cacheDir, "info-${System.currentTimeMillis()}.json").also { it.writeText(response.out) }
                _phase.value = Phase.Picking(url, platform, info, Choices.build(info))
            } catch (t: Throwable) {
                if (t is YoutubeDL.CanceledException || t is kotlinx.coroutines.CancellationException) return@launch
                _phase.value = Phase.Error(cleanError(t), canRetryDownload = false)
            } finally {
                currentProcess = null
            }
        }
    }

    // ---------------------------------------------------------------- download

    fun download(choice: DownloadChoice) {
        val picking = _phase.value as? Phase.Picking ?: (lastPicking ?: return)
        lastPicking = picking
        if (prefs.state.value.wifiOnly && isMetered()) {
            _phase.value = Phase.Error("Wi-Fi only is on in Settings, and you're on mobile data.", canRetryDownload = true)
            return
        }
        cancelJob()
        _phase.value = Phase.Downloading(picking.url, picking.platform, picking.info, choice)
        job = scope.launch {
            val outDir = downloadDir.apply { mkdirs() }
            try {
                ready.await()
                val file = try {
                    // reuse the probe's metadata - starts right away instead of re-extracting
                    runDownload(picking, choice, outDir, useCachedInfo = true)
                } catch (t: Throwable) {
                    if (t is YoutubeDL.CanceledException || t is kotlinx.coroutines.CancellationException) throw t
                    // media links in the cached info can expire: retry with a fresh extraction
                    _phase.update { (it as? Phase.Downloading)?.copy(progress = null, refreshing = true) ?: it }
                    try {
                        runDownload(picking, choice, outDir, useCachedInfo = false)
                    } catch (t2: Throwable) {
                        if (t2 is YoutubeDL.CanceledException || t2 is kotlinx.coroutines.CancellationException) throw t2
                        // "HTTP Error 403" usually means YouTube changed and yt-dlp is out of date:
                        // update it and try one last time
                        if (!(t2.message ?: "").contains("403")) throw t2
                        _phase.update { (it as? Phase.Downloading)?.copy(progress = null, refreshing = false, note = "YouTube blocked it. Updating the downloader…") ?: it }
                        val updated = runCatching { updater.update() }.getOrNull()
                        if (updated !is YtDlpUpdater.Result.Updated) throw t2
                        _version.value = updated.to
                        _phase.update { (it as? Phase.Downloading)?.copy(note = "Updated. Trying again…") ?: it }
                        runDownload(picking, choice, outDir, useCachedInfo = false)
                    }
                }
                withContext(Dispatchers.IO) {
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
                val entry = HistoryEntry(
                    url = picking.url,
                    title = picking.info.title,
                    uploader = picking.info.uploader,
                    platform = picking.platform.label,
                    thumbnail = picking.info.thumbnail,
                    filePath = file.absolutePath,
                    choice = if (choice.isAudio) "MP3" else "${choice.label} MP4",
                    isAudio = choice.isAudio,
                    sizeBytes = file.length(),
                )
                history.add(entry)
                infoJson?.delete(); infoJson = null
                _phase.value = Phase.Done(entry)
            } catch (t: Throwable) {
                if (t is YoutubeDL.CanceledException || t is kotlinx.coroutines.CancellationException) return@launch
                _phase.value = Phase.Error(cleanError(t), canRetryDownload = true)
            }
        }
    }

    private var lastPicking: Phase.Picking? = null

    private val progressRegex = Regex(
        """\[download]\s+([\d.]+)%\s+of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)(?:\s+at\s+([\d.]+)\s*([KMGT]?i?B)/s)?(?:\s+ETA\s+([\d:]+))?""",
    )

    private fun runDownload(p: Phase.Picking, choice: DownloadChoice, outDir: File, useCachedInfo: Boolean): File {
        val cached = infoJson?.takeIf { useCachedInfo && it.exists() }
        val request = if (cached != null) YoutubeDLRequest(emptyList<String>()).addOption("--load-info-json", cached.absolutePath)
        else YoutubeDLRequest(p.url)
        choice.args.forEach { (k, v) -> if (v == null) request.addOption(k) else request.addOption(k, v) }
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        request.addOption("--newline")
        request.addOption("--no-quiet")
        request.addOption("--progress")
        request.addOption("--print", "after_move:filepath")
        request.addOption("--no-simulate")
        request.addOption("--no-mtime")
        request.addOption("--windows-filenames") // Android storage rejects : ? * | in names
        request.addOption("-o", File(outDir, "%(title).80s.%(ext)s").absolutePath)

        var totalParts = 1
        val destinations = mutableListOf<String>()
        var lastFraction = -1f
        var part = 0

        val id = "dl-${System.nanoTime()}"
        currentProcess = id
        try {
            val response = YoutubeDL.getInstance().execute(request, id) { _, _, line ->
                when {
                    line.contains("Downloading 1 format(s):") ->
                        totalParts = line.substringAfter("format(s):").trim().split('+').size.coerceAtLeast(1)
                    line.startsWith("[download] Destination: ") -> {
                        destinations += line.removePrefix("[download] Destination: ")
                        part = (destinations.size - 1).coerceIn(0, totalParts - 1)
                    }
                    line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") || line.startsWith("[VideoConvertor]") || line.startsWith("[FixupM3u8]") -> {
                        Regex("""^\[Merger] Merging formats into "(.+)"$""").find(line)?.let { destinations += it.groupValues[1] }
                        Regex("""^\[ExtractAudio] Destination: (.+)$""").find(line)?.let { destinations += it.groupValues[1] }
                        _phase.update { (it as? Phase.Downloading)?.copy(processing = true) ?: it }
                    }
                    else -> progressRegex.find(line)?.let { m ->
                        val pct = m.groupValues[1].toFloatOrNull() ?: return@let
                        val total = Format.parseSize(m.groupValues[2], m.groupValues[3])
                        val speed = Format.parseSize(m.groupValues[4].ifEmpty { null }, m.groupValues[5].ifEmpty { null })
                        val eta = m.groupValues[6].takeIf { it.isNotEmpty() }?.split(':')?.fold(0L) { acc, s -> acc * 60 + (s.toLongOrNull() ?: 0) }
                        val f = (pct / 100f).coerceIn(0f, 1f)
                        if (f + 0.2f < lastFraction && part < totalParts - 1 && destinations.size <= part + 1) part++
                        lastFraction = f
                        _phase.update {
                            (it as? Phase.Downloading)?.copy(
                                // the last file hit 100%: what's left is merging / converting to mp3
                                processing = f >= 0.999f && part >= totalParts - 1,
                                refreshing = false,
                                note = null,
                                progress = Progress(f, (total ?: 0.0) * f, total, speed, eta, part, totalParts),
                            ) ?: it
                        }
                    }
                }
            }
            val path = response.out.lines().map { it.trim() }.lastOrNull { it.startsWith("/") }
                ?: throw IllegalStateException("yt-dlp finished but didn't say where it saved the file.")
            return File(path)
        } catch (t: Throwable) {
            if (t is YoutubeDL.CanceledException) removePartials(destinations)
            throw t
        } finally {
            currentProcess = null
        }
    }

    // ---------------------------------------------------------------- cancel / reset

    /** Stops whatever is running. Keeps the link so a cancel isn't destructive. */
    fun cancel() {
        cancelJob()
        _phase.value = Phase.Input()
    }

    fun backToPicker() {
        lastPicking?.let { _phase.value = it } ?: reset()
    }

    fun retry() {
        val p = _phase.value
        if (p is Phase.Error && p.canRetryDownload) {
            val d = lastDownloadChoice
            if (d != null) { download(d); return }
        }
        if (currentUrl.isNotEmpty()) probe(currentUrl) else reset()
    }

    private val lastDownloadChoice: DownloadChoice?
        get() = (lastStarted as? Phase.Downloading)?.choice

    private var lastStarted: Phase? = null

    init {
        // remember the last download so "Try again" can repeat it
        scope.launch {
            phase.collect { if (it is Phase.Downloading) lastStarted = it; if (it is Phase.Picking) lastPicking = it }
        }
    }

    fun reset() {
        cancelJob()
        infoJson?.delete(); infoJson = null
        lastPicking = null
        _phase.value = Phase.Input()
    }

    val lastUrl get() = currentUrl

    private fun cancelJob() {
        currentProcess?.let { runCatching { YoutubeDL.getInstance().destroyProcessById(it) } }
        currentProcess = null
        job?.cancel()
        job = null
    }

    private fun removePartials(destinations: List<String>) {
        destinations.flatMap { listOf(it, "$it.part", "$it.ytdl") }.forEach { runCatching { File(it).delete() } }
    }

    // ---------------------------------------------------------------- updates

    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        ready.await()
        updateLock.withLockCompat {
            when (val r = updater.update()) {
                is YtDlpUpdater.Result.Updated -> { _version.value = r.to; "Updated to ${r.to}" }
                is YtDlpUpdater.Result.UpToDate -> { _version.value = r.version; "Already the newest version (${r.version})" }
            }
        }
    }

    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockCompat(block: suspend () -> T): T {
        lock()
        try { return block() } finally { unlock() }
    }

    private fun isMetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    /** Full cause chain ("YoutubeDLException: failed to initialize ← IOException: …") so a failure is never blank. */
    private fun describe(t: Throwable): String =
        generateSequence(t) { it.cause }.take(4)
            .joinToString(" ← ") { "${it.javaClass.simpleName}${it.message?.let { m -> ": $m" } ?: ""}" }
            .take(500)

    private fun cleanError(t: Throwable): String {
        // a failed engine start surfaces here when you try to yoink: say what actually broke
        if (t is YoutubeDL.CanceledException) return "Cancelled."
        val raw = t.message ?: return describe(t)
        val last = raw.lines().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }
        val msg = last?.replace(Regex("""^ERROR:\s*(\[[^]]+]\s*)?"""), "") ?: raw.lines().firstOrNull { it.isNotBlank() } ?: raw
        return when {
            msg.contains("Unable to resolve host", true) || msg.contains("Failed to resolve", true) -> "No internet connection. Check your Wi-Fi or data and try again."
            msg.contains("Unsupported URL", true) -> "Yoinks can't download from this link. Try the video's own page."
            msg.contains("HTTP Error 403", true) -> "The site refused the download (HTTP 403). Open Settings and tap Update next to yt-dlp, then try again."
            else -> msg.take(400)
        }
    }
}
