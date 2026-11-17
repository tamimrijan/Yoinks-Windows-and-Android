<#
  Draws the Yoinks app icon and the installer artwork.
    powershell -ExecutionPolicy Bypass -File .\tools\make-art.ps1
  Writes src\Yoinks\Assets\{Yoinks.ico, AppIcon-256.png, AppIcon-512.png}
  and installer\art\wizard-*.bmp
#>
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$assets = Join-Path $root "src\Yoinks\Assets"
$art = Join-Path $root "installer\art"
New-Item -ItemType Directory -Force $assets, $art | Out-Null

$pink = [Drawing.Color]::FromArgb(255, 0xFF, 0x37, 0x5F)
$purple = [Drawing.Color]::FromArgb(255, 0xAF, 0x52, 0xDE)
$blue = [Drawing.Color]::FromArgb(255, 0x0A, 0x84, 0xFF)

function New-Squircle([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function Set-Brand([Drawing.Drawing2D.LinearGradientBrush]$b) {
    $blend = New-Object Drawing.Drawing2D.ColorBlend 3
    $blend.Colors = @($pink, $purple, $blue)
    $blend.Positions = @([float]0, [float]0.5, [float]1)
    $b.InterpolationColors = $blend
}

function Draw-Icon([int]$size) {
    $bmp = New-Object Drawing.Bitmap $size, $size, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = "AntiAlias"; $g.InterpolationMode = "HighQualityBicubic"; $g.PixelOffsetMode = "HighQuality"
    $g.Clear([Drawing.Color]::Transparent)

    # "Claw" mark, drawn on a 120-unit grid: an arcade claw grabbing a play button
    $k = [float]($size / 120.0)
    function P([float]$x, [float]$y) { [Drawing.PointF]::new($x * $k, $y * $k) }

    $shape = New-Squircle (4 * $k) (4 * $k) (112 * $k) (112 * $k) (28 * $k)
    $g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 0x17, 0x17, 0x1D))), $shape)

    # faint glass sheen + rim so it doesn't vanish on dark taskbars
    $g.SetClip($shape)
    $sheen = New-Object Drawing.Drawing2D.LinearGradientBrush (P 0 4), (P 0 70), ([Drawing.Color]::FromArgb(38, 255, 255, 255)), ([Drawing.Color]::FromArgb(0, 255, 255, 255))
    $g.FillEllipse($sheen, [float](-30 * $k), [float](-66 * $k), [float](180 * $k), [float](126 * $k))
    $g.ResetClip()
    if ($size -ge 32) {
        $g.DrawPath((New-Object Drawing.Pen ([Drawing.Color]::FromArgb(60, 255, 255, 255)), ([float][Math]::Max(1, 1.2 * $k))), $shape)
    }

    $white = New-Object Drawing.SolidBrush ([Drawing.Color]::White)
    $g.FillPath($white, (New-Squircle (56 * $k) (10 * $k) (8 * $k) (22 * $k) (3 * $k)))
    $g.FillPath($white, (New-Squircle (42 * $k) (29 * $k) (36 * $k) (13 * $k) (6.4 * $k)))

    $pen = New-Object Drawing.Pen ([Drawing.Color]::White), ([float](7 * $k))
    $pen.StartCap = "Round"; $pen.EndCap = "Round"; $pen.LineJoin = "Round"
    foreach ($side in @(1, -1)) {
        function X([float]$x) { if ($side -eq 1) { $x } else { 120 - $x } }
        $prong = New-Object Drawing.Drawing2D.GraphicsPath
        $prong.AddBezier((P (X 48) 41), (P (X 30) 52), (P (X 30) 76), (P (X 44) 89))
        $prong.AddLine((P (X 44) 89), (P (X 51) 84))
        $g.DrawPath($pen, $prong)
    }

    # the prize: a play button in the brand gradient
    $tri = New-Object Drawing.Drawing2D.GraphicsPath
    $tri.AddPolygon(@((P 52 55), (P 74 68), (P 52 81)))
    $grad = New-Object Drawing.Drawing2D.LinearGradientBrush (P 46 50), (P 80 86), $pink, $blue
    Set-Brand $grad
    $g.FillPath($grad, $tri)
    $gp = New-Object Drawing.Pen $grad, ([float](6 * $k))
    $gp.LineJoin = "Round"
    $g.DrawPath($gp, $tri)

    $g.Dispose()
    return $bmp
}

function Save-Png($bmp, $path) { $bmp.Save($path, [Drawing.Imaging.ImageFormat]::Png) }

# ---- app icon ----
Save-Png (Draw-Icon 256) (Join-Path $assets "AppIcon-256.png")
Save-Png (Draw-Icon 512) (Join-Path $assets "AppIcon-512.png")

