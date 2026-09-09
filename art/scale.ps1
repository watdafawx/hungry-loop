Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Bitmap]::new("$PSScriptRoot\hungry_loop.png")

function Scale([int]$size, $mode, [string]$path) {
    $out = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = $mode
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $dst = New-Object System.Drawing.Rectangle 0, 0, $size, $size
    $srcRect = New-Object System.Drawing.Rectangle 0, 0, $src.Width, $src.Height
    $g.DrawImage($src, $dst, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $out.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Host "wrote $path ($size x $size)"
}

$tex = "$PSScriptRoot\..\src\main\resources\assets\hungryloop\textures\item\hungry_loop.png"
Scale 32 ([System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic) $tex
Scale 16 ([System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic) "$PSScriptRoot\hungry_loop_16.png"
$src.Dispose()
