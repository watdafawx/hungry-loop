# Turns the source ring art into the item texture:
#   1. flood-fills the near-black background to transparent from the 4 corners
#   2. downscales to 16x16 (and a 32x32 copy) with nearest-neighbour
#
# Usage:  pwsh -File art/process-texture.ps1 art\ring-source.png
param([string]$Source = "$PSScriptRoot\ring-source.png")

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Bitmap]::new($Source)
$w = $src.Width; $h = $src.Height
$argb = [System.Drawing.Bitmap]::new($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($argb); $g.DrawImage($src, 0, 0, $w, $h); $g.Dispose()

function IsBlackish($c) { $c.R -lt 24 -and $c.G -lt 24 -and $c.B -lt 24 }

# Flood fill from every edge pixel, clearing connected near-black.
$stack = [System.Collections.Generic.Stack[object]]::new()
for ($x = 0; $x -lt $w; $x++) { $stack.Push(@($x, 0)); $stack.Push(@($x, $h - 1)) }
for ($y = 0; $y -lt $h; $y++) { $stack.Push(@(0, $y)); $stack.Push(@($w - 1, $y)) }
$seen = New-Object 'bool[,]' $w, $h

while ($stack.Count -gt 0) {
    $p = $stack.Pop(); $x = $p[0]; $y = $p[1]
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $w -or $y -ge $h -or $seen[$x, $y]) { continue }
    $seen[$x, $y] = $true
    if (-not (IsBlackish $argb.GetPixel($x, $y))) { continue }
    $argb.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $stack.Push(@($x + 1, $y)); $stack.Push(@($x - 1, $y))
    $stack.Push(@($x, $y + 1)); $stack.Push(@($x, $y - 1))
}

function SaveScaled($size, $path) {
    $out = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gg = [System.Drawing.Graphics]::FromImage($out)
    $gg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $gg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $gg.DrawImage($argb, 0, 0, $size, $size)
    $gg.Dispose()
    $out.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Host "wrote $path ($size x $size)"
}

$tex = "$PSScriptRoot\..\src\main\resources\assets\hungryloop\textures\item"
SaveScaled 16 "$tex\hungry_loop.png"
SaveScaled 32 "$PSScriptRoot\hungry_loop_32.png"
$argb.Save("$PSScriptRoot\ring-nobg.png", [System.Drawing.Imaging.ImageFormat]::Png)
$src.Dispose(); $argb.Dispose()
