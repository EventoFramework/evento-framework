# PowerShell twin of repair-catalog.sh (runs it with Git Bash).
. "$PSScriptRoot\_git-bash.ps1"
Invoke-LabScript "repair-catalog.sh" @args
exit $LASTEXITCODE
