#Requires -Version 5.1
<#
.SYNOPSIS
    Windows counterpart of bootstrap.sh: creates a standalone CINPO starter project.

.DESCRIPTION
    Downloads a released template archive, configures the project name, and
    initializes a git repository.

    A release-pinned copy uses the version written into $DefaultVersion.

    Environment overrides:
      CINPO_TEMPLATE_VERSION    Release version to download.
      CINPO_TEMPLATE_BASE_URL   Base URL for template archives.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1 my-project

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1 -Template probe C:\src\my-project

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1 -Version 0.5.2 C:\src\my-project
#>
[CmdletBinding()]
param(
    # PowerShell matches parameter names case-insensitively, so -template and
    # -name work without aliases.
    [string] $Template = 'basic',

    [string] $Name,

    [string] $Version,

    [Parameter(Position = 0)]
    [string] $Destination
)

$ErrorActionPreference = 'Stop'

$DefaultVersion = '0.5.3'
$DefaultBaseUrl = 'https://AokiApp.github.io/cinpo'
$TemplateProjectNameLine = "rootProject.name = 'cinpo-template'"
$VersionPattern = '^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z][0-9A-Za-z.-]*)?$'

# --------------------------------------------------------------------------
# UI helpers
#
# All decorative output goes to the host, so a caller that pipes or redirects
# this script receives only the machine-readable summary written at the end.
# --------------------------------------------------------------------------

function Write-Banner {
    Write-Host @'
       し       ん       ぽ
  _____ _____ __   _ _____   ____
 / ____|__ __|  \ | |  __ \ / __ \
| |      | | | | \| | |__) | |  | |
| |____ _| |_| |\ | |  ___/| |__| |
 \_____|_____|_| \__|_|     \____/
'@ -ForegroundColor Cyan
    Write-Host '        :: project bootstrapper ::' -ForegroundColor DarkGray
}

function Write-Section([string] $Title) {
    Write-Host ''
    Write-Host "==> $Title"
}

function Write-Info([string] $Key, [string] $Value) {
    Write-Host ("    {0,-14} {1}" -f $Key, $Value)
}

function Stop-WithError([string] $Message) {
    Write-Host "[x] Error: $Message" -ForegroundColor Red
    exit 1
}

function Show-Usage {
    Write-Host ''
    Write-Host 'Usage:  scripts\bootstrap.ps1 [-Template <name>] [-Version <version>] [-Name <name>] <destination>' -ForegroundColor Cyan
    Write-Host ''
    Write-Host 'Creates a standalone starter project from a released template archive.'
    Write-Host ''
    Write-Host 'Options:'
    Write-Host '  -Template <name>    Template to use (default: basic)'
    Write-Host '  -Version <x.y.z>    Release version to download'
    Write-Host '  -Name <name>        Project name (default: destination directory basename)'
    Write-Host ''
    Write-Host 'Environment:'
    Write-Host '  CINPO_TEMPLATE_VERSION    Release version to download.'
    Write-Host '  CINPO_TEMPLATE_BASE_URL   Base URL for template archives.'
    Write-Host ''
}

