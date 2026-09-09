Add-Type -AssemblyName System.Drawing

# Build the Curios "loop" slot icon: the loop texture scaled to 16x16, flattened
# to a dark silhouette so it reads as an empty-slot hint.
$src = [System.Drawing.Bitmap]::new("$PSScriptRoot\hungry_loop.png")

$size = 16
$scaled = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($scaled)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $size, $size),
    (New-Object System.Drawing.Rectangle 0, 0, $src.Width, $src.Height),
    [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()

$out = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $a = $scaled.GetPixel($x, $y).A
        if ($a -lt 24) {
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        } else {
            # dark charcoal at ~55% of the source coverage
            $oa = [int]([math]::Min(255, $a * 0.55))
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($oa, 38, 38, 42))
        }
    }
}

$dest = "$PSScriptRoot\..\src\main\resources\assets\hungryloop\textures\slot\loop.png"
New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
$out.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "wrote $dest"

$out.Dispose(); $scaled.Dispose(); $src.Dispose()
