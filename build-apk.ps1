# AutoTextTapper - build APK without Android Studio
# Run: powershell -ExecutionPolicy Bypass -File .\build-apk.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

$GradleVersion = "8.7"
$GradleZipName = "gradle-$GradleVersion-bin.zip"
$GradleLocalDir = "$ProjectRoot\.gradle-local"
$GradleZip = "$GradleLocalDir\$GradleZipName"
$GradleHome = "$GradleLocalDir\gradle-$GradleVersion"
$GradleBat = "$GradleHome\bin\gradle.bat"
$MinZipBytes = 130MB

function Test-Command($name) {
    return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

function Test-ZipFile($path) {
    if (-not (Test-Path $path)) { return $false }
    if ((Get-Item $path).Length -lt $MinZipBytes) { return $false }
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
        $zip.Dispose()
        return $true
    } catch {
        return $false
    }
}

function Download-GradleZip {
    param([string]$OutFile)

    New-Item -ItemType Directory -Force -Path $GradleLocalDir | Out-Null

    $urls = @(
        "https://services.gradle.org/distributions/$GradleZipName",
        "https://downloads.gradle.org/distributions/$GradleZipName"
    )

    foreach ($url in $urls) {
        Write-Host "Trying download: $url" -ForegroundColor Yellow

        if (Test-Command curl.exe) {
            # Resume supported (-C -). Large file friendly.
            & curl.exe -L -C - --retry 5 --retry-delay 3 -o $OutFile $url
            if ($LASTEXITCODE -eq 0 -and (Test-ZipFile $OutFile)) {
                return
            }
        }

        if (Test-Path $OutFile) {
            Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
        }

        try {
            $client = New-Object System.Net.WebClient
            $client.DownloadFile($url, $OutFile)
            if (Test-ZipFile $OutFile) {
                return
            }
        } catch {
            Write-Host "Download failed from $url : $($_.Exception.Message)" -ForegroundColor Yellow
        }

        if (Test-Path $OutFile) {
            Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
        }
    }

    throw @"
Could not download Gradle zip.

MANUAL FIX:
1) Download this file in Chrome/Edge:
   https://services.gradle.org/distributions/$GradleZipName
2) Save EXACTLY here:
   $OutFile
3) File size should be about 128-135 MB
4) Run this script again
"@
}

function Ensure-GradleInstalled {
    if (Test-Path $GradleBat) {
        return
    }

    if (-not (Test-ZipFile $GradleZip)) {
        if (Test-Path $GradleZip) {
            Write-Host "Removing broken Gradle zip..."
            Remove-Item $GradleZip -Force -ErrorAction SilentlyContinue
        }
        Download-GradleZip -OutFile $GradleZip
    }

    Write-Host "Extracting Gradle..."
    if (Test-Path $GradleHome) {
        Remove-Item $GradleHome -Recurse -Force
    }
    Expand-Archive -Path $GradleZip -DestinationPath $GradleLocalDir -Force
}

Write-Host "=== AutoTextTapper APK Build ===" -ForegroundColor Cyan

# 1) Java
if (-not (Test-Command java)) {
    Write-Host "ERROR: Java not found." -ForegroundColor Red
    Write-Host "Install JDK 17: https://adoptium.net/temurin/releases/?version=17"
    exit 1
}

$javaVersion = & {
    $ErrorActionPreference = "Continue"
    java -version 2>&1 | Select-Object -First 1
}
Write-Host "Java: $javaVersion"

# 2) Android SDK
$sdkPath = $null
$candidatePaths = @(
    "C:\Android\Sdk",
    "$env:LOCALAPPDATA\Android\Sdk",
    $env:ANDROID_HOME
) | Where-Object { $_ -and (Test-Path $_) }

if ($candidatePaths.Count -gt 0) {
    $sdkPath = $candidatePaths[0]
}

if (-not $sdkPath) {
    Write-Host "ERROR: Android SDK not found at C:\Android\Sdk" -ForegroundColor Red
    exit 1
}

Write-Host "SDK: $sdkPath"
$env:ANDROID_HOME = $sdkPath
$sdkEscaped = $sdkPath -replace '\\', '\\'
@"
sdk.dir=$sdkEscaped
"@ | Set-Content -Path "$ProjectRoot\local.properties" -Encoding UTF8

# 3) Gradle (extract locally; no gradlew download)
Ensure-GradleInstalled
Write-Host "Gradle: $GradleBat"

# 4) Build directly with extracted Gradle
Write-Host "Building debug APK..." -ForegroundColor Yellow
& $GradleBat assembleDebug --no-daemon --stacktrace

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "BUILD FAILED on this PC." -ForegroundColor Red
    Write-Host "Your internet/SSL is blocking downloads." -ForegroundColor Yellow
    Write-Host "Use ONLINE BUILD instead (no local download):" -ForegroundColor Yellow
    Write-Host "1) Create free GitHub account"
    Write-Host "2) Upload this project to GitHub"
    Write-Host "3) Open Actions tab -> Build APK -> Run workflow"
    Write-Host "4) Download APK from Artifacts"
    exit $LASTEXITCODE
}

$apk = "$ProjectRoot\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Host ""
    Write-Host "SUCCESS! APK ready:" -ForegroundColor Green
    Write-Host $apk
} else {
    Write-Host "Build finished but APK not found." -ForegroundColor Yellow
}
