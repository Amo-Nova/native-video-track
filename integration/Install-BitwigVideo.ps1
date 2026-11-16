param(
    [ValidateSet('Install','Restore','Status')][string]$Action = 'Install',
    [string]$BitwigRoot = "$env:ProgramFiles\Bitwig Studio",
    [string]$PackageRoot = $PSScriptRoot,
    [string]$SettingsSource = '',
    [string]$DataRoot = "$env:LOCALAPPDATA\BitwigVideo",
    [switch]$RegisterProjectAssociation,
    [switch]$SkipProjectAssociation
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'ProjectAssociation.ps1')

function Hash-File([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $digest = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
    finally { $digest.Dispose(); $stream.Dispose() }
}
function Full-Path([string]$Path) { [IO.Path]::GetFullPath($Path).TrimEnd('\','/') }
function Child-Path([string]$Base, [string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative.Contains(':') -or $Relative.Contains('\') -or $Relative.Split('/') -contains '..') {
        throw "Unsafe package path: $Relative"
    }
    $root = Full-Path $Base
    $target = Full-Path (Join-Path $root $Relative)
    if (!$target.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Path escapes destination: $Relative" }
    return $target
}
function Assert-NoReparse([string]$Path) {
    $next = Full-Path $Path
    while ($next) {
        if (Test-Path -LiteralPath $next) {
            $item = Get-Item -LiteralPath $next -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Refusing redirected installation path: $next" }
        }
        $next = [IO.Path]::GetDirectoryName($next)
    }
}
function Write-JsonAtomic([string]$Path, $Value) {
    $temp = $Path + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    [IO.File]::WriteAllText($temp, ($Value | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $Path) { [IO.File]::Replace($temp, $Path, ($temp+'.previous')) }
    else { [IO.File]::Move($temp, $Path) }
}
function Assert-Closed {
    if (!(Get-Command Get-CimInstance -ErrorAction SilentlyContinue)) { Import-Module (Join-Path $PSHOME 'Modules/CimCmdlets/CimCmdlets.psd1') }
    foreach ($process in @(Get-CimInstance Win32_Process -Filter "Name='BitwigStudioApp.exe'")) {
        if (!$process.ExecutablePath -or (Full-Path $process.ExecutablePath).StartsWith($script:hostRoot+'\', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Save your projects and close Bitwig normally before installing or restoring. No process was stopped.'
        }
    }
}
function Verify-Tree([string]$Root, $Files) {
    foreach ($file in $Files) {
        $path = Child-Path $Root $file.path
        if (!(Test-Path -LiteralPath $path -PathType Leaf) -or (Hash-File $path) -ne $file.sha256) { return $false }
    }
    return $true
}
function Expand-Verified([string]$Archive, [string]$Destination, $Files) {
    [IO.Directory]::CreateDirectory($Destination) | Out-Null
    $expected = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach ($file in $Files) { $expected.Add($file.path, $file.sha256) }
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $seen = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach ($entry in $zip.Entries) {
            if (!$expected.ContainsKey($entry.FullName) -or $seen.ContainsKey($entry.FullName)) { throw "Unexpected or duplicate archive entry: $($entry.FullName)" }
            $seen.Add($entry.FullName, $true)
            $dest = Child-Path $Destination $entry.FullName
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $false)
            if ((Hash-File $dest) -ne $expected[$entry.FullName]) { throw "Archive entry failed verification: $($entry.FullName)" }
        }
        if ($seen.Count -ne $expected.Count) { throw 'Archive is incomplete' }
    } finally { $zip.Dispose() }
}
function Verify-Jar([string]$Candidate, [string]$Original, $Overlay) {
    $expected = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach ($entry in $Overlay) { $expected.Add($entry.path, $entry) }
    $source = [IO.Compression.ZipFile]::OpenRead($Original)
    $target = [IO.Compression.ZipFile]::OpenRead($Candidate)
    $digest = [Security.Cryptography.SHA256]::Create()
    try {
        $seen = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach ($entry in $source.Entries) {
            $other = $target.GetEntry($entry.FullName)
            if ($null -eq $other) { throw "Missing original jar entry: $($entry.FullName)" }
            $seen.Add($entry.FullName, $true)
            $stream = $entry.Open()
            try { $originalHash = ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant() } finally { $stream.Dispose() }
            $wanted = $originalHash
            if ($expected.ContainsKey($entry.FullName)) {
                if ($originalHash -ne $expected[$entry.FullName].originalSha256) { throw "Original class mismatch: $($entry.FullName)" }
                $wanted = $expected[$entry.FullName].sha256
            }
            $stream = $other.Open()
            try { $actual = ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant() } finally { $stream.Dispose() }
            if ($actual -ne $wanted) { throw "Patched jar entry mismatch: $($entry.FullName)" }
        }
        foreach ($entry in $target.Entries) {
            if ($seen.ContainsKey($entry.FullName)) { continue }
            if (!$expected.ContainsKey($entry.FullName) -or $expected[$entry.FullName].originalSha256) { throw "Unexpected added jar entry: $($entry.FullName)" }
            $seen.Add($entry.FullName, $true)
            $stream = $entry.Open()
            try { $actual = ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant() } finally { $stream.Dispose() }
            if ($actual -ne $expected[$entry.FullName].sha256) { throw "Added jar entry mismatch: $($entry.FullName)" }
        }
        foreach ($name in $expected.Keys) { if (!$seen.ContainsKey($name)) { throw "Overlay entry absent: $name" } }
        if ($seen.Count -ne $target.Entries.Count) { throw 'Duplicate jar entries' }
    } finally { $digest.Dispose(); $source.Dispose(); $target.Dispose() }
}
function Archive-Pending {
    if (Test-Path -LiteralPath $script:pendingPath) {
        [IO.File]::Move($script:pendingPath, (Join-Path $script:history ('transaction-'+[Guid]::NewGuid().ToString('N')+'.json')))
    }
}

$hostRoot = Full-Path $BitwigRoot
if ($Action -eq 'Install' -and $hostRoot -cmatch '[^\x00-\x7F]') {
    throw 'This integration requires an ASCII Bitwig installation path. The original Bitwig launcher rejects the tested non-ANSI path. Project and media paths are separate from this restriction.'
}
$PackageRoot = Full-Path $PackageRoot
$bin = Join-Path $hostRoot 'bin'
$jar = Join-Path $bin 'bitwig.jar'
$componentRoot = Join-Path $bin 'bitwig-video'
$statePath = Join-Path $componentRoot 'installation.json'
$pendingPath = Join-Path $componentRoot 'pending.json'
$history = Join-Path $componentRoot 'history'
$manifestPath = Join-Path $PackageRoot 'manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.format -ne 1 -or $manifest.integrationVersion -notmatch '^[a-zA-Z0-9.-]+$') { throw 'Unsupported package manifest' }
$stockHash = 'a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367'
if ($manifest.originalSha256 -ne $stockHash) { throw 'This installer supports only the verified Windows 6.1.7 jar' }
if (!(Test-Path -LiteralPath $jar -PathType Leaf) -or !(Test-Path -LiteralPath (Join-Path $hostRoot 'Bitwig Studio.exe') -PathType Leaf)) { throw 'Bitwig installation not found' }
Assert-NoReparse $hostRoot
Assert-NoReparse $componentRoot
Assert-NoReparse $jar
$actualHash = Hash-File $jar
$state = $null; $pending = $null
if (Test-Path -LiteralPath $statePath) { $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json }
if (Test-Path -LiteralPath $pendingPath) { $pending = Get-Content -LiteralPath $pendingPath -Raw -Encoding UTF8 | ConvertFrom-Json }
$known = if ($pending -and $actualHash -eq $pending.installedSha256) { $pending } else { $state }
$release = Join-Path $componentRoot ('releases/'+$manifest.integrationVersion)
$backup = Join-Path $componentRoot ('backups/'+$stockHash+'.jar')

if ($Action -eq 'Status') {
    $status = if ($actualHash -eq $stockHash) { 'Original' } elseif ($known -and $actualHash -eq $known.installedSha256) { 'Installed' } else { 'UnknownOrUpdated' }
    $healthy = $status -eq 'Installed' -and (Verify-Tree $release $manifest.components)
    [ordered]@{ status=$status; healthy=$healthy; integration=$manifest.integrationVersion; bitwigRoot=$hostRoot; jarSha256=$actualHash; backupVerified=((Test-Path -LiteralPath $backup) -and (Hash-File $backup) -eq $stockHash); pending=($null -ne $pending) } | ConvertTo-Json
    exit 0
}
Assert-Closed
if ($actualHash -ne $stockHash -and (!$known -or $actualHash -ne $known.installedSha256)) {
    throw 'Bitwig was updated or changed by another tool. No files were changed. Use a compatible video component for this version.'
}
[IO.Directory]::CreateDirectory($componentRoot) | Out-Null
# A lock prevents two installer instances from racing after their initial read.
$lock = [IO.File]::Open((Join-Path $componentRoot 'install.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    if ((Hash-File $jar) -ne $actualHash) { throw 'Installation changed while acquiring lock; run again' }
    [IO.Directory]::CreateDirectory($history) | Out-Null
    $transaction = [Guid]::NewGuid().ToString('N')
    $stage = Join-Path $componentRoot ('staging/'+$transaction)
    [IO.Directory]::CreateDirectory($stage) | Out-Null
    if ($Action -eq 'Restore') {
        if ($actualHash -ne $stockHash) {
            if (!(Test-Path -LiteralPath $backup) -or (Hash-File $backup) -ne $stockHash) { throw 'Original backup missing or damaged; refusing an unsafe restore' }
            $restored = Join-Path $stage 'original.jar'
            [IO.File]::Copy($backup, $restored)
            Assert-Closed
            [IO.File]::Replace($restored, $jar, (Join-Path $stage 'removed-integration.jar'))
        }
        if ((Hash-File $jar) -ne $stockHash) { throw 'Original jar restore verification failed' }
        if (Test-Path -LiteralPath $statePath) { [IO.File]::Move($statePath, (Join-Path $history ('restored-'+$transaction+'.json'))) }
        Archive-Pending
        # Remove only this package's unchanged files. Backups, recovery tools, projects and user data stay.
        if (Test-Path -LiteralPath $release) {
            Assert-NoReparse $release
            foreach ($file in $manifest.components) {
                $owned = Child-Path $release $file.path
                Assert-NoReparse $owned
                if ((Test-Path -LiteralPath $owned -PathType Leaf) -and (Hash-File $owned) -eq $file.sha256) { Remove-Item -LiteralPath $owned -Force }
            }
            foreach ($directory in @(Get-ChildItem -LiteralPath $release -Directory -Recurse | Sort-Object FullName -Descending)) {
                Assert-NoReparse $directory.FullName
                if (@(Get-ChildItem -LiteralPath $directory.FullName -Force).Count -eq 0) { Remove-Item -LiteralPath $directory.FullName -Force }
            }
            if (@(Get-ChildItem -LiteralPath $release -Force).Count -eq 0) { Remove-Item -LiteralPath $release -Force }
        }
        if(!$SkipProjectAssociation){Update-ProjectAssociation 'Restore' $hostRoot $DataRoot}
        Write-Output ('RESTORED_ORIGINAL '+$stockHash)
        exit 0
    }

    foreach ($pair in @(@('jar-overlay.zip',$manifest.overlaySha256), @('component.zip',$manifest.componentSha256))) {
        if ((Hash-File (Join-Path $PackageRoot $pair[0])) -ne $pair[1]) { throw "Damaged installation package: $($pair[0])" }
    }
    $drive = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($hostRoot))
    if ($drive.AvailableFreeSpace -lt 1GB) { throw 'At least 1 GB of free space is required for verified installation and rollback' }
    if (!(Test-Path -LiteralPath $backup)) {
        if ($actualHash -ne $stockHash) { throw 'Cannot create original backup from an already modified jar' }
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup)) | Out-Null
        $backupTemp = Join-Path $stage 'backup.jar'
        [IO.File]::Copy($jar,$backupTemp)
        if ((Hash-File $backupTemp) -ne $stockHash) { throw 'Backup validation failed' }
        [IO.File]::Move($backupTemp,$backup)
    }
    Assert-NoReparse $backup
    if ((Hash-File $backup) -ne $stockHash) { throw 'Original backup is damaged' }
    $healthy = (Test-Path -LiteralPath $release) -and (Verify-Tree $release $manifest.components)
    if (!$healthy) {
        $stagedRelease = Join-Path $stage 'component'
        Expand-Verified (Join-Path $PackageRoot 'component.zip') $stagedRelease $manifest.components
        if (Test-Path -LiteralPath $release) {
            Assert-NoReparse $release
            [IO.Directory]::Move($release, (Join-Path $stage 'previous-component'))
        }
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($release)) | Out-Null
        [IO.Directory]::Move($stagedRelease, $release)
    }
    $recovery = Join-Path $componentRoot 'Recovery'
    [IO.Directory]::CreateDirectory($recovery) | Out-Null
    foreach ($name in @('Install-BitwigVideo.ps1','ProjectAssociation.ps1','Run-Installer.ps1','manifest.json','Restore.cmd','Status.cmd','README.txt')) {
        Copy-Item -LiteralPath (Join-Path $PackageRoot $name) -Destination (Join-Path $recovery $name) -Force
    }
    # The recovery copy locates its host even if the application cannot start.
    [IO.File]::WriteAllText((Join-Path $recovery 'host-root.txt'), $hostRoot, [Text.UTF8Encoding]::new($false))
    if ($SettingsSource -and (Test-Path -LiteralPath $SettingsSource -PathType Container)) {
        foreach ($id in @('video-preview','panel-management-framework','preferences-composition-framework','action-menu-composition-framework')) {
            $source = Join-Path $SettingsSource ($id+'/settings.properties')
            $dest = Join-Path $DataRoot ('mods/'+$id+'/settings.properties')
            if ((Test-Path -LiteralPath $source -PathType Leaf) -and !(Test-Path -LiteralPath $dest)) {
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest)) | Out-Null
                [IO.File]::Copy($source,$dest)
            }
        }
    }
    if ($known -and $actualHash -eq $known.installedSha256 -and $known.integrationVersion -eq $manifest.integrationVersion -and $known.overlaySha256 -eq $manifest.overlaySha256) {
        Write-JsonAtomic $statePath $known
        Archive-Pending
        if($RegisterProjectAssociation -and !$SkipProjectAssociation){Update-ProjectAssociation 'Install' $hostRoot $DataRoot}
        Write-Output ('INSTALLED_VERIFIED '+$known.integrationVersion+' '+$actualHash)
        exit 0
    }
    $candidate = Join-Path $stage 'bitwig.jar'
    [IO.File]::Copy($backup,$candidate)
    $delta = [IO.Compression.ZipFile]::OpenRead((Join-Path $PackageRoot 'jar-overlay.zip'))
    $target = [IO.Compression.ZipFile]::Open($candidate,[IO.Compression.ZipArchiveMode]::Update)
    try {
        foreach ($entry in $delta.Entries) {
            $unused = Child-Path $stage $entry.FullName # Validate path; entry content remains in the jar.
            $previous = $target.GetEntry($entry.FullName)
            if ($previous) { $previous.Delete() }
            $added = $target.CreateEntry($entry.FullName,[IO.Compression.CompressionLevel]::Optimal)
            $input = $entry.Open(); $output = $added.Open()
            try { $input.CopyTo($output) } finally { $output.Dispose();$input.Dispose() }
        }
    } finally { $target.Dispose(); $delta.Dispose() }
    Verify-Jar $candidate $backup $manifest.overlay
    $candidateHash = Hash-File $candidate
    $record = [ordered]@{ format=1; integrationVersion=$manifest.integrationVersion; videoVersion=$manifest.videoVersion; bitwigVersion=$manifest.bitwigVersion; hostRoot=$hostRoot; originalSha256=$stockHash; installedSha256=$candidateHash; overlaySha256=$manifest.overlaySha256; installedAt=[DateTime]::UtcNow.ToString('o'); transaction=$transaction }
    # Record the exact next jar before publication: both sides of a power loss are recoverable.
    Archive-Pending
    Write-JsonAtomic $pendingPath $record
    Assert-Closed
    $rollback = Join-Path $stage 'previous.jar'
    $published = $false
    try {
        [IO.File]::Replace($candidate,$jar,$rollback)
        $published = $true
        if ((Hash-File $jar) -ne $candidateHash) { throw 'Published jar verification failed' }
        Write-JsonAtomic $statePath $record
        Archive-Pending
    } catch {
        if ($published -and (Hash-File $jar) -eq $candidateHash -and (Test-Path -LiteralPath $rollback)) {
            [IO.File]::Replace($rollback,$jar,(Join-Path $stage 'failed-integration.jar'))
        }
        throw
    }
    if($RegisterProjectAssociation -and !$SkipProjectAssociation){Update-ProjectAssociation 'Install' $hostRoot $DataRoot}
    Write-Output ('INSTALLED '+$manifest.integrationVersion+' '+$candidateHash)
} finally { $lock.Dispose() }
