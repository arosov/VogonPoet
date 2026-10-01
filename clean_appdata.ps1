param (
    [switch]$DryRun,
    [switch]$Force
)

$TargetDir = Join-Path $env:APPDATA "VogonPoet"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   VOGON POET DATA CLEANER" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Target: $TargetDir`n" -ForegroundColor Gray

Write-Host "[WHAT WILL BE DELETED]" -ForegroundColor Yellow
Write-Host " - Configuration files (vogon.config.json, babelfish.config.json)"
Write-Host " - Application logs"
Write-Host " - Custom wakeword models (openwakeword_models)"
Write-Host "`n[WHAT WILL BE PRESERVED]" -ForegroundColor Green
Write-Host " - The application Cache (Python environment, Downloaded ASR Models)"
Write-Host " - The application binaries`n"

if (-not (Test-Path -Path $TargetDir)) {
    Write-Host "[INFO] Target directory does not exist. Nothing to clean." -ForegroundColor Gray
    exit
}

if ($DryRun) {
    Write-Host "[DRY RUN] The following files would be deleted:" -ForegroundColor Magenta
    Get-ChildItem -Path $TargetDir -Recurse | Select-Object -ExpandProperty FullName
    exit
}

if (-not $Force) {
    $confirmation = Read-Host "Are you sure you want to delete this directory? (Y/N)"
    if ($confirmation -ne 'Y') {
        Write-Host "[INFO] Operation cancelled." -ForegroundColor Gray
        exit
    }
}

Write-Host "[1/1] Removing directory..." -ForegroundColor Yellow
try {
    Remove-Item -Path $TargetDir -Recurse -Force -ErrorAction Stop
    Write-Host "[SUCCESS] Directory cleared." -ForegroundColor Green
}
catch {
    Write-Host "[ERROR] Failed to delete directory. Ensure VogonPoet is closed." -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}
