#Requires -Version 5.1
[CmdletBinding()]
param([string]$StudioPath,[switch]$Elevated,[switch]$ValidateOnly,[switch]$Launch,[switch]$Quiet,[string]$RegistryUserProfile=[Environment]::GetFolderPath('UserProfile'))
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'RegistryCache.ps1')
$componentConfig=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'componente.json') -Raw|ConvertFrom-Json

function Read-Manifest([string]$BundlePath){
    if(Test-Path -LiteralPath $BundlePath -PathType Container){$text=[IO.File]::ReadAllText((Join-Path $BundlePath 'META-INF\MANIFEST.MF'))}
    else{$zip=[IO.Compression.ZipFile]::OpenRead($BundlePath);try{$reader=New-Object IO.StreamReader($zip.GetEntry('META-INF/MANIFEST.MF').Open());try{$text=$reader.ReadToEnd()}finally{$reader.Dispose()}}finally{$zip.Dispose()}}
    $text=$text -replace "\r?\n ",''
    $result=@{}
    foreach($line in ($text -split '\r?\n')){if($line -match '^([^:]+): (.*)$'){$result[$matches[1]]=$matches[2]}}
    return $result
}
function Find-Studios {
    $found=New-Object 'System.Collections.Generic.List[string]'
    $roots=@($env:ProgramFiles,${env:ProgramFiles(x86)},(Join-Path $env:LOCALAPPDATA 'Programs'))
    foreach($root in $roots){if($root -and (Test-Path -LiteralPath $root)){
        $dirs=@(Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue|Where-Object{$_.Name -match 'Jaspersoft|JasperStudio'})
        foreach($dir in $dirs){$candidates=@($dir.FullName)+@(Get-ChildItem -LiteralPath $dir.FullName -Directory -ErrorAction SilentlyContinue|ForEach-Object{$_.FullName});foreach($candidate in $candidates){if(Test-Path -LiteralPath (Join-Path $candidate 'Jaspersoft Studio.ini')){if(-not $found.Contains($candidate)){$found.Add($candidate)}}}}
    }}
    return @($found.ToArray())
}
function Choose-Studio {
    Add-Type -AssemblyName System.Windows.Forms
    $candidates=@(Find-Studios)
    if($candidates.Count -eq 1){return $candidates[0]}
    $dialog=New-Object Windows.Forms.FolderBrowserDialog
    $dialog.Description='Selecione a pasta do Jaspersoft Studio 6.20.6 (a que contém plugins e Jaspersoft Studio.ini).'
    $dialog.ShowNewFolderButton=$false
    if($candidates.Count -gt 0){$dialog.SelectedPath=$candidates[0]}
    try{if($dialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK){throw 'Seleção cancelada.'};return $dialog.SelectedPath}finally{$dialog.Dispose()}
}
function Get-JavaMajor([string]$Root){
    $lines=Get-Content -LiteralPath (Join-Path $Root 'Jaspersoft Studio.ini')
    $vm=$null
    for($i=0;$i -lt $lines.Count-1;$i++){if($lines[$i].Trim() -eq '-vm'){$vm=$lines[$i+1].Trim().Trim('"');break}}
    if($vm){
        $vm=[Environment]::ExpandEnvironmentVariables($vm)
        if(-not [IO.Path]::IsPathRooted($vm)){$vm=Join-Path $Root $vm}
        $item=Get-Item -LiteralPath $vm -ErrorAction SilentlyContinue
        if($item){if($item.PSIsContainer){$dir=$item.FullName}else{$dir=$item.DirectoryName};for($i=0;$i -lt 5 -and $dir;$i++){$release=Join-Path $dir 'release';if(Test-Path -LiteralPath $release){$text=Get-Content -LiteralPath $release -Raw;if($text -match 'JAVA_VERSION="(\d+)'){$major=[int]$matches[1];if($major -eq 1 -and $text -match 'JAVA_VERSION="1\.(\d+)'){$major=[int]$matches[1]};return $major}};$dir=Split-Path -Parent $dir}}
    } else {
        $java=Get-Command java.exe -ErrorAction SilentlyContinue
        if($java){$text=(& $java.Source -version 2>&1|Out-String);if($text -match 'version "(\d+)'){return [int]$matches[1]}}
    }
    throw 'Não foi possível confirmar o Java usado por esta instalação. Este pacote exige Java 17 ou superior.'
}
function Get-Plan([string]$Root,[string]$PackageRoot=$PSScriptRoot){
    $rootFull=(Resolve-Path -LiteralPath $Root).Path
    $plugins=Join-Path $rootFull 'plugins'
    if(-not(Test-Path -LiteralPath (Join-Path $rootFull 'Jaspersoft Studio.ini')) -or -not(Test-Path -LiteralPath $plugins)){throw 'Pasta inválida. Selecione a pasta que contém plugins e Jaspersoft Studio.ini.'}
    $hostBundles=@{}
    foreach($bundle in Get-ChildItem -LiteralPath $plugins){if($bundle.Name -match '^(com\.jaspersoft\.|org\.eclipse\.|net\.sf\.jasperreports)'){
        try{$mf=Read-Manifest $bundle.FullName;$id=($mf['Bundle-SymbolicName'] -split ';')[0];if($id){$hostBundles[$id]=$mf['Bundle-Version']}}catch{ }
    }}
    $version=$hostBundles['com.jaspersoft.studio']
    if($version -ne '6.20.6.final'){throw "Versão encontrada: $version. Este pacote foi preparado para o Studio 6.20.6. Não é compatível diretamente com 6.16 ou outras versões."}
    $javaMajor=Get-JavaMajor $rootFull
    if($javaMajor -lt 17){throw "Java encontrado: $javaMajor. Os complementos deste pacote exigem Java 17 ou superior."}
    $manifest=Get-Content -LiteralPath (Join-Path $PackageRoot 'pacote\arquivos.json') -Raw|ConvertFrom-Json
    $files=New-Object 'System.Collections.Generic.List[object]';$skipped=New-Object 'System.Collections.Generic.List[string]'
    foreach($entry in $manifest){
        $source=Join-Path (Join-Path $PackageRoot 'pacote\plugins') $entry.name
        if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $entry.sha256){throw "Arquivo ausente ou alterado no pacote: $($entry.name)"}
        $mf=Read-Manifest $source;$fragment=$mf['Fragment-Host']
        if($fragment){$hostId=($fragment -split ';')[0];if(-not $hostBundles.ContainsKey($hostId)){$skipped.Add($entry.name);continue}
            if($fragment -match 'bundle-version="\[([^,]+),([^\]]+)\]"'){if($hostBundles[$hostId] -ne $matches[1]){throw "Módulo incompatível: $hostId. Encontrado: $($hostBundles[$hostId]); esperado: $($matches[1])."}}
        }
        $files.Add([pscustomobject]@{Source=$source;Name=$entry.name;Hash=$entry.sha256})
    }
    return [pscustomobject]@{Root=$rootFull;Version=$version;Java=$javaMajor;Files=$files.ToArray();Skipped=$skipped.ToArray()}
}

