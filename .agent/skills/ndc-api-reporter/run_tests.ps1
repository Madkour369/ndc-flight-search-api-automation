<#
.SYNOPSIS
  ndc-api-reporter skill: run the Maven/TestNG suite, then generate the
  executive Execution_QA_Report.html -- always, even if tests fail, so the
  report's "Failed & Blocked Issues" section is exactly when it matters most.

.EXAMPLE
  .\.agent\skills\ndc-api-reporter\run_tests.ps1
  .\.agent\skills\ndc-api-reporter\run_tests.ps1 -ProjectRoot C:\path\to\other\suite

.NOTES
  Secrets: if a .env file exists at the project root, KEY=VALUE lines from it
  are loaded into the environment -- but only for keys not already set, so a
  real secret injected by CI always wins over a local .env fallback. Never
  commit a real .env file.
#>
param(
    [string]$ProjectRoot = (Get-Location).Path
)

$SkillDir = $PSScriptRoot
$EnvFile = Join-Path $ProjectRoot ".env"

if (Test-Path $EnvFile) {
    Write-Host "Loading environment secrets from $EnvFile (existing environment variables take precedence)"
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $parts = $line -split '=', 2
        if ($parts.Length -eq 2) {
            $key = $parts[0].Trim()
            $value = $parts[1].Trim()
            if (-not (Test-Path "Env:\$key")) {
                Set-Item "Env:\$key" $value
            }
        }
    }
}

Write-Host "Running test suite in $ProjectRoot ..."
Push-Location $ProjectRoot
try {
    & mvn -B clean test
    $testExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host "Generating executive QA report ..."
$env:PROJECT_ROOT = $ProjectRoot
node (Join-Path $SkillDir "generate_report.js")

if ($testExitCode -ne 0) {
    Write-Host "Test suite reported failures (exit code $testExitCode) -- see Execution_QA_Report.html for details."
}

exit $testExitCode
