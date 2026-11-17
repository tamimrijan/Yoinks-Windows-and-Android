<#
  Builds Yoinks and its installer.

    powershell -ExecutionPolicy Bypass -File .\build.ps1

  Needs the .NET 8 SDK (dotnet) and Inno Setup 6 (ISCC.exe).
  On first run it downloads yt-dlp.exe and an FFmpeg build into vendor\ (cached).
  Output: dist\app (the app) and dist\Yoinks-Setup-<version>-x64.exe (the installer)
#>
param(
    [string]$Version = "1.0.0",
    [string]$Dotnet = "",
    [string]$Iscc = "",
    [switch]$RefreshTools,
    [switch]$SkipInstaller
)
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = $PSScriptRoot

function Find-Tool($explicit, $name, $candidates) {
    if ($explicit) { return $explicit }
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "$name not found. Install it or pass its path (see README)."
}

$Dotnet = Find-Tool $Dotnet "dotnet" @(
    "$env:USERPROFILE\.droppdf-buildtools\dotnet\dotnet.exe",
    "$env:USERPROFILE\.dotnet\dotnet.exe",
    "$env:ProgramFiles\dotnet\dotnet.exe")
$Iscc = Find-Tool $Iscc "ISCC" @(
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "$env:ProgramFiles\Inno Setup 6\ISCC.exe")

# ---------- third-party tools bundled with the app ----------
$vendor = Join-Path $root "vendor"
$tools = Join-Path $vendor "tools"
if ($RefreshTools -and (Test-Path $tools)) { Remove-Item $tools -Recurse -Force }
New-Item -ItemType Directory -Force $tools | Out-Null

$ytdlp = Join-Path $tools "yt-dlp.exe"
if (-not (Test-Path $ytdlp)) {
    Write-Host "==> Downloading yt-dlp" -ForegroundColor Cyan
    Invoke-WebRequest -UseBasicParsing "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe" -OutFile $ytdlp
}

if (-not (Test-Path (Join-Path $tools "ffmpeg.exe"))) {
    Write-Host "==> Downloading FFmpeg (yt-dlp build, shared)" -ForegroundColor Cyan
    $zip = Join-Path $vendor "ffmpeg.zip"
    Invoke-WebRequest -UseBasicParsing "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip" -OutFile $zip
    $tmp = Join-Path $vendor "ffmpeg-extract"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive $zip $tmp
    $bin = Get-ChildItem $tmp -Recurse -Directory -Filter bin | Select-Object -First 1
    Get-ChildItem $bin.FullName | Where-Object { $_.Name -in "ffmpeg.exe", "ffprobe.exe" -or $_.Extension -eq ".dll" } |
        Copy-Item -Destination $tools
    $lic = Get-ChildItem $tmp -Recurse -Filter "LICENSE*" | Select-Object -First 1
    if ($lic) { Copy-Item $lic.FullName (Join-Path $tools "FFmpeg-LICENSE.txt") }
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
}

# ---------- app ----------
$dist = Join-Path $root "dist"
$app = Join-Path $dist "app"
if (Test-Path $app) { Remove-Item $app -Recurse -Force }

Write-Host "==> Publishing Yoinks $Version" -ForegroundColor Cyan
& $Dotnet publish (Join-Path $root "src\Yoinks\Yoinks.csproj") `
    -c Release -r win-x64 -p:Platform=x64 -p:Version=$Version -o $app --nologo
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed" }

# leave out debug symbols and unused artwork, add the tools and licenses
Get-ChildItem $app -Recurse -Include *.pdb, AppIcon-512.png | Remove-Item -Force
Copy-Item $tools (Join-Path $app "tools") -Recurse
Copy-Item (Join-Path $root "LICENSE") (Join-Path $app "LICENSE.txt")

if ($SkipInstaller) { Write-Host "==> Done: $app" -ForegroundColor Green; return }

Write-Host "==> Building installer" -ForegroundColor Cyan
& $Iscc "/DAppVersion=$Version" (Join-Path $root "installer\Yoinks.iss")
if ($LASTEXITCODE -ne 0) { throw "ISCC failed" }

Get-ChildItem $dist -Filter "Yoinks-Setup-*.exe" | ForEach-Object {
    Write-Host ("==> Done: {0} ({1:N1} MB)" -f $_.FullName, ($_.Length / 1MB)) -ForegroundColor Green
}
