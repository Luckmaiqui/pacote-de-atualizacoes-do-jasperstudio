#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath,[Parameter(Mandatory=$true)][string]$PortBuild,[Parameter(Mandatory=$true)][string]$TestResults,[switch]$Launch,[switch]$Wait)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$studio=(Resolve-Path -LiteralPath $StudioPath).Path
$port=Get-Content -LiteralPath $PortBuild -Raw|ConvertFrom-Json
$tested=Get-Content -LiteralPath $TestResults -Raw|ConvertFrom-Json
if($port.platformKind -ne 'windows-installation' -or $port.studio -notmatch '^6\.\d+\.\d+$'){throw 'É necessária uma distribuição Windows 6.x, não uma montagem de APIs.'}
if($tested.schema -ne 2 -or @($tested.failures).Count -or $tested.studio -ne $port.studio){throw 'A suíte de componentes precisa passar antes da prévia.'}
$required=@('CheckAccounts','CheckNativeSelection','CheckReview270','CheckPublishing','CheckThemeSwitch','CheckRepositoryOrder','CheckExplorerSearch','CheckDisabledRendering','GlobalSearchEngineTest','NumericOrderTest','SubreportPathsTest','SubreportInputsTest','RemoteSubreportMatcherTest','GestaoCatalogTest','CheckCustomColors','CheckSaveProgress','PortResourceBindings')
foreach($name in $required){if($name -notin $tested.tests){throw ('Teste obrigatório ausente: '+$name)}}
$plugins=Join-Path $studio 'plugins'
if(!(Test-Path -LiteralPath (Join-Path $plugins ('com.jaspersoft.studio_'+$port.studio+'.final.jar')))){throw 'Distribuição diferente da plataforma compilada.'}
foreach($entry in $port.plugins){
    $previous=@($tested.moduleHashes|Where-Object name -eq ([IO.Path]::GetFileName($entry.path)))
    if($previous.Count -ne 1 -or $previous[0].sha256 -ne $entry.sha256 -or (Get-FileHash -LiteralPath $entry.path -Algorithm SHA256).Hash -ne $entry.sha256){throw 'O conjunto de complementos difere do conjunto testado.'}
}
$java=$tested.runtimeJava
if((Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash -ne $tested.runtimeJavaSha256){throw 'JVM diferente da JVM testada.'}
$stage=Join-Path $root ('build/studio-preview-'+$port.studio+'-'+[guid]::NewGuid())
$configuration=Join-Path $stage 'configuration'
New-Item -ItemType Directory -Force $configuration,(Join-Path $stage 'workspace'),(Join-Path $stage 'user')|Out-Null
# pluginCustomization defines defaults; the workbench can seed another instance
# preference on first launch. Set the theme only in this newly-created test
# workspace, never in an existing user workspace or the Studio installation.
if($port.studio -ne '6.16.0'){
 $settings=Join-Path $stage 'workspace/.metadata/.plugins/org.eclipse.core.runtime/.settings'
 New-Item -ItemType Directory -Force $settings|Out-Null
 [IO.File]::WriteAllLines((Join-Path $settings 'org.eclipse.e4.ui.css.swt.theme.prefs'),@('eclipse.preferences.version=1','themeid=com.salutem.jasper.theme.deepblue'),[Text.Encoding]::ASCII)
}
# A partial .metadata triggers the vendor's old-workspace migration prompt in
# 6.16.0. Let that version initialize its fresh workspace; defaults.ini below
# supplies the theme without inventing a workspace-version marker.
$lines=@('#version=1')
$ids=[Collections.Generic.HashSet[string]]::new()
foreach($line in Get-Content -LiteralPath (Join-Path $studio 'configuration/org.eclipse.equinox.simpleconfigurator/bundles.info')){
    if($line.StartsWith('#') -or !$line.Trim()){continue}
    $entry=$line.Split(',');if($entry.Length -ne 5){throw 'Inventário da distribuição inválido.'}
    # Use only the original platform, not pre-existing custom dropins or
    # remembered external install locations from the user's configuration.
    if($entry[0].StartsWith('com.salutem.') -or $entry[2] -notmatch '^plugins/'){continue}
    $source=Join-Path $studio $entry[2]
    if(!(Test-Path -LiteralPath $source)){throw ('Componente da distribuição ausente: '+$entry[0])}
    $entry[2]=if(Test-Path -LiteralPath $source -PathType Container){([uri]($source+'/')).AbsoluteUri}else{([uri]$source).AbsoluteUri}
    $lines+=($entry -join ',');[void]$ids.Add($entry[0])
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($entry in $port.plugins){
    $zip=[IO.Compression.ZipFile]::OpenRead($entry.path)
    try{$reader=[IO.StreamReader]::new($zip.GetEntry('META-INF/MANIFEST.MF').Open());$manifest=$reader.ReadToEnd() -replace '\r?\n ','';$reader.Dispose()}finally{$zip.Dispose()}
    $id=[regex]::Match($manifest,'(?m)^Bundle-SymbolicName: ([^;\r\n]+)').Groups[1].Value
    $version=[regex]::Match($manifest,'(?m)^Bundle-Version: ([^\r\n]+)').Groups[1].Value
    if(!$ids.Add($id)){throw ('Componente duplicado na prévia: '+$id)}
    $lines+=($id+','+$version+','+([uri]$entry.path).AbsoluteUri+',4,false')
}
[IO.File]::WriteAllLines((Join-Path $configuration 'bundles.info'),$lines,[Text.UTF8Encoding]::new($false))
$framework=Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.osgi_*.jar'|Select-Object -First 1
$simple=Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.equinox.simpleconfigurator_*.jar'|Select-Object -First 1
# The older launcher treats an encoded absolute framework-extension URI as
# a filename. Retain the distribution's install-relative launcher syntax,
# including when the original installation path contains spaces.
$extensions=@(Get-ChildItem -LiteralPath $plugins -Filter '*.jar'|Where-Object Name -Match '^(org\.eclipse\.osgi\.compatibility\.state_|org\.osgi\.framework\.system\.packages\.extra_|javax\.transaction_)'|ForEach-Object{'reference:file:'+$_.Name})
$properties=@('osgi.bundles.defaultStartLevel=4','osgi.sharedConfiguration.area=@none','equinox.use.ds=true','ds.delayed.keepInstances=true','eclipse.product=com.jaspersoft.studio.rcp.product','eclipse.application=com.jaspersoft.studio.rcp.application',('osgi.bundles=reference:'+([uri]$simple.FullName).AbsoluteUri+'@1:start'),('osgi.framework=file:plugins/'+$framework.Name),('osgi.framework.extensions='+($extensions -join ',')),('org.eclipse.equinox.simpleconfigurator.configUrl='+([uri](Join-Path $configuration 'bundles.info')).AbsoluteUri))
[IO.File]::WriteAllLines((Join-Path $configuration 'config.ini'),$properties,[Text.Encoding]::ASCII)
[IO.File]::WriteAllLines((Join-Path $stage 'defaults.ini'),@('org.eclipse.e4.ui.css.swt.theme/themeid=com.salutem.jasper.theme.deepblue','org.eclipse.ui/SHOW_PROGRESS_ON_STARTUP=true'),[Text.Encoding]::ASCII)
$launcher=Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.equinox.launcher_*.jar'|Select-Object -First 1
$ini=[IO.File]::ReadAllLines((Join-Path $studio 'Jaspersoft Studio.ini'));$vmIndex=[Array]::IndexOf($ini,'-vmargs')
if($vmIndex -lt 0){throw 'Argumentos originais da JVM não localizados.'}
$arguments=@($ini[($vmIndex+1)..($ini.Length-1)]|Where-Object{$_ -ne ''})+@(('-Duser.home='+(Join-Path $stage 'user')),'-jar',$launcher.FullName,'-install',$studio,'-configuration',$configuration,'-data',(Join-Path $stage 'workspace'),'-nl','pt_BR','-pluginCustomization',(Join-Path $stage 'defaults.ini'),'-consoleLog')
if([version]$port.studio -lt [version]'6.21.0'){
    $tools=Get-Content (Join-Path $root '.compatibility/tools.json') -Raw|ConvertFrom-Json
    $buildJava=($tools.java|Where-Object major -eq 17|Select-Object -First 1).executable
    $ecj=Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Recurse -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1
    if(!$buildJava -or !$ecj){throw 'Compilador da prévia não disponível.'}
    $bootstrap=Join-Path $stage 'bootstrap';New-Item -ItemType Directory -Path $bootstrap|Out-Null
    & $buildJava -jar $ecj.FullName -8 -encoding UTF-8 -classpath $framework.FullName -d $bootstrap (Join-Path $PSScriptRoot 'StudioPreviewLauncher.java') *> (Join-Path $stage 'bootstrap-compile.log')
    if($LASTEXITCODE -ne 0){throw 'Falha na compilação do bootstrap da prévia.'}
    $properties=@($properties|Where-Object{$_ -notmatch '^osgi.framework(=|\.extensions=)'})+@(('osgi.framework='+([uri]$framework.FullName).AbsoluteUri),'osgi.framework.extensions=')
    [IO.File]::WriteAllLines((Join-Path $configuration 'config.ini'),$properties,[Text.Encoding]::ASCII)
    $arguments=@($ini[($vmIndex+1)..($ini.Length-1)]|Where-Object{$_ -ne ''})+@(('-Duser.home='+(Join-Path $stage 'user')),'-cp',($framework.FullName+';'+$bootstrap),'StudioPreviewLauncher','-install',$studio,'-configuration',$configuration,'-data',(Join-Path $stage 'workspace'),'-nl','pt_BR','-pluginCustomization',(Join-Path $stage 'defaults.ini'),'-consoleLog')
}
[IO.File]::WriteAllText((Join-Path $stage 'launch-arguments.json'),(ConvertTo-Json -InputObject @($arguments)),[Text.UTF8Encoding]::new($false))
$preview=@{studio=$port.studio;stage=$stage;configuration=$configuration;workspace=(Join-Path $stage 'workspace');java=$java;installed=$false;launchRequested=[bool]$Launch}
if($Launch){
    $process=[Diagnostics.ProcessStartInfo]::new($java);$process.UseShellExecute=$false;$process.CreateNoWindow=$true;$process.WorkingDirectory=$stage
    if($Wait){$process.RedirectStandardOutput=$true;$process.RedirectStandardError=$true}
    foreach($variable in @('APPDATA','LOCALAPPDATA','USERPROFILE')){$directory=Join-Path $stage ('user/'+$variable);New-Item -ItemType Directory -Force $directory|Out-Null;$process.Environment[$variable]=$directory}
    foreach($argument in $arguments){$process.ArgumentList.Add($argument)}
    $child=[Diagnostics.Process]::Start($process);$preview.processId=$child.Id
    if($Wait){$stdout=$child.StandardOutput.ReadToEndAsync();$stderr=$child.StandardError.ReadToEndAsync()}
}
$preview|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $stage 'preview.json') -Encoding utf8
Write-Output ('Prévia isolada preparada: '+$stage+'. Nenhum arquivo foi instalado na distribuição original.')
if($Launch){
    try{if($Wait){$child.WaitForExit();[IO.File]::WriteAllText((Join-Path $stage 'stdout.log'),$stdout.Result);[IO.File]::WriteAllText((Join-Path $stage 'stderr.log'),$stderr.Result);if($child.ExitCode -ne 0){throw ('A prévia terminou com código '+$child.ExitCode+'. Consulte os logs em '+$stage)}}}finally{$child.Dispose()}
}