function Get-LaunchPlan([string]$Root,[string]$StateDirectory,[string]$Component){
    $rootFull=(Resolve-Path -LiteralPath $Root).Path
    $exe=Join-Path $rootFull 'Jaspersoft Studio.exe'
    $core=Join-Path $rootFull 'plugins\com.jaspersoft.studio_6.20.6.final.jar'
    if(-not(Test-Path -LiteralPath $exe) -or -not(Test-Path -LiteralPath $core)){throw 'Instalação incompatível. É necessário o Studio Community 6.20.6.'}
    $mf=Read-Manifest $core
    if($mf['Bundle-Version'] -ne '6.20.6.final'){throw 'Versão incompatível do Studio.'}
    $javaMajor=Get-JavaMajor $rootFull
    if($javaMajor -lt 17){throw 'É necessário Java 17 ou superior.'}
    $target=Join-Path $rootFull 'dropins\eclipse\plugins'
    $inventory=@(Get-ChildItem -LiteralPath $target -File -Filter '*.jar' -ErrorAction SilentlyContinue|Sort-Object Name|ForEach-Object{$_.Name+'|'+$_.Length+'|'+$_.LastWriteTimeUtc.Ticks}) -join "`n"
    $sha=[Security.Cryptography.SHA256]::Create()
    try{
        $signature=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($inventory)))).Replace('-','')
        $rootBytes=[Text.Encoding]::UTF8.GetBytes($rootFull.ToLowerInvariant())
        $rootId=([BitConverter]::ToString($sha.ComputeHash($rootBytes))).Replace('-','')
    }finally{$sha.Dispose()}
    $stateFile=Join-Path $StateDirectory ('.launch-'+$rootId+'.json')
    $needsClean=$true
    if(Test-Path -LiteralPath $stateFile){try{$state=Get-Content -LiteralPath $stateFile -Raw|ConvertFrom-Json;$needsClean=($state.signature -ne $signature)}catch{ }}
    $arguments=@()
    if($needsClean){$arguments+='-clean'}
    if($Component -eq 'translation'){$arguments+=@('-nl','pt_BR')}
    if($Component -eq 'themes'){$arguments+=@('-pluginCustomization',('"'+(Join-Path $StateDirectory 'padroes.ini')+'"'))}
    return [pscustomobject]@{Root=$rootFull;Version=$mf['Bundle-Version'];Java=$javaMajor;Executable=$exe;Arguments=$arguments;NeedsClean=$needsClean;StateFile=$stateFile;Signature=$signature}
}

