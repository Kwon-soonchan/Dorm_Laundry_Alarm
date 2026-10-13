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

& "$PSScriptRoot\..\gradlew.bat" bootRun --args='--spring.profiles.active=local'
