param(
    [Parameter(Mandatory = $true)][string]$SourceIcon,
    [Parameter(Mandatory = $true)][string]$OutputIcon,
    [string]$PreviewPng
)

Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName System.Drawing

$decoder = [System.Windows.Media.Imaging.IconBitmapDecoder]::new(
    [Uri]::new((Resolve-Path -LiteralPath $SourceIcon).Path),
    [System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,
    [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
)
$sourceFrame = $decoder.Frames | Where-Object { $_.PixelWidth -eq 256 } | Select-Object -First 1
if ($null -eq $sourceFrame) { throw 'The source icon has no 256px frame.' }

$sourcePng = [System.Windows.Media.Imaging.PngBitmapEncoder]::new()
$sourcePng.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($sourceFrame))
$sourceStream = [IO.MemoryStream]::new()
$sourcePng.Save($sourceStream)
$sourceStream.Position = 0
$sourceBitmap = [System.Drawing.Bitmap]::new($sourceStream)
$result = [System.Drawing.Bitmap]::new(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

try {
    # Preserve the source icon's rounded-square silhouette and transparency,
    # replacing only the oversized Q with a centered 84%-scale version.
    for ($y = 0; $y -lt 256; $y++) {
        for ($x = 0; $x -lt 256; $x++) {
            $pixel = $sourceBitmap.GetPixel($x, $y)
            $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, 13, 15, 17))
        }
    }
    $graphics = [System.Drawing.Graphics]::FromImage($result)
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($sourceBitmap, [System.Drawing.Rectangle]::new(20, 20, 216, 216))
    } finally { $graphics.Dispose() }

    $sizes = @(256, 128, 64, 48, 32, 16)
    $frames = foreach ($size in $sizes) {
        $scaled = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($scaled)
        try {
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.DrawImage($result, [System.Drawing.Rectangle]::new(0, 0, $size, $size))
        } finally { $graphics.Dispose() }
        $stream = [IO.MemoryStream]::new()
        try {
            $scaled.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
            if ($size -eq 256 -and $PreviewPng) {
                [IO.File]::WriteAllBytes($PreviewPng, $stream.ToArray())
            }
            [pscustomobject]@{ Size = $size; Bytes = $stream.ToArray() }
        } finally { $stream.Dispose(); $scaled.Dispose() }
    }

    $iconStream = [IO.MemoryStream]::new()
    $writer = [IO.BinaryWriter]::new($iconStream)
    try {
        $writer.Write([uint16]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]$frames.Count)
        $offset = 6 + 16 * $frames.Count
        foreach ($frame in $frames) {
            $dimension = if ($frame.Size -eq 256) { 0 } else { $frame.Size }
            $writer.Write([byte]$dimension)
            $writer.Write([byte]$dimension)
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Write([uint16]1)
            $writer.Write([uint16]32)
            $writer.Write([uint32]$frame.Bytes.Length)
            $writer.Write([uint32]$offset)
            $offset += $frame.Bytes.Length
        }
        foreach ($frame in $frames) { $writer.Write([byte[]]$frame.Bytes) }
        [IO.File]::WriteAllBytes($OutputIcon, $iconStream.ToArray())
    } finally { $writer.Dispose(); $iconStream.Dispose() }
} finally {
    $result.Dispose()
    $sourceBitmap.Dispose()
    $sourceStream.Dispose()
}
