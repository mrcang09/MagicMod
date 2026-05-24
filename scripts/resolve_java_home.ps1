$ErrorActionPreference = "SilentlyContinue"

$candidates = New-Object System.Collections.Generic.List[string]

function Add-Candidate {
    param(
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }

    if (-not $candidates.Contains($Path)) {
        $candidates.Add($Path)
    }
}

function Get-VersionLine {
    param(
        [string]$JavaExe
    )

    if (-not (Test-Path $JavaExe -PathType Leaf)) {
        return $null
    }

    try {
        return (& cmd /d /c "`"$JavaExe`" -version 2>&1" | Select-Object -First 1).ToString()
    } catch {
        return $null
    }
}

Add-Candidate $env:JAVA_HOME

try {
    $javaCommand = Get-Command java -ErrorAction Stop | Select-Object -First 1
    if ($javaCommand -and $javaCommand.Source) {
        Add-Candidate (Split-Path (Split-Path $javaCommand.Source -Parent) -Parent)
    }
} catch {
}

@(
    "D:\\jdk",
    "D:\\java21",
    (Join-Path $env:USERPROFILE ".cache\\magicbot-jdk21\\jdk-21.0.10+7"),
    (Join-Path $env:USERPROFILE ".cache\\magicbot-jdk21"),
    (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
    (Join-Path $env:ProgramFiles "Java"),
    (Join-Path $env:ProgramFiles "Microsoft"),
    (Join-Path $env:LocalAppData "Programs\\Eclipse Adoptium")
) | ForEach-Object { Add-Candidate $_ }

$fallback = $null

foreach ($candidate in $candidates) {
    if (-not (Test-Path $candidate -PathType Container)) {
        continue
    }

    $homesToTry = New-Object System.Collections.Generic.List[string]
    $homesToTry.Add($candidate)

    foreach ($child in Get-ChildItem $candidate -Directory -ErrorAction SilentlyContinue) {
        $homesToTry.Add($child.FullName)
    }

    foreach ($javaHome in $homesToTry) {
        $javaExe = Join-Path $javaHome "bin\\java.exe"
        $versionLine = Get-VersionLine -JavaExe $javaExe
        if ([string]::IsNullOrWhiteSpace($versionLine)) {
            continue
        }

        $resolvedHome = (Resolve-Path $javaHome).Path
        if (-not $fallback) {
            $fallback = $resolvedHome
        }

        if ($versionLine -match '"21(?:\.\d+)?') {
            Write-Output $resolvedHome
            exit 0
        }
    }
}

if ($fallback) {
    Write-Output $fallback
    exit 0
}

Write-Error "No JDK installation was found."
exit 1
