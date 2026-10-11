param(
    [string]$CharacterTag,
    [string]$Dictionary,
    [Alias('Work')][string[]]$Copyright = @(),
    [int]$DiscoveryPages = 50,
    [int]$MaxCharacters = 100,
    [switch]$Solo,
    [string[]]$IncludeTag = @(),
    [string[]]$ExcludeTag = @(),
    [ValidateSet('server', 'local')][string]$FilterMode = 'server',
    [ValidateSet('score', 'upvotes', 'favcount')][string]$Sort = 'score',
    [ValidateSet('general')][string]$Rating = 'general',
    [string]$OutputDirectory,
    [switch]$Refresh
)
$ErrorActionPreference = 'Stop'
$interactiveLaunch = -not $CharacterTag -and -not $Dictionary -and -not $Copyright.Count
try {
    if ($interactiveLaunch) {
        $CharacterTag = Read-Host 'Character tag (example: amiya_(arknights); Enter to fetch by work)'
        $copyrightInput = Read-Host 'Copyright/work tags (ANY, comma-separated; example: arknights,blue_archive)'
        if (-not [string]::IsNullOrWhiteSpace($copyrightInput)) { $Copyright = @($copyrightInput) }
        if ([string]::IsNullOrWhiteSpace($CharacterTag) -and -not $Copyright.Count) { exit 0 }
        if (-not $IncludeTag.Count) {
            $requiredInput = Read-Host 'Required tags (ALL, comma-separated; Enter for none)'
            if (-not [string]::IsNullOrWhiteSpace($requiredInput)) { $IncludeTag = @($requiredInput) }
        }
        if (-not $ExcludeTag.Count) {
            $excludedInput = Read-Host 'Excluded tags (ANY, comma-separated; Enter for none)'
            if (-not [string]::IsNullOrWhiteSpace($excludedInput)) { $ExcludeTag = @($excludedInput) }
        }
        $localFiltering = Read-Host 'Filter returned metadata locally? Useful for account tag limits (y/N)'
        if ($localFiltering -match '^(y|yes)$') { $FilterMode = 'local' }
    }
    $pythonExe = $null
    $pythonPrefix = @()
    $pyCommand = Get-Command py.exe -ErrorAction SilentlyContinue
    $pythonCommand = Get-Command python.exe -ErrorAction SilentlyContinue
    $bundledPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
    if ($pyCommand) {
        $pythonExe = $pyCommand.Source
        $pythonPrefix = @('-3')
    } elseif ($pythonCommand -and $pythonCommand.Source -notlike '*\WindowsApps\*') {
        $pythonExe = $pythonCommand.Source
    } elseif (Test-Path -LiteralPath $bundledPython) {
        $pythonExe = $bundledPython
    } else {
        throw 'Python 3.10+ is required. Install Python, then run this script again.'
    }
    $fetchArgs = @((Join-Path $PSScriptRoot 'fetch_danbooru_previews.py'), '--sort', $Sort, '--rating', $Rating)
    if ($Dictionary) {
        $fetchArgs += @('--dictionary', $Dictionary)
    } elseif ($CharacterTag) {
        $fetchArgs += @('--tag', $CharacterTag)
    }
    $fetchArgs += @('--max-characters', [string]$MaxCharacters, '--discovery-pages', [string]$DiscoveryPages)
    foreach ($copyrightTag in $Copyright) { $fetchArgs += @('--copyright', $copyrightTag) }
    if ($Solo) { $fetchArgs += '--solo' }
    foreach ($filterTag in $IncludeTag) { $fetchArgs += @('--include-tag', $filterTag) }
    foreach ($filterTag in $ExcludeTag) { $fetchArgs += @('--exclude-tag', $filterTag) }
    $fetchArgs += @('--filter-mode', $FilterMode)
    if ($Refresh) { $fetchArgs += '--refresh' }
    if (-not $OutputDirectory) {
        $projectRoot = Split-Path $PSScriptRoot -Parent
        if ((Split-Path $projectRoot -Leaf) -eq 'source') { $projectRoot = Split-Path $projectRoot -Parent }
        $OutputDirectory = Join-Path $projectRoot 'datasets\danbooru-character-previews'
    }
    $fetchArgs += @('--output', $OutputDirectory)
    & $pythonExe @pythonPrefix @fetchArgs
    $fetchExitCode = $LASTEXITCODE
    if ($interactiveLaunch) { Read-Host 'Press Enter to close' | Out-Null }
    exit $fetchExitCode
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($interactiveLaunch) { Read-Host 'Press Enter to close' | Out-Null }
    exit 2
}
