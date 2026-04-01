param (
    [Parameter(Mandatory=$true)]
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+$')]
    [string]$Version
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Move to the script's directory
Set-Location -Path $PSScriptRoot

$file = "app/build.gradle.kts"
if (-not (Test-Path $file)) {
    Write-Error "Error: Could not find $file in $PSScriptRoot"
    exit 1
}

Write-Host "Reading $file"
$content = Get-Content $file -Raw

Write-Host "Updating version"
if ($content -match 'versionCode = (\d+)') {
    $currentCode = [int]$matches[1]
    $newCode = $currentCode + 1
    $content = $content -replace "versionCode = $currentCode", "versionCode = $newCode"
    $content = $content -replace 'versionName = ".*"', "versionName = `"$Version`""
} else {
    Write-Error "Could not find versionCode in $file"
    exit 1
}

Write-Host "Saving $file"
Set-Content -Path $file -Value $content -NoNewline

Write-Host "Committing changes and creating git tag"
git add $file
git commit -m "Version $Version"
git tag $Version

Write-Host "Successfully updated to version $Version (versionCode $newCode) and created git tag."
