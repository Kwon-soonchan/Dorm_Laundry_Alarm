param(
    [switch]$PromptForWebhook
)

$ErrorActionPreference = 'Stop'
$env:GRADLE_USER_HOME = Join-Path $env:LOCALAPPDATA 'DormLaundryAlarm\gradle'

if ($PromptForWebhook -and -not $env:DISCORD_WEBHOOK_URL) {
    $secureWebhook = Read-Host 'Paste the Discord webhook URL' -AsSecureString
    $webhookPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureWebhook)
    try {
        $env:DISCORD_WEBHOOK_URL = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($webhookPointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($webhookPointer)
    }
}

if (-not $env:DISCORD_WEBHOOK_URL) {
    Write-Warning 'DISCORD_WEBHOOK_URL is not set. Discord notifications will be skipped.'
}

$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# Run the executable JAR directly to avoid Gradle worker/classpath encoding issues
# in Windows project paths containing Korean characters or spaces.
& "$projectDirectory\gradlew.bat" -p $projectDirectory bootJar
if ($LASTEXITCODE -ne 0) {
    throw 'Backend build failed.'
}
$applicationJar = Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'build\libs') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*-plain.jar' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $applicationJar) {
    throw 'Executable application JAR was not found.'
}
Write-Host 'Dashboard: http://localhost:8081'
& java -jar $applicationJar.FullName --spring.profiles.active=local
