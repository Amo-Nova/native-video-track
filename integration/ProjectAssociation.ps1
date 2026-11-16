# Per-user registration, only when Windows has no existing default or explicit UserChoice.
# RegistryKey parameters let tests use an isolated HKCU test tree instead of real associations.
function Read-AssociationDefault($Root,[string]$Subkey) {
    $key=$Root.OpenSubKey($Subkey)
    if(!$key){return $null}
    try { return $key.GetValue('', $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames) }
    finally {$key.Dispose()}
}
function Read-ProjectUserChoice($Choice) {
    if($null -eq $Choice){return $null}
    return $Choice.GetValue('ProgId',$null)
}
function Read-AssociationTree($Root,[string]$Subkey,[string]$Relative='') {
    $key=$Root.OpenSubKey($Subkey)
    if(!$key){return}
    try {
        foreach($name in @($key.GetValueNames() | Sort-Object)) {
            [ordered]@{path=$Relative;name=$name;kind=$key.GetValueKind($name).ToString();value=$key.GetValue($name,$null,[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)}
        }
        foreach($name in @($key.GetSubKeyNames() | Sort-Object)) {
            Read-AssociationTree $Root ($Subkey+'\'+$name) ($Relative+'/'+$name)
        }
    } finally {$key.Dispose()}
}
function Test-OwnedAssociationTree($Actual,$Expected) {
    if(@($Actual).Count -eq 0){return $false}
    foreach($item in @($Actual)){
        $match=@($Expected | Where-Object {$_.path -ceq $item.path -and $_.name -ceq $item.name -and $_.kind -ceq $item.kind -and $_.value -ceq $item.value})
        if($match.Count -ne 1){return $false}
    }
    return $true
}
function Set-MissingProjectAssociation($Classes,$EffectiveClasses,$Choice,[string]$HostRoot,[string]$DataRoot) {
    $progId='BitwigVideo.InstalledProject'
    $current=Read-AssociationDefault $EffectiveClasses '.bwproject'
    $chosen=Read-ProjectUserChoice $Choice
    if($current -or $chosen){return 'PreservedExistingAssociation'}
    $recordPath=Join-Path $DataRoot 'installation/file-association.json'
    $old=$null
    if(Test-Path -LiteralPath $recordPath){
        $old=Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if($old.hostRoot -ne $HostRoot){return 'PreservedOtherInstallationAssociation'}
    }
    $own=$Classes.OpenSubKey($progId)
    if($own){
        $own.Dispose()
        $tree=@(Read-AssociationTree $Classes $progId)
        if(!$old -or !(Test-OwnedAssociationTree $tree $old.expected)){return 'PreservedExistingProgramRegistration'}
    }
    $extension=$Classes.OpenSubKey('.bwproject')
    $hadKey=$null -ne $extension;$hadDefault=$false;$oldDefault=$null;$oldKind='String'
    if($extension){
        try {
            $hadDefault=@($extension.GetValueNames()).Contains('')
            if($hadDefault){$oldDefault=$extension.GetValue('');$oldKind=$extension.GetValueKind('').ToString()}
        } finally {$extension.Dispose()}
    }
    $command='"'+(Join-Path $HostRoot 'Bitwig Studio.exe')+'" "%1"'
    $values=@(
        @('','', 'Bitwig Studio Project'),
        @('','BitwigVideoIntegrationOwner','1'),
        @('DefaultIcon','', '"'+(Join-Path $HostRoot 'bwproject.ico')+'"'),
        @('shell','', 'open'),
        @('shell\open\command','', $command)
    )
    $expected=@($values | ForEach-Object { [ordered]@{path=('/'+$_[0].Replace('\','/')).TrimEnd('/');name=$_[1];kind='String';value=$_[2]} } | Sort-Object path,name)
    $record=[ordered]@{format=1;hostRoot=$HostRoot;progId=$progId;hadUserKey=$hadKey;hadUserDefault=$hadDefault;previousDefault=$oldDefault;previousKind=$oldKind;expected=$expected}
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($recordPath)) | Out-Null
    if(!(Test-Path -LiteralPath $recordPath)){Write-JsonAtomic $recordPath $record}
    try {
        foreach($value in $values) {
            $path=$progId+$(if($value[0]){'\'+$value[0]}else{''})
            $key=$Classes.CreateSubKey($path)
            try {$key.SetValue($value[1],$value[2],[Microsoft.Win32.RegistryValueKind]::String)}finally{$key.Dispose()}
        }
        $key=$Classes.CreateSubKey('.bwproject')
        try {$key.SetValue('',$progId,[Microsoft.Win32.RegistryValueKind]::String)}finally{$key.Dispose()}
    }catch{
        Restore-ProjectAssociation $Classes $Choice $HostRoot $DataRoot | Out-Null
        throw
    }
    return 'RegisteredMissingAssociation'
}
function Restore-ProjectAssociation($Classes,$Choice,[string]$HostRoot,[string]$DataRoot) {
    $recordPath=Join-Path $DataRoot 'installation/file-association.json'
    if(!(Test-Path -LiteralPath $recordPath)){return 'NoOwnedAssociation'}
    $record=Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if($record.format -ne 1 -or $record.progId -ne 'BitwigVideo.InstalledProject' -or $record.hostRoot -ne $HostRoot){return 'PreservedOtherInstallationAssociation'}
    $progId=$record.progId
    if((Read-AssociationDefault $Classes '.bwproject') -eq $progId){
        $key=$Classes.OpenSubKey('.bwproject',$true)
        try {
            if($record.hadUserDefault){$key.SetValue('',$record.previousDefault,[Enum]::Parse([Microsoft.Win32.RegistryValueKind],$record.previousKind))}
            else{$key.DeleteValue('',$false)}
        } finally {$key.Dispose()}
    }
    $tree=@(Read-AssociationTree $Classes $progId | Sort-Object path,name)
    $wanted=@($record.expected | Sort-Object path,name)
    if((Read-ProjectUserChoice $Choice) -ne $progId -and (Test-OwnedAssociationTree $tree $wanted)) {
        $Classes.DeleteSubKeyTree($progId,$false)
    }
    $key=$Classes.OpenSubKey('.bwproject')
    if($key){
        try {$empty=$key.GetValueNames().Length -eq 0 -and $key.GetSubKeyNames().Length -eq 0}finally{$key.Dispose()}
        if(!$record.hadUserKey -and $empty){$Classes.DeleteSubKey('.bwproject',$false)}
    }
    [IO.File]::Move($recordPath,($recordPath+'.restored-'+[Guid]::NewGuid().ToString('N')))
    return 'RestoredOwnedAssociation'
}
function Update-ProjectAssociation([string]$Operation,[string]$HostRoot,[string]$DataRoot) {
    $classes=[Microsoft.Win32.Registry]::CurrentUser.CreateSubKey('Software\Classes')
    $effective=[Microsoft.Win32.Registry]::ClassesRoot
    $choice=[Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.bwproject\UserChoice')
    try {
        $result=if($Operation -eq 'Install'){Set-MissingProjectAssociation $classes $effective $choice $HostRoot $DataRoot}else{Restore-ProjectAssociation $classes $choice $HostRoot $DataRoot}
        if($result -in @('RegisteredMissingAssociation','RestoredOwnedAssociation')){
            if(!('BitwigVideoAssociationNotify' -as [type])){
                Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class BitwigVideoAssociationNotify { [DllImport("shell32.dll")] public static extern void SHChangeNotify(uint e, uint f, IntPtr a, IntPtr b); }'
            }
            [BitwigVideoAssociationNotify]::SHChangeNotify(0x08000000,0,[IntPtr]::Zero,[IntPtr]::Zero)
        }
        Write-Output $result
    }finally{if($choice){$choice.Dispose()};$classes.Dispose()}
}