$sizes = 16, 20, 24, 32, 40, 48, 64, 128, 256
$pngs = foreach ($sz in $sizes) {
    $ms = New-Object IO.MemoryStream
    (Draw-Icon $sz).Save($ms, [Drawing.Imaging.ImageFormat]::Png)
    , $ms.ToArray()
}
$ico = New-Object IO.MemoryStream
$w = New-Object IO.BinaryWriter $ico
$w.Write([UInt16]0); $w.Write([UInt16]1); $w.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $sz = $sizes[$i]; $data = $pngs[$i]
    $w.Write([byte]($(if ($sz -ge 256) { 0 } else { $sz })))
    $w.Write([byte]($(if ($sz -ge 256) { 0 } else { $sz })))
    $w.Write([byte]0); $w.Write([byte]0)
    $w.Write([UInt16]1); $w.Write([UInt16]32)
    $w.Write([UInt32]$data.Length); $w.Write([UInt32]$offset)
    $offset += $data.Length
}
foreach ($data in $pngs) { $w.Write($data) }
$w.Flush()
[IO.File]::WriteAllBytes((Join-Path $assets "Yoinks.ico"), $ico.ToArray())

# ---- installer art ----
$wordmark = @(
    "+.+.###.###.##.#.#.#.###",
    "+.+.#.#..#..#.##.#.#.#..",
    "###.#.+..+..#..+.+#..##+",
    ".#..#.+..+..#..+.+.#...+",
    ".#..###.###.#..#.#.#.###"
)

function Save-Bmp24($bmp, $path) {
    $out = New-Object Drawing.Bitmap $bmp.Width, $bmp.Height, ([Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [Drawing.Graphics]::FromImage($out); $g.DrawImage($bmp, 0, 0, $bmp.Width, $bmp.Height); $g.Dispose()
    $out.Save($path, [Drawing.Imaging.ImageFormat]::Bmp)
}

function Draw-Side([int]$w, [int]$h, [float]$k) {
    $bmp = New-Object Drawing.Bitmap $w, $h
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = "AntiAlias"; $g.InterpolationMode = "HighQualityBicubic"; $g.TextRenderingHint = "AntiAliasGridFit"
    $bg = New-Object Drawing.Drawing2D.LinearGradientBrush ([Drawing.PointF]::new(0, 0)), ([Drawing.PointF]::new($w, $h)), $pink, $blue
    Set-Brand $bg
    $g.FillRectangle($bg, 0, 0, $w, $h)

    # soft glass orbs
    foreach ($o in @(@(-0.3, -0.1, 1.0, 120), @(0.35, 0.55, 0.9, 70), @(-0.2, 0.75, 0.7, 60))) {
        $ox = [float]($w * $o[0]); $oy = [float]($h * $o[1]); $od = [float]($w * $o[2] * 1.4)
        $path = New-Object Drawing.Drawing2D.GraphicsPath; $path.AddEllipse($ox, $oy, $od, $od)
        $pb = New-Object Drawing.Drawing2D.PathGradientBrush $path
        $pb.CenterColor = [Drawing.Color]::FromArgb([int]$o[3], 255, 255, 255)
        $pb.SurroundColors = @([Drawing.Color]::FromArgb(0, 255, 255, 255))
        $g.FillPath($pb, $path)
    }

    # frosted card with the icon
    $cw = [float](104 * $k); $cx = [float](($w - $cw) / 2); $cy = [float](70 * $k)
    $card = New-Squircle $cx $cy $cw $cw ([float](28 * $k))
    $g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(56, 255, 255, 255))), $card)
    $g.DrawPath((New-Object Drawing.Pen ([Drawing.Color]::FromArgb(130, 255, 255, 255)), ([float](1.2 * $k))), $card)
    $icon = Draw-Icon ([int](80 * $k))
    $g.DrawImage($icon, [float]($cx + 12 * $k), [float]($cy + 12 * $k))

    # pixel wordmark
    $cell = [float](5.4 * $k); $wmW = $cell * 24
    $wx = [float](($w - $wmW) / 2); $wy = [float](206 * $k)
    for ($r = 0; $r -lt 5; $r++) {
        for ($c = 0; $c -lt 24; $c++) {
            $ch = $wordmark[$r][$c]
            if ($ch -eq '.') { continue }
            $a = $(if ($ch -eq '+') { 110 } else { 255 })
            $tile = New-Squircle ($wx + $c * $cell) ($wy + $r * $cell) ($cell * 0.86) ($cell * 0.86) ($cell * 0.2)
            $g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb($a, 255, 255, 255))), $tile)
        }
    }
    $font = New-Object Drawing.Font "Segoe UI", ([float](9 * $k)), ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
    $sf = New-Object Drawing.StringFormat; $sf.Alignment = "Center"
    $g.DrawString("for Windows", $font, (New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(230, 255, 255, 255))), ([Drawing.RectangleF]::new(0, [float](246 * $k), $w, [float](20 * $k))), $sf)
    $g.Dispose()
    return $bmp
}

Save-Bmp24 (Draw-Side 164 314 1) (Join-Path $art "wizard-side-1x.bmp")
Save-Bmp24 (Draw-Side 328 628 2) (Join-Path $art "wizard-side-2x.bmp")

function Draw-Small([int]$s) {
    $bmp = New-Object Drawing.Bitmap $s, $s
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.Clear([Drawing.Color]::White)
    $g.InterpolationMode = "HighQualityBicubic"
    $g.DrawImage((Draw-Icon $s), 0, 0, $s, $s)
    $g.Dispose()
    return $bmp
}
Save-Bmp24 (Draw-Small 55) (Join-Path $art "wizard-small-1x.bmp")
Save-Bmp24 (Draw-Small 110) (Join-Path $art "wizard-small-2x.bmp")

Write-Host "Artwork written to $assets and $art" -ForegroundColor Green
