#Requires -Version 5.1
[CmdletBinding()]
param([string]$StudioPath,[switch]$Elevated,[switch]$ValidateOnly,[switch]$Launch,[switch]$Quiet,[switch]$PlanJson,[switch]$ValidatedOnly,[switch]$Experimental,[string]$RegistryUserProfile=[Environment]::GetFolderPath('UserProfile'))
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'RegistryCache.ps1')
$componentConfig=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'componente.json') -Raw|ConvertFrom-Json
$expectedStudioVersion='6.20.6.final';$expectedJavaMajor=17
if($Experimental){
 if(!$ValidatedOnly -or $componentConfig.profileValidation -ne 'experimental' -or $componentConfig.requiredStudioVersion -notmatch '^\d+\.\d+\.\d+\.[\w-]+$' -or $componentConfig.requiredJavaMajor -notin @(8,11,17)){throw 'Configuração experimental inválida.'}
 $expectedStudioVersion=$componentConfig.requiredStudioVersion;$expectedJavaMajor=$componentConfig.requiredJavaMajor
}
if($ValidatedOnly -and !$Experimental -and $componentConfig.requiredStudioVersion){
    if($componentConfig.requiredStudioVersion -notin @('6.6.0.final','6.20.6.final','6.21.5.final') -or $componentConfig.profileValidation -notin @('validated-baseline','validated-port')){throw 'Perfil de plataforma sem validação para este instalador.'}
    $expectedStudioVersion=$componentConfig.requiredStudioVersion
    $expectedJavaMajor=if($expectedStudioVersion -eq '6.6.0.final'){8}else{17}
    if($componentConfig.requiredJavaMajor -ne $expectedJavaMajor){throw 'A JVM do perfil não corresponde à plataforma comprovada.'}
}

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
    $dialog.Description="Selecione a pasta do Jaspersoft Studio $expectedStudioVersion (a que contém plugins e Jaspersoft Studio.ini)."
    $dialog.ShowNewFolderButton=$false
    if($candidates.Count -gt 0){$dialog.SelectedPath=$candidates[0]}
    try{if($dialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK){throw 'Seleção cancelada.'};return $dialog.SelectedPath}finally{$dialog.Dispose()}
}
function Get-JavaMajor([string]$Root,[switch]$Details){
    $lines=Get-Content -LiteralPath (Join-Path $Root 'Jaspersoft Studio.ini')
    $vm=$null
    for($i=0;$i -lt $lines.Count-1;$i++){if($lines[$i].Trim() -eq '-vm'){$vm=$lines[$i+1].Trim().Trim('"');break}}
    if($vm){
        $vm=[Environment]::ExpandEnvironmentVariables($vm)
        if(-not [IO.Path]::IsPathRooted($vm)){$vm=Join-Path $Root $vm}
        $item=Get-Item -LiteralPath $vm -ErrorAction SilentlyContinue
        if($item){if($item.PSIsContainer){$dir=$item.FullName}else{$dir=$item.DirectoryName};for($i=0;$i -lt 5 -and $dir;$i++){$release=Join-Path $dir 'release';if(Test-Path -LiteralPath $release){$text=Get-Content -LiteralPath $release -Raw;if($text -match 'JAVA_VERSION="(\d+)'){$major=[int]$matches[1];if($major -eq 1 -and $text -match 'JAVA_VERSION="1\.(\d+)'){$major=[int]$matches[1]};if($Details){$runtimeVersion=if($text -match 'JAVA_VERSION="([^"]+)'){$matches[1]}else{''};$architecture=if($text -match 'OS_ARCH="([^"]+)'){$matches[1]}else{''};return [pscustomobject]@{Major=$major;Version=$runtimeVersion;Architecture=$architecture}};return $major}};$dir=Split-Path -Parent $dir}}
    } else {
        $java=Get-Command java.exe -ErrorAction SilentlyContinue
        if($java){$text=(& $java.Source -version 2>&1|Out-String);if($text -match 'version "(\d+)'){return [int]$matches[1]}}
    }
    throw 'Não foi possível confirmar o Java usado por esta instalação. Confira o runtime indicado em -vm no Studio.'
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
    if($version -ne $expectedStudioVersion){throw "Versão encontrada: $version. O perfil deste pacote exige exatamente o Studio $expectedStudioVersion."}
    if($ValidatedOnly -and [IO.File]::ReadAllText((Join-Path $rootFull 'Jaspersoft Studio.ini')) -notmatch '(?m)^-vm\s*$'){throw 'Configure explicitamente -vm no Studio para confirmar o runtime antes de instalar.'}
    $javaInfo=Get-JavaMajor $rootFull -Details:$ValidatedOnly
    $javaMajor=if($ValidatedOnly){$javaInfo.Major}else{$javaInfo}
    if($javaMajor -lt $expectedJavaMajor){throw "Java encontrado: $javaMajor. Este perfil exige Java $expectedJavaMajor."}
    if($ValidatedOnly){
        if(!(Test-Path -LiteralPath (Join-Path $rootFull 'Jaspersoft Studio.exe'))){throw 'Executável do Studio ausente.'}
        if($Experimental){if($javaInfo.Architecture -notin @('amd64','x86_64')){throw 'O pacote experimental requer Studio e Java Windows x64.'};if(!$hostBundles.ContainsKey('org.eclipse.swt.win32.win32.x86_64')){throw 'SWT Windows x64 ausente.'}}
        if(!$Experimental){
        if($javaMajor -ne $expectedJavaMajor){throw "Perfil gráfico validado apenas com Java $expectedJavaMajor. Outras JVMs do Studio não foram validadas."}
        if(!$componentConfig.requiredJavaVersion -or $javaInfo.Version -ne $componentConfig.requiredJavaVersion -or $javaInfo.Architecture -notin @('amd64','x86_64')){throw 'Versão exata ou arquitetura do Java não corresponde ao perfil comprovado.'}
        if(!$componentConfig.requiredSwtVersion -or $hostBundles['org.eclipse.swt'] -ne $componentConfig.requiredSwtVersion -or $hostBundles['org.eclipse.swt.win32.win32.x86_64'] -ne $componentConfig.requiredSwtVersion){throw 'A versão/arquitetura do SWT difere do perfil validado do Studio.'}
        }
        foreach($required in @($componentConfig.requiredHosts)){if($required -and $hostBundles[$required] -ne $expectedStudioVersion){throw "Componente obrigatório ausente ou incompatível: $required"}}
        if($componentConfig.minimumWindowsBuild){$build=[int](Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion' -Name CurrentBuildNumber).CurrentBuildNumber;if($build -lt [int]$componentConfig.minimumWindowsBuild){throw 'A versão do Windows não atende ao perfil nativo validado.'}}
    }
    $manifest=Get-Content -LiteralPath (Join-Path $PackageRoot 'pacote\arquivos.json') -Raw|ConvertFrom-Json
    $files=New-Object 'System.Collections.Generic.List[object]';$skipped=New-Object 'System.Collections.Generic.List[string]'
    foreach($entry in $manifest){
        if([IO.Path]::GetFileName($entry.name) -ne $entry.name -or $entry.name -notmatch '\.jar$'){throw 'Nome de arquivo inválido no inventário.'}
        $source=Join-Path (Join-Path $PackageRoot 'pacote\plugins') $entry.name
        if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $entry.sha256){throw "Arquivo ausente ou alterado no pacote: $($entry.name)"}
        $mf=Read-Manifest $source;$fragment=$mf['Fragment-Host']
        if($ValidatedOnly){
            if(!$Experimental -and $expectedStudioVersion -ne '6.20.6.final'){
                $qualifier='.port'+$expectedStudioVersion.Replace('.final','').Replace('.','')+'.jar'
                if(!$entry.name.EndsWith($qualifier,[StringComparison]::Ordinal)){throw 'Complemento preparado para outra plataforma do Studio.'}
            }elseif(!$Experimental -and $entry.name -match '\.port\d+\.jar$'){throw 'O perfil de referência não aceita complementos de outro porte.'}
            if($mf['Bundle-RequiredExecutionEnvironment'] -match 'JavaSE-(?:1\.)?(\d+)' -and [int]$matches[1] -gt $javaMajor){throw 'O bytecode declarado pelo complemento exige outra JVM.'}
            if($Experimental){
                $archive=[IO.Compression.ZipFile]::OpenRead($source)
                try{foreach($class in $archive.Entries|Where-Object{ $_.FullName -like '*.class' -and $_.FullName -notlike 'META-INF/versions/*' }){$stream=$class.Open();try{$header=New-Object byte[] 8;if($stream.Read($header,0,8) -ne 8 -or ([int]$header[6]*256+$header[7]) -gt ($javaMajor+44)){throw 'Classe do complemento incompatível com o Java selecionado.'}}finally{$stream.Dispose()}}}finally{$archive.Dispose()}
            }
        }
        if($fragment){$hostId=($fragment -split ';')[0];if(-not $hostBundles.ContainsKey($hostId)){$skipped.Add($entry.name);continue}
            if($fragment -match 'bundle-version="\[([^,]+),([^\]]+)\]"'){if($hostBundles[$hostId] -ne $matches[1]){if($Experimental){$skipped.Add($entry.name);continue};throw "Módulo incompatível: $hostId. Encontrado: $($hostBundles[$hostId]); esperado: $($matches[1])."}}
        }
        $files.Add([pscustomobject]@{Source=$source;Name=$entry.name;Hash=$entry.sha256})
    }
    return [pscustomobject]@{Root=$rootFull;Version=$version;Java=$javaMajor;Files=$files.ToArray();Skipped=$skipped.ToArray()}
}

function Get-LaunchPlan([string]$Root,[string]$StateDirectory,[string]$Component,[switch]$WithThemes,[switch]$WithTranslation){
    $rootFull=(Resolve-Path -LiteralPath $Root).Path
    $exe=Join-Path $rootFull 'Jaspersoft Studio.exe'
    $core=Join-Path $rootFull ('plugins\com.jaspersoft.studio_'+$expectedStudioVersion+'.jar')
    if(-not(Test-Path -LiteralPath $exe) -or -not(Test-Path -LiteralPath $core)){throw "Instalação incompatível. É necessário o Studio Community $expectedStudioVersion."}
    $mf=Read-Manifest $core
    if($mf['Bundle-Version'] -ne $expectedStudioVersion){throw 'Versão incompatível do Studio.'}
    if($ValidatedOnly -and [IO.File]::ReadAllText((Join-Path $rootFull 'Jaspersoft Studio.ini')) -notmatch '(?m)^-vm\s*$'){throw 'Configure explicitamente -vm no Studio para confirmar o runtime antes de abrir.'}
    $javaInfo=Get-JavaMajor $rootFull -Details:$ValidatedOnly
    $javaMajor=if($ValidatedOnly){$javaInfo.Major}else{$javaInfo}
    if($javaMajor -lt $expectedJavaMajor){throw "É necessário Java $expectedJavaMajor ou superior."}
    if($ValidatedOnly -and !$Experimental -and $javaMajor -ne $expectedJavaMajor){throw "A abertura pelo perfil gráfico foi validada apenas com Java $expectedJavaMajor."}
    if($ValidatedOnly -and !$Experimental -and (!$componentConfig.requiredJavaVersion -or $javaInfo.Version -ne $componentConfig.requiredJavaVersion -or $javaInfo.Architecture -notin @('amd64','x86_64'))){throw 'Runtime de abertura diferente do perfil comprovado.'}
    if($Experimental -and $javaInfo.Architecture -notin @('amd64','x86_64')){throw 'A abertura experimental requer Java Windows x64.'}
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
    if($Component -eq 'translation' -or $WithTranslation){$arguments+=@('-nl','pt_BR')}
    if($Component -eq 'themes' -or $WithThemes){$arguments+=@('-pluginCustomization',('"'+(Join-Path $StateDirectory 'padroes.ini')+'"'))}
    return [pscustomobject]@{Root=$rootFull;Version=$mf['Bundle-Version'];Java=$javaMajor;Executable=$exe;Arguments=$arguments;NeedsClean=$needsClean;StateFile=$stateFile;Signature=$signature}
}

$installMutex=$null;$ownsInstallMutex=$false
try {
    if(-not $StudioPath){if($ValidateOnly){throw 'Informe -StudioPath para validar sem interface.'};$StudioPath=Choose-Studio}
    if($Launch){
        $launchPlan=Get-LaunchPlan $StudioPath $PSScriptRoot $componentConfig.component -WithThemes:($componentConfig.launchThemes -eq $true) -WithTranslation:($componentConfig.launchTranslation -eq $true)
        if($ValidateOnly){$launchPlan|Select-Object Root,Version,Java,Arguments,NeedsClean;exit 0}
        if($launchPlan.Arguments.Count){Start-Process -FilePath $launchPlan.Executable -ArgumentList $launchPlan.Arguments}else{Start-Process -FilePath $launchPlan.Executable}
        try{@{signature=$launchPlan.Signature}|ConvertTo-Json|Set-Content -LiteralPath $launchPlan.StateFile -Encoding UTF8}catch{ }
        exit 0
    }
    $plan=Get-Plan $StudioPath
    if($ValidateOnly){$view=$plan|Select-Object Root,Version,Java,@{n='Plugins';e={$_.Files.Count}},@{n='OpcionaisAusentes';e={$_.Skipped.Count}},@{n='FileNames';e={@($_.Files|ForEach-Object Name)}};if($PlanJson){[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false);$view|ConvertTo-Json -Compress}else{$view};exit 0}
    $principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
        $argsList=@('-NoProfile','-ExecutionPolicy','Bypass','-STA','-File',('"'+$PSCommandPath+'"'),'-StudioPath',('"'+$plan.Root+'"'),'-Elevated')
        $argsList+=@('-RegistryUserProfile',('"'+$RegistryUserProfile+'"'))
        if($Quiet){$argsList+='-Quiet'}
        if($ValidatedOnly){$argsList+='-ValidatedOnly'}
        if($Experimental){$argsList+='-Experimental'}
        $child=Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -ArgumentList $argsList -Wait -PassThru
        exit $child.ExitCode
    }
    if($ValidatedOnly){
        $sha=[Security.Cryptography.SHA256]::Create()
        try{$lockId=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($plan.Root.ToLowerInvariant())))).Replace('-','')}finally{$sha.Dispose()}
        $installMutex=New-Object Threading.Mutex($false,('Local\Salutem-JasperInstall-'+$lockId))
        try{$ownsInstallMutex=$installMutex.WaitOne(0)}catch [Threading.AbandonedMutexException]{$ownsInstallMutex=$true}
        if(!$ownsInstallMutex){throw 'Outra instalação está atualizando este Studio. Aguarde e confira novamente.'}
    }
    $running=@(Get-Process -ErrorAction SilentlyContinue|Where-Object{$_.ProcessName -like '*Jaspersoft*' -and $_.Path -and $_.Path.StartsWith($plan.Root,[StringComparison]::OrdinalIgnoreCase)})
    if($running.Count){throw 'Salve os relatórios e feche esta instalação do Jaspersoft Studio antes de instalar.'}
    $target=Join-Path $plan.Root 'dropins\eclipse\plugins'
    foreach($file in $plan.Files){$dest=Join-Path $target $file.Name;if(Test-Path -LiteralPath $dest){if((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -ne $file.Hash){throw "Já existe um arquivo diferente: $dest. Nenhum arquivo foi substituído."}}}
    New-Item -ItemType Directory -Force -Path $target|Out-Null
    $added=New-Object 'System.Collections.Generic.List[string]'
    $previous=New-Object 'System.Collections.Generic.List[object]'
    $backup=Join-Path $PSScriptRoot ('backups\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    $runtimeChange=$null
    try {
        if($componentConfig.privateJavaSource){
            if($plan.Version -ne '6.16.0.final'){throw 'Java privado configurado para uma plataforma incorreta.'}
            . (Join-Path $PSScriptRoot 'PrivateRuntime.ps1')
            $runtimeChange=Set-PrivateStudioRuntime $plan.Root $componentConfig.privateJavaSource $componentConfig.privateJavaInventory $backup
        }
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
        foreach($file in $plan.Files){$dest=Join-Path $target $file.Name;if(-not(Test-Path -LiteralPath $dest)){$added.Add($dest);Copy-Item -LiteralPath $file.Source -Destination $dest};if((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -ne $file.Hash){throw "Falha de cópia: $($file.Name)"}}
        # Equinox may rebuild bundle IDs while retaining a registry from the
        # previous bundle inventory. Rebuild only that derived cache, even when
        # reinstalling the same JARs to repair an already affected installation.
        $registryBackup=@(Move-StudioRegistryCache @(Get-StudioRegistryCachePaths $plan.Root $RegistryUserProfile) $backup)
    }catch{
        $originalError=$_.Exception.Message;$recoveryErrors=@()
        if($runtimeChange){try{[IO.File]::WriteAllBytes($runtimeChange.Ini,$runtimeChange.Original)}catch{$recoveryErrors+='INI: '+$_.Exception.Message}}
        foreach($created in $added){try{if([IO.Path]::GetDirectoryName($created) -eq $target -and (Test-Path -LiteralPath $created)){Remove-Item -LiteralPath $created -Force}}catch{$recoveryErrors+=$_.Exception.Message}}
        foreach($old in $previous){try{Move-Item -LiteralPath $old.Backup -Destination $old.Original -Force}catch{$recoveryErrors+=$_.Exception.Message}}
        if($recoveryErrors.Count){throw ($originalError+"`nRecuperação incompleta; preserve o backup em "+$backup+"`n"+($recoveryErrors -join "`n"))}
        throw $originalError
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
}finally{
    if($ownsInstallMutex){$installMutex.ReleaseMutex()}
    if($installMutex){$installMutex.Dispose()}
}
