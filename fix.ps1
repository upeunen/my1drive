param (
    [string] = "D:\My1drive\Android\app\src\main\java\by\w6\my1drive\ui\GalleryScreen.kt"
)
$content = [System.IO.File]::ReadAllText($FilePath, [System.Text.Encoding]::UTF8)
$searchStart = "fun FullscreenPreview("
$searchEnd = "@Composable
fun InfoDialog("
$startIdx = $content.IndexOf($searchStart)
$endIdx = $content.IndexOf($searchEnd, $startIdx + 1000)
if ($startIdx -lt 0 -or $endIdx -lt 0) { Write-Host "Not found!"; exit 1 }
Write-Host "Found: $startIdx -> $endIdx"
