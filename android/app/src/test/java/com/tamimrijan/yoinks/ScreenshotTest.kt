package com.tamimrijan.yoinks

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamimrijan.yoinks.data.HistoryEntry
import com.tamimrijan.yoinks.data.ThemeMode
import com.tamimrijan.yoinks.engine.Choices
import com.tamimrijan.yoinks.engine.Phase
import com.tamimrijan.yoinks.engine.Progress
import com.tamimrijan.yoinks.engine.RawFormat
import com.tamimrijan.yoinks.engine.VideoInfo
import com.tamimrijan.yoinks.ui.MainActivity
import com.tamimrijan.yoinks.util.Platform
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens to PNGs (app/build/outputs/roborazzi) so the design can be checked
 * without a phone. Run: gradlew recordRoborazziDebug
 * The download engine (Python, FFmpeg) only runs on a phone, so each phase is set up by hand.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {
    @get:org.junit.Rule val compose = createEmptyComposeRule()

    private val app get() = YoinksApp.instance
    private val yt = Platform("youtube", "YouTube")
    private val info = VideoInfo(
        title = "Me at the zoo",
        uploader = "jawed",
        duration = 19.0,
        thumbnail = null,
        formats = listOf(
            RawFormat("137", "mp4", "avc1", "none", 1080, null, 4400.0, 52_000_000.0),
            RawFormat("136", "mp4", "avc1", "none", 720, null, 2500.0, 27_000_000.0),
            RawFormat("135", "mp4", "avc1", "none", 480, null, 1200.0, 13_000_000.0),
            RawFormat("140", "m4a", "none", "mp4a", null, 129.0, 129.0, 3_100_000.0),
        ),
    )

    private fun seed(dark: Boolean) {
        app.prefs.update { it.copy(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, watchClipboard = false) }
        val now = System.currentTimeMillis()
        // real (empty) files so the cards don't say "moved or deleted"
        fun file(name: String) = java.io.File(app.cacheDir, name).apply { writeText("x") }.absolutePath
        app.history.replaceAll(
            listOf(
                HistoryEntry("https://youtu.be/a", "Lo-fi beats to study and relax to (1 hour mix)", "Chillhop", "YouTube", null, file("Lo-fi beats.mp3"), "MP3", true, 58_000_000, now - 120_000),
                HistoryEntry("https://youtu.be/b", "Me at the zoo", "jawed", "YouTube", null, file("Me at the zoo.mp4"), "720p MP4", false, 3_400_000, now - 3_600_000),
                HistoryEntry("https://instagram.com/p/c", "Sunset timelapse over the bay", "tamim_ahmed_rijan", "Instagram", null, file("Sunset timelapse.mp4"), "1080p MP4", false, 24_000_000, now - 86_400_000 * 2),
            ),
        )
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(2_500)
        compose.onRoot().captureRoboImage("screens/$name.png")
    }

    private fun tab(route: String) {
        // tabs are switched with the clock running: a paused clock stalls the page transition
        compose.onNode(hasTestTag("tab_$route")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun liveShot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/$name.png")
    }

    private fun run(dark: Boolean, suffix: String) {
        seed(dark)
        app.engine.showForPreview(Phase.Input())
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.mainClock.autoAdvance = false
            shot("01_home$suffix")

            app.engine.showForPreview(Phase.Probing("https://www.youtube.com/watch?v=jNQXAC9IVRw", yt, "fetching video info…"))
            shot("02_probing$suffix")

            app.engine.showForPreview(Phase.Picking("https://youtu.be/jNQXAC9IVRw", yt, info, Choices.build(info)))
            shot("03_pick$suffix")

            val choice = Choices.build(info)[1]
            app.engine.showForPreview(
                Phase.Downloading("https://youtu.be/jNQXAC9IVRw", yt, info, choice, Progress(0.4f, 10_800_000.0, 27_000_000.0, 3_400_000.0, 6, 0, 2)),
            )
            shot("04_downloading$suffix")

            app.engine.showForPreview(Phase.Done(app.history.items.value[1]))
            shot("05_done$suffix")

            app.engine.showForPreview(Phase.Error("This video is private. Only the uploader can see it.", canRetryDownload = false))
            shot("06_error$suffix")

            app.engine.showForPreview(Phase.Input())
            compose.mainClock.autoAdvance = true
            tab("downloads"); liveShot("07_downloads$suffix")
            tab("settings"); liveShot("08_settings$suffix")
            tab("about"); liveShot("09_about$suffix")
        }
    }

    @Test fun light() = run(dark = false, suffix = "")

    @Test fun dark() = run(dark = true, suffix = "_dark")
}
