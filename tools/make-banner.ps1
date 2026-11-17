<#
  Draws docs\banner.png (1280x640, GitHub social preview size) for the repo.
    powershell -ExecutionPolicy Bypass -File .\tools\make-banner.ps1
#>
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root "docs\banner.png"
New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null

$W = 1280; $H = 640
$pink = [Drawing.Color]::FromArgb(255, 0xFF, 0x37, 0x5F)
$purple = [Drawing.Color]::FromArgb(255, 0xAF, 0x52, 0xDE)
$blue = [Drawing.Color]::FromArgb(255, 0x0A, 0x84, 0xFF)

$bmp = New-Object Drawing.Bitmap $W, $H
$g = [Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = "AntiAlias"; $g.InterpolationMode = "HighQualityBicubic"; $g.TextRenderingHint = "AntiAliasGridFit"
$g.Clear([Drawing.Color]::FromArgb(255, 0x10, 0x10, 0x16))

# soft colour glows, like the app's ambient background
foreach ($o in @(@(-180, -260, 760, $pink), @(700, -300, 820, $purple), @(380, 300, 760, $blue))) {
    $path = New-Object Drawing.Drawing2D.GraphicsPath; $path.AddEllipse([float]$o[0], [float]$o[1], [float]$o[2], [float]$o[2])
    $pb = New-Object Drawing.Drawing2D.PathGradientBrush $path
    $c = $o[3]; $pb.CenterColor = [Drawing.Color]::FromArgb(120, $c.R, $c.G, $c.B)
    $pb.SurroundColors = @([Drawing.Color]::FromArgb(0, $c.R, $c.G, $c.B))
    $g.FillPath($pb, $path)
}

# frosted glass card
function RR([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object Drawing.Drawing2D.GraphicsPath; $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90); $p.CloseFigure(); $p
}
$card = RR 90 110 1100 420 44
$g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(34, 255, 255, 255))), $card)
$g.DrawPath((New-Object Drawing.Pen ([Drawing.Color]::FromArgb(70, 255, 255, 255)), 1.5), $card)

$icon = [Drawing.Image]::FromFile((Join-Path $root "src\Yoinks\Assets\AppIcon-512.png"))
$g.DrawImage($icon, 150, 170, 300, 300)

# pixel wordmark
$rows = @("+.+.###.###.##.#.#.#.###", "+.+.#.#..#..#.##.#.#.#..", "###.#.+..+..#..+.+#..##+", ".#..#.+..+..#..+.+.#...+", ".#..###.###.#..#.#.#.###")
$cell = 26; $x0 = 510; $y0 = 200
for ($r = 0; $r -lt 5; $r++) { for ($c = 0; $c -lt 24; $c++) {
    $ch = $rows[$r][$c]; if ($ch -eq '.') { continue }
    $t = ($c + $r * 0.6) / (23 + 3)
    if ($t -lt 0.5) { $a = $pink; $b = $purple; $k = $t / 0.5 } else { $a = $purple; $b = $blue; $k = ($t - 0.5) / 0.5 }
    $col = [Drawing.Color]::FromArgb($(if ($ch -eq '+') { 110 } else { 255 }), [int]($a.R + ($b.R - $a.R) * $k), [int]($a.G + ($b.G - $a.G) * $k), [int]($a.B + ($b.B - $a.B) * $k))
    $g.FillPath((New-Object Drawing.SolidBrush $col), (RR ($x0 + $c * $cell) ($y0 + $r * $cell) ($cell - 4) ($cell - 4) 5))
} }

$white = New-Object Drawing.SolidBrush ([Drawing.Color]::White)
$soft = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(190, 255, 255, 255))
$g.DrawString("yoink any video. paste. yoink. done.", (New-Object Drawing.Font "Segoe UI Semibold", 30, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)), $white, 508, 352)
$dot = "  " + [char]0x00B7 + "  "   # kept out of the source so PowerShell 5.1's ANSI parsing can't mangle it
$g.DrawString(("YouTube", "X", "Instagram", "Threads", "TikTok", "1,800+ sites" -join $dot), (New-Object Drawing.Font "Segoe UI", 21, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)), $soft, 510, 404)
$g.DrawString("Free Windows app" + $dot + "by Tamim Ahmed Rijan", (New-Object Drawing.Font "Segoe UI", 19, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)), $soft, 510, 446)

$g.Dispose()
$bmp.Save($out, [Drawing.Imaging.ImageFormat]::Png)
"wrote $out"
