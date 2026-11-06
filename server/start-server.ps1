$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$MinecraftVersion = '1.21.4'
$ServerJar = Join-Path $PSScriptRoot "paper-$MinecraftVersion.jar"
$PluginsDir = Join-Path $PSScriptRoot 'plugins'
$RuntimeDir = Join-Path $PSScriptRoot 'runtime'
$JavaExe = $null

Write-Host ''
Write-Host '=== Astral Nexus RPG: local test server ===' -ForegroundColor Cyan
Write-Host 'The first start downloads Java 21 (if needed) and Paper.'
Write-Host ''

# Find a suitable Java 21+ runtime, otherwise install a private portable JRE.
$candidates = @()
$cmd = Get-Command java.exe -ErrorAction SilentlyContinue
if ($cmd) { $candidates += $cmd.Source }
if (Test-Path $RuntimeDir) {
    $candidates += Get-ChildItem -Path $RuntimeDir -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName }
}
foreach ($candidate in ($candidates | Select-Object -Unique)) {
    try {
        $versionText = (& $candidate -version 2>&1 | Out-String)
        if ($versionText -match 'version "(\d+)') {
            if ([int]$Matches[1] -ge 21) { $JavaExe = $candidate; break }
        }
    } catch {}
}

if (-not $JavaExe) {
    if ([Environment]::Is64BitOperatingSystem -eq $false) {
        throw 'This launcher requires 64-bit Windows to install its private Java 21 runtime.'
    }
    Write-Host '[1/4] Downloading portable Java 21...' -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
    $jreZip = Join-Path $env:TEMP 'astral-java21-jre.zip'
    Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse' -OutFile $jreZip
    Expand-Archive -LiteralPath $jreZip -DestinationPath $RuntimeDir -Force
    Remove-Item $jreZip -Force
    $java = Get-ChildItem -Path $RuntimeDir -Filter java.exe -Recurse | Select-Object -First 1
    if (-not $java) { throw 'Java 21 download completed, but java.exe was not found.' }
    $JavaExe = $java.FullName
} else {
    Write-Host "[1/4] Java is ready: $JavaExe" -ForegroundColor Green
}

# Download the latest Paper build for the plugin's target Minecraft version.
if (-not (Test-Path $ServerJar)) {
    Write-Host '[2/4] Downloading Paper server 1.21.4...' -ForegroundColor Yellow
    $versionInfo = Invoke-RestMethod -Uri "https://api.papermc.io/v2/projects/paper/versions/$MinecraftVersion"
    $build = $versionInfo.builds[-1]
    $jarName = "paper-$MinecraftVersion-$build.jar"
    $downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/$MinecraftVersion/builds/$build/downloads/$jarName"
    Invoke-WebRequest -Uri $downloadUrl -OutFile $ServerJar
} else {
    Write-Host '[2/4] Paper server already downloaded.' -ForegroundColor Green
}

New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null
$pluginJar = Get-ChildItem -Path $PluginsDir -Filter '*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pluginJar) {
    Write-Host ''
    Write-Host 'The Astral Nexus plugin JAR is missing from the plugins folder.' -ForegroundColor Red
    Write-Host 'Download the portable server package from GitHub Actions; it includes the plugin.'
    throw 'Missing plugin JAR.'
}

# Use safe local defaults. No public port forwarding is configured.
$properties = Join-Path $PSScriptRoot 'server.properties'
if (-not (Test-Path $properties)) {
    @(
        'motd=Astral Nexus RPG - Local Test World'
        'server-port=25565'
        'server-ip='
        'online-mode=true'
        'max-players=8'
        'view-distance=6'
        'simulation-distance=4'
        'spawn-protection=0'
        'enable-command-block=false'
        'white-list=false'
    ) | Set-Content -Path $properties -Encoding ASCII
}

$eulaFile = Join-Path $PSScriptRoot 'eula.txt'
if (-not (Test-Path $eulaFile) -or (Get-Content $eulaFile -Raw) -notmatch 'eula=true') {
    Write-Host ''
    Write-Host 'Minecraft EULA must be accepted before the server can start.' -ForegroundColor Yellow
    Write-Host 'Read it here: https://www.minecraft.net/eula'
    $answer = Read-Host 'Type I AGREE only if you accept the Minecraft EULA'
    if ($answer -cne 'I AGREE') {
        Set-Content -Path $eulaFile -Value 'eula=false' -Encoding ASCII
        Write-Host 'EULA was not accepted. Server was not started.'
        exit 0
    }
    Set-Content -Path $eulaFile -Value 'eula=true' -Encoding ASCII
}

Write-Host ''
Write-Host '[3/4] Starting Paper...' -ForegroundColor Green
Write-Host '[4/4] When "Done" appears, open Minecraft 1.21.4 and connect to localhost.'
Write-Host 'To stop the server, type stop in this window.'
Write-Host ''
& $JavaExe -Xms1G -Xmx2G -jar $ServerJar --nogui
if ($LASTEXITCODE -ne 0) { throw "Paper server exited with code $LASTEXITCODE." }