# Runs a step, hiding its output unless it fails. Mirrors run_step in bootstrap.sh.
function Invoke-Step {
    param(
        [int] $Number,
        [int] $Total,
        [string] $Label,
        [scriptblock] $Action
    )

    Write-Host ("[..]  [{0}/{1}] {2}..." -f $Number, $Total, $Label) -ForegroundColor DarkGray
    $logFile = [IO.Path]::GetTempFileName()
    try {
        & $Action *> $logFile
        Write-Host ("[OK]  [{0}/{1}] {2}" -f $Number, $Total, $Label) -ForegroundColor Green
    } catch {
        Write-Host ("[x]   [{0}/{1}] {2} (failed)" -f $Number, $Total, $Label) -ForegroundColor Red
        $captured = Get-Content -LiteralPath $logFile -Raw -ErrorAction SilentlyContinue
        if ($captured) {
            Write-Host '--- captured output ---' -ForegroundColor DarkGray
            Write-Host $captured
            Write-Host '------------------------' -ForegroundColor DarkGray
        }
        throw
    } finally {
        Remove-Item -LiteralPath $logFile -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-Git {
    param([string[]] $Arguments)

    # git writes progress to stderr even on success. Windows PowerShell turns each
    # such line into an ErrorRecord, which would terminate the script under
    # $ErrorActionPreference = 'Stop', so flatten the streams to plain strings and
    # judge success by the exit code alone.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & git @Arguments 2>&1 | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $previousPreference
    }

    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

# Writes text as UTF-8 without a BOM and with LF line endings. Gradle wrapper
# properties and settings.gradle are consumed on every platform, so a CRLF or a
# BOM introduced here would follow the generated project to Linux.
function Write-TextFileLf {
    param([string] $Path, [string] $Content)

    $normalized = $Content -replace "`r`n", "`n"
    [IO.File]::WriteAllText($Path, $normalized, (New-Object Text.UTF8Encoding($false)))
}

# --------------------------------------------------------------------------
# Validate arguments
# --------------------------------------------------------------------------

if (-not $Destination) {
    Write-Banner
    Show-Usage
    exit 1
}

if (-not $Template) {
    Stop-WithError 'Missing value for -Template'
}
if ($Template -match '[\\/]' -or $Template -eq '.' -or $Template -eq '..') {
    Stop-WithError "Invalid template name: $Template"
}

foreach ($commandName in @('git', 'tar.exe', 'curl.exe')) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        if ($commandName -eq 'git') {
            Stop-WithError 'Required command not found: git'
        }
        Stop-WithError "$commandName is required (bundled with Windows 10 1803 and later)"
    }
}

# --------------------------------------------------------------------------
# Resolve the template version
# --------------------------------------------------------------------------

$selectedTemplate = $Template
$templateVersion = $null

if ($Version) {
    $templateVersion = $Version
} elseif ($env:CINPO_TEMPLATE_VERSION) {
    $templateVersion = $env:CINPO_TEMPLATE_VERSION
} elseif ($DefaultVersion) {
    $templateVersion = $DefaultVersion
} else {
    Stop-WithError 'No template version: pass -Version <x.y.z>'
}

if ($templateVersion -notmatch $VersionPattern) {
    Stop-WithError "Invalid version: $templateVersion (expected x.y.z)"
}

$baseUrl = $env:CINPO_TEMPLATE_BASE_URL
if (-not $baseUrl) {
    $baseUrl = $DefaultBaseUrl
}
$baseUrl = $baseUrl.TrimEnd('/')

$templateUrl = "$baseUrl/templates/$templateVersion/$selectedTemplate/template.tar.gz"

$destinationDir = [IO.Path]::GetFullPath([IO.Path]::Combine((Get-Location).ProviderPath, $Destination))

if (Test-Path -LiteralPath $destinationDir) {
    Stop-WithError "Destination already exists: $destinationDir"
}

$projectName = $Name
if (-not $projectName) {
    $projectName = Split-Path -Leaf $destinationDir
}

# --------------------------------------------------------------------------
# Banner + configuration panel
# --------------------------------------------------------------------------

Write-Banner

Write-Section 'Configuration'
Write-Info 'Template'     $selectedTemplate
Write-Info 'Project name' $projectName
Write-Info 'Destination'  $destinationDir
Write-Info 'Source'       $templateUrl

