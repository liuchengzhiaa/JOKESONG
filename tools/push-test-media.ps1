param(
    [string]$AdbPath = "F:\AndroidSDK\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$testMediaPath = Join-Path $repositoryRoot "local-test-media\ASMR_Test"

if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw "adb was not found: $AdbPath"
}

if (-not (Test-Path -LiteralPath $testMediaPath)) {
    throw "Local test media was not found: $testMediaPath"
}

$deviceLines = & $AdbPath devices
$deviceSerials = @(
    $deviceLines |
        Select-Object -Skip 1 |
        ForEach-Object {
            if ($_ -match "^([^\s]+)\s+device$") { $Matches[1] }
        }
)

if ($deviceSerials.Count -ne 1) {
    throw "Exactly one running and authorized device is required; found $($deviceSerials.Count)."
}

$deviceSerial = $deviceSerials[0]
& $AdbPath -s $deviceSerial shell mkdir -p /sdcard/Music
if ($LASTEXITCODE -ne 0) {
    throw "Unable to prepare /sdcard/Music on the device."
}

& $AdbPath -s $deviceSerial push $testMediaPath /sdcard/Music/
if ($LASTEXITCODE -ne 0) {
    throw "Failed to import test media."
}

Write-Host "Test media imported to /sdcard/Music/ASMR_Test"
