param([ValidateSet('Install','Restore','Status')][string]$Action='Install',[string]$ReportPath='')
$ErrorActionPreference='Stop'
$script=Join-Path $PSScriptRoot 'Install-BitwigVideo.ps1'
$hostFile=Join-Path $PSScriptRoot 'host-root.txt'
$hostRoot=if(Test-Path -LiteralPath $hostFile){[IO.File]::ReadAllText($hostFile)}else{"$env:ProgramFiles\Bitwig Studio"}
$admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if($Action -ne 'Status' -and !$admin){
    # All text passed here is a resolved filesystem path or a ValidateSet value.
    if($script.Contains('"') -or $hostRoot.Contains('"')){throw 'Unsupported quote in installation path'}
    $reportFolder=Join-Path $env:LOCALAPPDATA 'BitwigVideo/installer-logs'
    [IO.Directory]::CreateDirectory($reportFolder) | Out-Null
    $report=Join-Path $reportFolder ([DateTime]::Now.ToString('yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N')+'.txt')
    $wrapper=$PSCommandPath
    if($wrapper.Contains('"') -or $report.Contains('"')){throw 'Unsupported quote in installation path'}
    $arguments='-NoProfile -ExecutionPolicy Bypass -File "'+$wrapper+'" -Action '+$Action+' -ReportPath "'+$report+'"'
    $process=Start-Process powershell.exe -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden -Wait -PassThru
    if(Test-Path -LiteralPath $report){Write-Host ([IO.File]::ReadAllText($report))}
    if($process.ExitCode -ne 0){throw ('Installation did not complete. Details: '+$report)}
}else{
    try {
        $result = & $script -Action $Action -BitwigRoot $hostRoot -RegisterProjectAssociation
        $text=($result | Out-String)
        if($ReportPath){[IO.File]::WriteAllText($ReportPath,$text,[Text.UTF8Encoding]::new($false))}
        Write-Host $text
    }catch{
        if($ReportPath){[IO.File]::WriteAllText($ReportPath,$_.Exception.Message,[Text.UTF8Encoding]::new($false))}
        throw
    }
}
