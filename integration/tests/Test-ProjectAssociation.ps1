$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path (Split-Path $PSScriptRoot) 'ProjectAssociation.ps1')
function Write-JsonAtomic([string]$Path,$Value){[IO.File]::WriteAllText($Path,($Value|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))}
function Check($Good,[string]$Message){if(!$Good){throw $Message};Write-Output ('PASS '+$Message)}
$id=[Guid]::NewGuid().ToString('N')
$testKey='Software\BitwigVideo\InstallerAssociationTests\'+$id
$base=[Microsoft.Win32.Registry]::CurrentUser.CreateSubKey($testKey)
$classes=$base.CreateSubKey('Classes');$choice=$base.CreateSubKey('Choice')
$data=Join-Path $PSScriptRoot ('association/'+$id)
$hostPath='C:\Bitwig Test';$prog='BitwigVideo.InstalledProject'
try {
    Check ((Set-MissingProjectAssociation $classes $classes $choice $hostPath $data) -eq 'RegisteredMissingAssociation') 'register missing association'
    Check ((Read-AssociationDefault $classes '.bwproject') -eq $prog) 'extension uses owned program ID'
    Check ((Read-AssociationDefault $classes ($prog+'\shell\open\command')) -eq '"C:\Bitwig Test\Bitwig Studio.exe" "%1"') 'ordinary executable receives quoted project path'
    Check ((Set-MissingProjectAssociation $classes $classes $choice $hostPath $data) -eq 'PreservedExistingAssociation') 'idempotent registration'
    $key=$classes.OpenSubKey('.bwproject',$true);$key.DeleteValue('');$key.Dispose();$classes.DeleteSubKeyTree($prog+'\DefaultIcon')
    Check ((Set-MissingProjectAssociation $classes $classes $choice $hostPath $data) -eq 'RegisteredMissingAssociation') 'repair interrupted registration'
    Check ((Restore-ProjectAssociation $classes $choice $hostPath $data) -eq 'RestoredOwnedAssociation') 'restore original association state'
    Check (!(Read-AssociationDefault $classes '.bwproject') -and @((Read-AssociationTree $classes $prog)).Count -eq 0) 'remove owned registration'
    $key=$classes.CreateSubKey('.bwproject');$key.SetValue('','Other.App');$key.Dispose()
    Check ((Set-MissingProjectAssociation $classes $classes $choice $hostPath $data) -eq 'PreservedExistingAssociation') 'preserve existing default'
    $classes.DeleteSubKeyTree('.bwproject');$choice.SetValue('ProgId','Other.App')
    Check ((Set-MissingProjectAssociation $classes $classes $choice $hostPath $data) -eq 'PreservedExistingAssociation') 'preserve explicit Windows UserChoice'
    $choice.DeleteValue('ProgId')
    Set-MissingProjectAssociation $classes $classes $choice $hostPath $data | Out-Null
    $key=$classes.OpenSubKey('.bwproject',$true);$key.SetValue('','Other.App');$key.Dispose()
    Restore-ProjectAssociation $classes $choice $hostPath $data | Out-Null
    Check ((Read-AssociationDefault $classes '.bwproject') -eq 'Other.App') 'restore preserves a newer user default'
} finally {
    $choice.Dispose();$classes.Dispose();$base.Dispose()
    # Delete only the unique registry fixture created by this test, never real Software\Classes.
    if($testKey -notmatch '^Software\\BitwigVideo\\InstallerAssociationTests\\[a-f0-9]{32}$'){throw 'Unsafe registry test cleanup path'}
    [Microsoft.Win32.Registry]::CurrentUser.DeleteSubKeyTree($testKey,$false)
}
