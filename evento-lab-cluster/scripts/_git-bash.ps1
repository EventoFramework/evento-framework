# Shared by the *.ps1 twins: locate Git Bash and run a lab script with it.
function Invoke-LabScript {
    param([string]$Script, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest)
    $candidates = @(
        "$env:ProgramFiles\Git\bin\bash.exe",
        "${env:ProgramFiles(x86)}\Git\bin\bash.exe",
        "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
    )
    $gitCmd = Get-Command git.exe -ErrorAction SilentlyContinue
    if ($gitCmd) { $candidates += (Join-Path (Split-Path (Split-Path $gitCmd.Source)) 'bin\bash.exe') }
    $bash = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $bash) { throw "Git Bash not found; install Git for Windows or run the .sh scripts directly." }
    $path = (Join-Path $PSScriptRoot $Script).Replace([char]92, [char]47)
    & $bash $path @Rest
}
