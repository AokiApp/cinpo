#Requires -Version 5.1
<#
.SYNOPSIS
    Windows counterpart of extract_vendor.sh: extracts the encrypted vendor archive
    and verifies the Oracle Java Card kits it contains.

.EXAMPLE
    $env:VENDOR_PASSWORD = '<password>'
    powershell -ExecutionPolicy Bypass -File scripts\extract_vendor.ps1
#>
[CmdletBinding()]
param(
    [string] $Archive = 'vendor.zip',
    [string] $Destination = 'vendor'
)

$ErrorActionPreference = 'Stop'

if (-not $env:VENDOR_PASSWORD) {
    Write-Error "VENDOR_PASSWORD is required to extract $Archive."
}
if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) {
    Write-Error "Vendor archive not found: $Archive"
}

if (Test-Path -LiteralPath $Destination) {
    Remove-Item -LiteralPath $Destination -Recurse -Force
}
New-Item -ItemType Directory -Path $Destination | Out-Null

# bsdtar ships with Windows 10/11 and is the only in-box tool that reads
# password-protected zips.
tar -xf $Archive --passphrase $env:VENDOR_PASSWORD -C $Destination
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to extract $Archive (tar exit code $LASTEXITCODE)."
}

$requiredFiles = @(
    'java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip'
)

# Oracle ships one simulator kit per host OS. At least one must be present; which
# one is required depends on where the build eventually runs.
$simulatorFiles = @(
    'java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz',
    'java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip'
)

$checksums = @{
    'java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip'                 = '86443cb1b64c006456e524d91082ba25d5ebb0ee5506c6e4d7088350ce251d9d'
    'java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz'    = 'b8b999c3e1cfac5d56f7ef16654ce28c99cf472d96761d419a03ddf11f5811c0'
    'java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip'         = '5ab2efc69486989bcd73d0ed235f4f8348e82df262448fbc781dabd1bcb9b9c5'
}

foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $Destination $requiredFile) -PathType Leaf)) {
        Write-Error "Required vendor file was not extracted: $Destination\$requiredFile"
    }
}

$presentSimulators = @($simulatorFiles | Where-Object {
    Test-Path -LiteralPath (Join-Path $Destination $_) -PathType Leaf
})
if ($presentSimulators.Count -eq 0) {
    Write-Error "No Java Card simulator kit was extracted. Expected one of: $($simulatorFiles -join ', ')"
}

# Verify only what was actually extracted, so a kit for another OS may be absent.
foreach ($entry in $checksums.GetEnumerator()) {
    $path = Join-Path $Destination $entry.Key
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $entry.Value) {
        Write-Error "Checksum verification failed for $($entry.Key). Expected: $($entry.Value), Actual: $actual"
    }
    Write-Host "$($entry.Key): OK"
}
