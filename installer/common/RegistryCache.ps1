# Registry cache only. Workspace preferences, reports and secure storage are not targets.
function Get-StudioInstallHash([string]$CanonicalPath) {
    # EquinoxLocations.getInstallDirHash(): Java String.hashCode of the canonical install path.
    [long]$hash=0
    foreach($character in $CanonicalPath.ToCharArray()){$hash=($hash*31+[int]$character) -band 4294967295L}
    if($hash -ge 2147483648L){$hash-=4294967296L}
    return [string]$hash
}

function Get-StudioRegistryCachePaths([string]$StudioRoot,[string]$UserProfile=[Environment]::GetFolderPath('UserProfile')) {
    $root=(Get-Item -LiteralPath $StudioRoot -ErrorAction Stop).FullName.TrimEnd('\','/')
    $hash=Get-StudioInstallHash $root
    $configurations=@((Join-Path $root 'configuration'))
    if($UserProfile){$configurations+=Join-Path $UserProfile ('.eclipse\'+$hash+'_win32_win32_x86_64\configuration')}
    foreach($configuration in $configurations){
        $configuration=[IO.Path]::GetFullPath($configuration)
        [pscustomobject]@{Configuration=$configuration;Cache=(Join-Path $configuration 'org.eclipse.core.runtime')}
    }
}

function Move-StudioRegistryCache([object[]]$Paths,[string]$BackupRoot) {
    $backup=[IO.Path]::GetFullPath($BackupRoot)
    $moves=New-Object 'System.Collections.Generic.List[object]'
    $targets=New-Object 'System.Collections.Generic.List[object]'
    # Validate every target before moving anything. Reparse points and unknown files
    # are rejected: this directory is disposable registry data, never a workspace.
    foreach($path in $Paths){
        $expected=Join-Path ([IO.Path]::GetFullPath($path.Configuration)) 'org.eclipse.core.runtime'
        if([IO.Path]::GetFullPath($path.Cache) -ne $expected){throw 'Caminho de cache fora da configuração esperada.'}
        if($backup -eq $expected -or $backup.StartsWith($expected+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'O backup deve ficar fora do cache.'}
        if(-not(Test-Path -LiteralPath $expected -PathType Container)){continue}
        $item=Get-Item -LiteralPath $expected -Force
        $ancestors=@($item,(Get-Item -LiteralPath $path.Configuration -Force))
        if(@($ancestors|Where-Object{($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0}).Count){throw 'Cache de extensões em link não pode ser movido automaticamente.'}
        $files=@(Get-ChildItem -LiteralPath $expected -Force -Recurse)
        foreach($file in $files){
            if(($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'Link encontrado no cache de extensões.'}
            $relative=$file.FullName.Substring($expected.Length+1)
            if($relative -notmatch '^\.manager(\\[^\\]+)?$|^\.(contributions|contributors|extraData|mainData|namespaces|orphans|table)(\.\d+)?$'){
                throw "Conteúdo não reconhecido no cache de extensões: $relative. Nada foi removido."
            }
        }
        if(@($files|Where-Object{-not $_.PSIsContainer -and $_.DirectoryName -eq $expected}).Count){$targets.Add($path)}
    }
    try{
        $number=0
        foreach($path in $targets){
            if(-not(Test-Path -LiteralPath $path.Cache -PathType Container)){continue}
            $destination=Join-Path $backup ('registry-cache-'+$number);$number++
            if(Test-Path -LiteralPath $destination){throw 'Backup de cache já existe.'}
            New-Item -ItemType Directory -Force -Path $backup|Out-Null
            Move-Item -LiteralPath $path.Cache -Destination $destination -ErrorAction Stop
            $moves.Add([pscustomobject]@{Original=$path.Cache;Backup=$destination})
        }
        return $moves.ToArray()
    }catch{
        foreach($move in $moves){if(-not(Test-Path -LiteralPath $move.Original)){Move-Item -LiteralPath $move.Backup -Destination $move.Original -ErrorAction Stop}}
        throw
    }
}
