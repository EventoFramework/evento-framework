# PowerShell twin of up.sh: runs the bash script with Git Bash (not WSL's bash.exe),
# forwarding all arguments and the current environment (LAB_TAG, BUS_VIA_LB, ...).
. "$PSScriptRoot\_git-bash.ps1"
Invoke-LabScript "up.sh" @args
exit $LASTEXITCODE
