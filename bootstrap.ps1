#Requires -Version 5.1
<#
.SYNOPSIS
    Windows counterpart of bootstrap.sh: creates a standalone CINPO starter project.

.DESCRIPTION
    Fetches the CINPO template with git into a temporary sparse checkout, copies
    ./template/<template-name> into a new project directory, and prepares it as a
    standalone starter project.

    When run from a CINPO Git checkout, the template is fetched from the current
    origin/HEAD commit so the generated project matches that checked-out revision.
    Otherwise it defaults to https://github.com/AokiApp/cinpo @ main.

    Environment overrides:
      CINPO_TEMPLATE_REPO  Git repository URL used when auto-detection is unavailable.
      CINPO_TEMPLATE_REF   Git ref used when auto-detection is unavailable.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1 my-project

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1 -Template probe C:\src\my-project
#>
[CmdletBinding()]
param(
    # PowerShell matches parameter names case-insensitively, so -template and
    # -name work without aliases.
    [string] $Template = 'basic',

    [string] $Name,

    [Parameter(Position = 0)]
    [string] $Destination
)

$ErrorActionPreference = 'Stop'

$GradleVersion = '9.3.1'
$GradleRepo = 'https://github.com/gradle/gradle'
$DefaultRepo = 'https://github.com/AokiApp/cinpo'
$DefaultRef = 'main'
$TemplateProjectNameLine = "rootProject.name = 'cinpo-template'"

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
    Write-Host 'Usage:  scripts\bootstrap.ps1 [-Template <name>] [-Name <name>] <destination>' -ForegroundColor Cyan
    Write-Host ''
    Write-Host 'Fetches the CINPO template with git into a temporary sparse checkout, copies'
    Write-Host './template/<template-name> into a new project directory, and prepares it as a'
    Write-Host 'standalone starter project.'
    Write-Host ''
    Write-Host 'Options:'
    Write-Host '  -Template <name>   Template to use (default: basic)'
    Write-Host '  -Name <name>       Project name (default: destination directory basename)'
    Write-Host ''
    Write-Host 'Environment overrides:'
    Write-Host '  CINPO_TEMPLATE_REPO  Git repository URL used when auto-detection is unavailable.'
    Write-Host '  CINPO_TEMPLATE_REF   Git ref used when auto-detection is unavailable.'
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
        Write-Host "[x] Error: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
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

function Get-CheckoutSource {
    param([string] $CandidateDirectory)

    if (-not $CandidateDirectory) { return $null }

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & git -C $CandidateDirectory rev-parse --show-toplevel 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { return $null }

        $repoUrl = & git -C $CandidateDirectory remote get-url origin 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { return $null }
        $ref = & git -C $CandidateDirectory rev-parse HEAD 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { return $null }
    } finally {
        $ErrorActionPreference = $previousPreference
    }

    if (-not $repoUrl -or -not $ref) { return $null }
    return [PSCustomObject]@{ Repo = "$repoUrl".Trim(); Ref = "$ref".Trim() }
}

function Invoke-SparseCheckout {
    param(
        [string] $RepoUrl,
        [string] $Ref,
        [string] $CheckoutDirectory,
        [string[]] $SparsePaths
    )

    Invoke-Git @('init', '-q', $CheckoutDirectory)
    Invoke-Git @('-C', $CheckoutDirectory, 'remote', 'add', 'origin', $RepoUrl)
    Invoke-Git @('-C', $CheckoutDirectory, 'config', 'advice.detachedHead', 'false')
    Invoke-Git @('-C', $CheckoutDirectory, 'config', 'core.sparseCheckout', 'true')
    # Check out bytes verbatim. With the user's global core.autocrlf=true the POSIX
    # gradlew copied into the generated project would otherwise get CRLF endings and
    # fail with "bad interpreter: /bin/sh^M" on Linux and in the devcontainer.
    Invoke-Git @('-C', $CheckoutDirectory, 'config', 'core.autocrlf', 'false')
    Invoke-Git @('-C', $CheckoutDirectory, 'config', 'core.eol', 'lf')

    $infoDir = Join-Path $CheckoutDirectory '.git\info'
    New-Item -ItemType Directory -Force -Path $infoDir | Out-Null
    Write-TextFileLf -Path (Join-Path $infoDir 'sparse-checkout') -Content (($SparsePaths -join "`n") + "`n")

    Invoke-Git @('-C', $CheckoutDirectory, 'fetch', '--depth', '1', 'origin', $Ref)
    Invoke-Git @('-C', $CheckoutDirectory, 'checkout', '--detach', '-q', 'FETCH_HEAD')
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

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Stop-WithError 'Required command not found: git'
}

# --------------------------------------------------------------------------
# Resolve repo / ref / paths
# --------------------------------------------------------------------------

$templateRepo = $env:CINPO_TEMPLATE_REPO
$templateRef = $env:CINPO_TEMPLATE_REF