try {
    if(-not $StudioPath){if($ValidateOnly){throw 'Informe -StudioPath para validar sem interface.'};$StudioPath=Choose-Studio}
    if($Launch){
        $launchPlan=Get-LaunchPlan $StudioPath $PSScriptRoot $componentConfig.component
        if($ValidateOnly){$launchPlan|Select-Object Root,Version,Java,Arguments,NeedsClean;exit 0}
        if($launchPlan.Arguments.Count){Start-Process -FilePath $launchPlan.Executable -ArgumentList $launchPlan.Arguments}else{Start-Process -FilePath $launchPlan.Executable}
        try{@{signature=$launchPlan.Signature}|ConvertTo-Json|Set-Content -LiteralPath $launchPlan.StateFile -Encoding UTF8}catch{ }
        exit 0
    }
    $plan=Get-Plan $StudioPath
    if($ValidateOnly){$plan|Select-Object Root,Version,Java,@{n='Plugins';e={$_.Files.Count}},@{n='OpcionaisAusentes';e={$_.Skipped.Count}};exit 0}
    $principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
        $argsList=@('-NoProfile','-ExecutionPolicy','Bypass','-STA','-File',('"'+$PSCommandPath+'"'),'-StudioPath',('"'+$plan.Root+'"'),'-Elevated')
        $argsList+=@('-RegistryUserProfile',('"'+$RegistryUserProfile+'"'))
        if($Quiet){$argsList+='-Quiet'}
        $child=Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -ArgumentList $argsList -Wait -PassThru
        exit $child.ExitCode
    }
    $running=@(Get-Process -ErrorAction SilentlyContinue|Where-Object{$_.ProcessName -like '*Jaspersoft*' -and $_.Path -and $_.Path.StartsWith($plan.Root,[StringComparison]::OrdinalIgnoreCase)})
    if($running.Count){throw 'Salve os relatórios e feche esta instalação do Jaspersoft Studio antes de instalar.'}
    $target=Join-Path $plan.Root 'dropins\eclipse\plugins'
    foreach($file in $plan.Files){$dest=Join-Path $target $file.Name;if(Test-Path -LiteralPath $dest){if((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -ne $file.Hash){throw "Já existe um arquivo diferente: $dest. Nenhum arquivo foi substituído."}}}
    New-Item -ItemType Directory -Force -Path $target|Out-Null
    $added=New-Object 'System.Collections.Generic.List[string]'
    $previous=New-Object 'System.Collections.Generic.List[object]'
    $backup=Join-Path $PSScriptRoot ('backups\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    try {
        foreach($file in $plan.Files){if($file.Name.StartsWith('com.salutem.')){
            $prefix=$file.Name.Substring(0,$file.Name.LastIndexOf('_'))
            foreach($old in @(Get-ChildItem -LiteralPath $target -File -Filter ($prefix+'_*.jar'))){if($old.Name -ne $file.Name){
                if([IO.Path]::GetDirectoryName($old.FullName) -ne $target){throw 'Arquivo anterior fora da pasta de plugins.'}
                New-Item -ItemType Directory -Force -Path $backup|Out-Null
                $saved=Join-Path $backup $old.Name
                Move-Item -LiteralPath $old.FullName -Destination $saved
                $previous.Add([pscustomobject]@{Original=$old.FullName;Backup=$saved})
            }}
        }}
        foreach($file in $plan.Files){$dest=Join-Path $target $file.Name;if(-not(Test-Path -LiteralPath $dest)){Copy-Item -LiteralPath $file.Source -Destination $dest;$added.Add($dest)};if((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -ne $file.Hash){throw "Falha de cópia: $($file.Name)"}}
        # Equinox may rebuild bundle IDs while retaining a registry from the
        # previous bundle inventory. Rebuild only that derived cache, even when
        # reinstalling the same JARs to repair an already affected installation.
        $registryBackup=@(Move-StudioRegistryCache @(Get-StudioRegistryCachePaths $plan.Root $RegistryUserProfile) $backup)
    }catch{
        foreach($created in $added){if([IO.Path]::GetDirectoryName($created) -eq $target){Remove-Item -LiteralPath $created -Force}}
        foreach($old in $previous){Move-Item -LiteralPath $old.Backup -Destination $old.Original -Force}
        throw
    }
    $report=@('Instalação concluída.',('Pasta: '+$plan.Root),('Versão: '+$plan.Version),('Java: '+$plan.Java),('Complementos conferidos: '+$plan.Files.Count),('Módulos opcionais ausentes: '+$plan.Skipped.Count),('Arquivos adicionados: '+$added.Count),('Caches de extensões preparados para reconstrução: '+$registryBackup.Count),('Data: '+(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')))
    try{$report|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'instalacao.log') -Encoding UTF8}catch{ }
    if($Quiet){$report;exit 0}
    Add-Type -AssemblyName System.Windows.Forms
    $message=$componentConfig.title+" instalado com sucesso.`r`n`r`nReinicie o Studio pelo Abrir JS.bat desta pasta."
    if($componentConfig.component -eq 'themes'){$message+="`r`nSelecione Noite, Grafite, Floresta, Ameixa, GitHub, Ônix ou Meia-Noite em Janela > Preferências > Gerais > Aparência."}
    [Windows.Forms.MessageBox]::Show($message,$componentConfig.title,'OK','Information')|Out-Null
    exit 0
} catch {
    if($ValidateOnly -or $Quiet){Write-Error $_;exit 1}
    Add-Type -AssemblyName System.Windows.Forms
    [Windows.Forms.MessageBox]::Show($_.Exception.Message,'Pacote de Atualizações do JasperStudio','OK','Error')|Out-Null
    exit 1
}
