param(
    [Parameter(Mandatory = $true)]
    [string]$IconPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory
)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName PresentationCore
$ErrorActionPreference = 'Stop'

$iconDecoder = [System.Windows.Media.Imaging.IconBitmapDecoder]::new(
    [Uri]::new((Resolve-Path -LiteralPath $IconPath).Path),
    [System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,
    [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
)
$iconFrame = $iconDecoder.Frames | Sort-Object PixelWidth -Descending | Select-Object -First 1
$iconEncoder = [System.Windows.Media.Imaging.PngBitmapEncoder]::new()
$iconEncoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($iconFrame))
$iconStream = [IO.MemoryStream]::new()
$iconEncoder.Save($iconStream)
$iconStream.Position = 0
$iconBitmap = [System.Drawing.Bitmap]::new($iconStream)

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function New-QTalkArtwork {
    param(
        [int]$Width,
        [int]$Height,
        [string]$OutputPath,
        [bool]$IncludeWordmark
    )

    $bitmap = New-Object System.Drawing.Bitmap $Width, $Height
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $background = [System.Drawing.Color]::FromArgb(11, 13, 16)
    $accent = [System.Drawing.Color]::FromArgb(77, 113, 203)
    $graphics.Clear($background)

    $accentBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(34, $accent))
    $graphics.FillEllipse($accentBrush, -[int]($Width * 0.55), [int]($Height * 0.48), [int]($Width * 1.6), [int]($Width * 1.6))

    $iconSize = if ($IncludeWordmark) { [int]($Width * 0.56) } else { [int]($Width * 0.72) }
    $iconX = [int](($Width - $iconSize) / 2)
    $iconY = if ($IncludeWordmark) { [int]($Height * 0.23) } else { [int](($Height - $iconSize) / 2) }
    $graphics.DrawImage($iconBitmap, $iconX, $iconY, $iconSize, $iconSize)

    if ($IncludeWordmark) {
        $fontSize = [Math]::Max(16, [int]($Width * 0.15))
        $font = New-Object System.Drawing.Font 'Segoe UI', $fontSize, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
        $textBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(242, 244, 248))
        $format = New-Object System.Drawing.StringFormat
        $format.Alignment = [System.Drawing.StringAlignment]::Center
        $graphics.DrawString('QTalk', $font, $textBrush, (New-Object System.Drawing.RectangleF 0, ($iconY + $iconSize + 14), $Width, 40), $format)
        $format.Dispose()
        $textBrush.Dispose()
        $font.Dispose()
    }

    $accentBrush.Dispose()
    $graphics.Dispose()
    $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bitmap.Dispose()
}

New-QTalkArtwork -Width 164 -Height 314 -OutputPath (Join-Path $OutputDirectory 'wizard-large.bmp') -IncludeWordmark $true
New-QTalkArtwork -Width 55 -Height 55 -OutputPath (Join-Path $OutputDirectory 'wizard-small.bmp') -IncludeWordmark $false
$iconBitmap.Dispose()
$iconStream.Dispose()
