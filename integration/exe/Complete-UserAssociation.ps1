param(
    [Parameter(Mandatory=$true)][ValidateSet('Install','Restore')][string]$Action,
    [Parameter(Mandatory=$true)][string]$BitwigRoot,
    [Parameter(Mandatory=$true)][string]$DataRoot
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
# Run in the original user's context, after the elevated installation succeeds.
. (Join-Path $PSScriptRoot 'ProjectAssociation.ps1')
function Write-JsonAtomic([string]$Path,$Value) {
    $temp=$Path+'.'+[Guid]::NewGuid().ToString('N')+'.tmp'
    [IO.File]::WriteAllText($temp,($Value | ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
    if(Test-Path -LiteralPath $Path){[IO.File]::Replace($temp,$Path,($temp+'.previous'))}
    else{[IO.File]::Move($temp,$Path)}
}
Update-ProjectAssociation $Action ([IO.Path]::GetFullPath($BitwigRoot).TrimEnd('\','/')) $DataRoot