if (-not $templateRepo -or -not $templateRef) {
    $detected = Get-CheckoutSource -CandidateDirectory $PSScriptRoot
    if ($detected) {
        if (-not $templateRepo) { $templateRepo = $detected.Repo }
        if (-not $templateRef) { $templateRef = $detected.Ref }
    }
}
if (-not $templateRepo) { $templateRepo = $DefaultRepo }
if (-not $templateRef) { $templateRef = $DefaultRef }

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
Write-Info 'Template'     $Template
Write-Info 'Project name' $projectName
Write-Info 'Destination'  $destinationDir
Write-Info 'Source'       "$templateRepo @ $templateRef"

$workDir = Join-Path ([IO.Path]::GetTempPath()) ("cinpo-bootstrap-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

try {
    # ----------------------------------------------------------------------
    # Phase 1 - fetch template
    # ----------------------------------------------------------------------

    $templateCheckoutDir = Join-Path $workDir 'cinpo-source'

    Invoke-Step 1 4 'Fetching template' {
        Invoke-SparseCheckout -RepoUrl $templateRepo -Ref $templateRef `
            -CheckoutDirectory $templateCheckoutDir -SparsePaths @("template/$Template/")
    }

    $templateDir = Join-Path $templateCheckoutDir "template\$Template"
    if (-not (Test-Path -LiteralPath $templateDir -PathType Container)) {
        Write-Host "[x] Error: template not found: $Template" -ForegroundColor Red
        Write-Host "    Template source: $templateRepo @ $templateRef"
        exit 1
    }

    # ----------------------------------------------------------------------
    # Phase 2 - copy files + configure project
    # ----------------------------------------------------------------------

    Invoke-Step 2 4 'Copying files & configuring project' {
        $parent = Split-Path -Parent $destinationDir
        if ($parent -and -not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
        }
        Copy-Item -LiteralPath $templateDir -Destination $destinationDir -Recurse -Force

        foreach ($stale in @('.gradle', 'build', 'vendor')) {
            $stalePath = Join-Path $destinationDir $stale
            if (Test-Path -LiteralPath $stalePath) {
                Remove-Item -LiteralPath $stalePath -Recurse -Force
            }
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
    # Phase 3 - Gradle wrapper
    # ----------------------------------------------------------------------

    $wrapperDir = Join-Path $destinationDir 'gradle\wrapper'

    Invoke-Step 3 4 "Fetching Gradle wrapper ($GradleVersion)" {
        New-Item -ItemType Directory -Force -Path $wrapperDir | Out-Null

        $gradleCheckoutDir = Join-Path $workDir 'gradle-source'
        Invoke-SparseCheckout -RepoUrl $GradleRepo -Ref "v$GradleVersion" `
            -CheckoutDirectory $gradleCheckoutDir `
            -SparsePaths @('gradle/wrapper/gradle-wrapper.jar', 'gradlew', 'gradlew.bat')

        $wrapperJar = Join-Path $gradleCheckoutDir 'gradle\wrapper\gradle-wrapper.jar'
        if (-not (Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
            throw "gradle-wrapper.jar not found in fetched Gradle source: $GradleRepo @ v$GradleVersion"
        }

        Copy-Item -LiteralPath $wrapperJar -Destination (Join-Path $wrapperDir 'gradle-wrapper.jar') -Force
        Copy-Item -LiteralPath (Join-Path $gradleCheckoutDir 'gradlew') -Destination (Join-Path $destinationDir 'gradlew') -Force
        Copy-Item -LiteralPath (Join-Path $gradleCheckoutDir 'gradlew.bat') -Destination (Join-Path $destinationDir 'gradlew.bat') -Force

        Write-TextFileLf -Path (Join-Path $wrapperDir 'gradle-wrapper.properties') -Content @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-$GradleVersion-all.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@
    }

    # ----------------------------------------------------------------------
    # Phase 4 - git init
    # ----------------------------------------------------------------------

    Invoke-Step 4 4 'Initializing git repository' {
        Invoke-Git @('-C', $destinationDir, 'init', '-q')
        # Windows has no POSIX execute bit, so record it in the index directly.
        # Without this the generated project's gradlew is not executable on Linux.
        Invoke-Git @('-C', $destinationDir, 'add', '-A')
        Invoke-Git @('-C', $destinationDir, 'update-index', '--chmod=+x', 'gradlew')
        Invoke-Git @('-C', $destinationDir,
            '-c', 'user.name=CINPO Template',
            '-c', 'user.email=cinpo-template@example.invalid',
            'commit', '-q', '-m', 'Initial commit')
    }
} finally {
    Remove-Item -LiteralPath $workDir -Recurse -Force -ErrorAction SilentlyContinue
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

Write-Output @"
Created project from template:
  $destinationDir

Template:
  $Template

Project name:
  $projectName

Template source:
  $templateRepo @ $templateRef
"@
