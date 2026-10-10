# scripts/start-emulators.ps1
# Khoi dong hai Android emulator de test chat real-time tren mot may.
#
# Cach dung (chay tu thu muc goc repo):
#   .\scripts\start-emulators.ps1
#
# GPU mac dinh da chon qua thu nghiem thuc te (2026-10-09):
#   m2       -> angle_indirect  (host xung dot khi co 2 emulator dong thoi)
#   Pixel_7  -> host            (Quadro P1000, boot ~92s)
# De ghi de GPU mode:
#   .\scripts\start-emulators.ps1 -Gpu1 host -Gpu2 host
#
# Yeu cau: ANDROID_HOME hoac %LOCALAPPDATA%\Android\Sdk phai ton tai.

param(
    [string]$Avd1  = "m2",
    [string]$Avd2  = "Pixel_7",
    [int]$Port1    = 5554,
    [int]$Port2    = 5556,
    [string]$Gpu1  = "angle_indirect",
    [string]$Gpu2  = "host"
)

$ErrorActionPreference = "Stop"

$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$emu = Join-Path $sdkRoot "emulator\emulator.exe"
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"

if (-not (Test-Path $emu)) {
    Write-Error "Khong tim thay emulator tai: $emu"
    exit 1
}

Write-Host "=== SkillExchange Emulator Launcher ===" -ForegroundColor Cyan
Write-Host "AVD 1 : $Avd1  port=$Port1  gpu=$Gpu1"
Write-Host "AVD 2 : $Avd2  port=$Port2  gpu=$Gpu2"
Write-Host ""

$avdList = & $emu -list-avds 2>&1
foreach ($name in @($Avd1, $Avd2)) {
    if ($avdList -notcontains $name) {
        Write-Error "AVD '$name' khong ton tai. Chay 'emulator -list-avds' de kiem tra."
        exit 1
    }
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Khoi dong $Avd1 (port $Port1, gpu=$Gpu1)..." -ForegroundColor Yellow
$launch1 = Get-Date
$proc1 = Start-Process -FilePath $emu `
    -ArgumentList "-avd $Avd1 -port $Port1 -no-boot-anim -no-audio -dns-server 8.8.8.8,8.8.4.4 -gpu $Gpu1" `
    -PassThru -WindowStyle Normal

Start-Sleep -Seconds 6

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Khoi dong $Avd2 (port $Port2, gpu=$Gpu2)..." -ForegroundColor Yellow
$launch2 = Get-Date
$proc2 = Start-Process -FilePath $emu `
    -ArgumentList "-avd $Avd2 -port $Port2 -no-boot-anim -no-audio -dns-server 8.8.8.8,8.8.4.4 -gpu $Gpu2" `
    -PassThru -WindowStyle Normal

Write-Host ""
Write-Host "Dang cho boot xong (sys.boot_completed=1, toi da 5 phut)..."
Write-Host "Nhan Ctrl+C de huy cho - emulator van tiep tuc chay."
Write-Host ""

function Wait-EmulatorBoot {
    param([string]$Serial, [DateTime]$StartTime, [string]$Label)
    $timeout = 300; $elapsed = 0
    while ($elapsed -lt $timeout) {
        try {
            $r = (& $adb -s $Serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
            if ($r -eq "1") {
                $dur = [math]::Round(((Get-Date) - $StartTime).TotalSeconds, 1)
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Label boot xong: ${dur}s" -ForegroundColor Green
                return $dur
            }
        } catch {}
        Start-Sleep -Seconds 3; $elapsed += 3
    }
    Write-Warning "$Label chua boot xong sau ${timeout}s."
    return -1
}

Start-Sleep -Seconds 8
$t1 = Wait-EmulatorBoot -Serial "emulator-$Port1" -StartTime $launch1 -Label $Avd1
$t2 = Wait-EmulatorBoot -Serial "emulator-$Port2" -StartTime $launch2 -Label $Avd2

$freeGB = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB, 2)

Write-Host ""
Write-Host "=== KET QUA ===" -ForegroundColor Cyan
Write-Host "Boot $Avd1  (port $Port1): $(if ($t1 -ge 0) { "${t1}s" } else { 'timeout' })"
Write-Host "Boot $Avd2  (port $Port2): $(if ($t2 -ge 0) { "${t2}s" } else { 'timeout' })"
Write-Host "RAM con trong             : ${freeGB} GB"
Write-Host ""
Write-Host "ADB serials: emulator-$Port1  emulator-$Port2"
Write-Host "Cai app   : adb -s emulator-$Port1 install android\app\build\outputs\apk\debug\app-debug.apk"