$workDir = Join-Path ([IO.Path]::GetTempPath()) ("cinpo-bootstrap-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

$destinationCreated = $false
$completed = $false
$archive = Join-Path $workDir 'template.tar.gz'

try {
    # ----------------------------------------------------------------------
    # Phase 1 - acquire the template
    # ----------------------------------------------------------------------

    Invoke-Step 1 3 "Fetching template ($selectedTemplate $templateVersion)" {
        $httpCode = & curl.exe -sS -L --retry 3 -o $archive -w '%{http_code}' $templateUrl 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "failed to download $templateUrl (curl exit $LASTEXITCODE)"
        }
        if ("$httpCode" -ne '200') {
            if ("$httpCode" -eq '404') {
                throw "template '$selectedTemplate' is not published for version $templateVersion`n    $templateUrl"
            }
            throw "failed to download $templateUrl (HTTP $httpCode)"
        }
    }

    # ----------------------------------------------------------------------
    # Phase 2 - extract files + configure project
    # ----------------------------------------------------------------------

    $destinationCreated = $true

    Invoke-Step 2 3 'Extracting files & configuring project' {
        $parent = Split-Path -Parent $destinationDir
        if ($parent -and -not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
        }

        New-Item -ItemType Directory -Force -Path $destinationDir | Out-Null
        & tar.exe -xzf $archive -C $destinationDir
        if ($LASTEXITCODE -ne 0) {
            throw "tar failed to extract $archive"
        }

        $settingsFile = Join-Path $destinationDir 'settings.gradle'
        if (-not (Test-Path -LiteralPath $settingsFile -PathType Leaf)) {
            throw "settings.gradle not found in extracted template: $settingsFile"
        }

        $escapedProjectName = $projectName -replace '\\', '\\' -replace "'", "\'"
        $replacement = "rootProject.name = '$escapedProjectName'"

        $lines = [IO.File]::ReadAllLines($settingsFile)
        $replaced = $false
        for ($i = 0; $i -lt $lines.Length; $i++) {
            if (-not $replaced -and $lines[$i] -ceq $TemplateProjectNameLine) {
                $lines[$i] = $replacement
                $replaced = $true
            }
        }
        if (-not $replaced) {
            throw "Expected template project name declaration not found in $settingsFile"
        }
        Write-TextFileLf -Path $settingsFile -Content (($lines -join "`n") + "`n")
    }

    # ----------------------------------------------------------------------
    # Phase 3 - git init
    # ----------------------------------------------------------------------

    Invoke-Step 3 3 'Initializing git repository' {
        Invoke-Git @('-C', $destinationDir, 'init', '-q')
        Invoke-Git @('-C', $destinationDir, 'add', '-A')
        # Windows has no POSIX execute bit, so record it in the index directly.
        # Without this the generated project's gradlew is not executable on Linux.
        Invoke-Git @('-C', $destinationDir, 'update-index', '--chmod=+x', 'gradlew')
        Invoke-Git @('-C', $destinationDir,
            '-c', 'user.name=CINPO Template',
            '-c', 'user.email=cinpo-template@example.invalid',
            'commit', '-q', '-m', 'Initial commit')
    }

    $completed = $true
} catch {
    Write-Host "[x] Error: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    if (-not $completed -and $destinationCreated -and (Test-Path -LiteralPath $destinationDir)) {
        Remove-Item -LiteralPath $destinationDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $workDir -Recurse -Force -ErrorAction SilentlyContinue
}

if (-not $completed) {
    exit 1
}

# --------------------------------------------------------------------------
# Success panel (chrome to the host, summary to stdout)
# --------------------------------------------------------------------------

$box = '+--------------------------------------------------+'
Write-Host ''
Write-Host $box -ForegroundColor Green
Write-Host '|  [OK] Project created successfully!              |' -ForegroundColor Green
Write-Host ("|  {0,-48}|" -f $destinationDir) -ForegroundColor Green
Write-Host $box -ForegroundColor Green
Write-Host ''

Write-Host '[!] Before running, prepare the vendor directory:' -ForegroundColor Yellow
Write-Host "    - Download the Java Card SDK Tools and Simulator from Oracle's website."
Write-Host '    - Place the archives (unextracted) into'
Write-Host "      $destinationDir\vendor"
Write-Host ''
Write-Host 'Next steps'
Write-Host "    1. cd $destinationDir"
Write-Host '    2. .\gradlew.bat cinpoRun'

$sourceLine = "  $templateUrl @ $templateVersion"

Write-Output @"
Created project from template:
  $destinationDir

Template:
  $selectedTemplate

Project name:
  $projectName

Template source:
$sourceLine
"@
