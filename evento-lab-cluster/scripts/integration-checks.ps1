# PowerShell twin of integration-checks.sh (runs it with Git Bash).
. "$PSScriptRoot\_git-bash.ps1"
Invoke-LabScript "integration-checks.sh" @args
exit $LASTEXITCODE
