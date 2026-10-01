param (
    [switch]$DryRun,
    [switch]$Force
)

$HomeBinDir = Join-Path $HOME ".local\bin"
$UvBinaries = @("uv.exe", "uvx.exe", "uvw.exe")
$LocalAppDataUv = Join-Path $env:LOCALAPPDATA "uv"
$AppDataUv = Join-Path $env:APPDATA "uv"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   UV UNINSTALLER" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

Write-Host "[WHAT WILL BE DELETED]" -ForegroundColor Yellow
Write-Host " - Binaries: $($UvBinaries -join ', ') in $HomeBinDir"
Write-Host " - Cache and Data: $LocalAppDataUv"
Write-Host " - Tools and Python: $AppDataUv"

$Targets = @()
foreach ($bin in $UvBinaries) {
    $path = Join-Path $HomeBinDir $bin
    if (Test-Path $path) { $Targets += $path }
}
if (Test-Path $LocalAppDataUv) { $Targets += $LocalAppDataUv }
if (Test-Path $AppDataUv) { $Targets += $AppDataUv }

if ($Targets.Count -eq 0) {
    Write-Host "`n[INFO] No uv installation or data found. Nothing to uninstall." -ForegroundColor Gray
    exit
}

if ($DryRun) {
    Write-Host "`n[DRY RUN] The following items would be deleted:" -ForegroundColor Magenta
    foreach ($item in $Targets) {
        Write-Host " - $item"
    }
    exit
}

if (-not $Force) {
    $confirmation = Read-Host "`nAre you sure you want to uninstall uv and delete all its data? (Y/N)"
    if ($confirmation -ne 'Y') {
        Write-Host "[INFO] Operation cancelled." -ForegroundColor Gray
        exit
    }
}

Write-Host "`n[1/2] Stopping uv and python processes..." -ForegroundColor Yellow
$ProcessNames = @("uv", "python")
foreach ($name in $ProcessNames) {
    $procs = Get-Process -Name $name -ErrorAction SilentlyContinue
    if ($procs) {
        Write-Host " Killing $($procs.Count) $name process(es)..." -ForegroundColor Gray
        $procs | Stop-Process -Force -ErrorAction SilentlyContinue
    }
}
# Give OS a moment to release file handles
Start-Sleep -Seconds 2

Write-Host "[2/2] Uninstalling uv..." -ForegroundColor Yellow
foreach ($item in $Targets) {
    try {
        if (Test-Path $item) {
            Write-Host " Removing: $item" -ForegroundColor Gray
            Remove-Item -Path $item -Recurse -Force -ErrorAction Stop
        }
    }
    catch {
        Write-Host "[ERROR] Failed to remove $item" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
}

Write-Host "`n[SUCCESS] uv has been uninstalled." -ForegroundColor Green
