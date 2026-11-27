#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath,[string]$PortBuild,[string[]]$Tests,[string]$RuntimeJava)
$ErrorActionPreference='Stop'
$testRunnerSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
$root=Split-Path -Parent $PSScriptRoot
# Pixel/focus assertions share the interactive Windows desktop. Concurrent
# suites can close each other's popups and produce false regression failures.
$nativeLock=[Threading.Mutex]::new($false,'Local\SalutemStudioNativeRegressionTests')
$ownsNativeLock=$false
try{
$ownsNativeLock=$nativeLock.WaitOne(0)
if(!$ownsNativeLock){throw 'Outra suíte visual nativa está em execução. Execute estas suítes sequencialmente.'}
& node (Join-Path $PSScriptRoot 'Verify.mjs')
if($LASTEXITCODE -ne 0){throw 'Artefatos não correspondem às fontes.'}
$studio=(Resolve-Path -LiteralPath $StudioPath).Path
$plugins=Join-Path $studio 'plugins'
$targetVersion='6.20.6';$bundlePath=Join-Path $root 'artifacts/plugins'
if($PortBuild){
    $port=Get-Content -LiteralPath $PortBuild -Raw|ConvertFrom-Json;$targetVersion=$port.studio
    if($targetVersion -notmatch '^6\.\d+\.\d+$'){throw 'Apenas plataformas 6.x são aceitas.'}
    $bundlePath=Join-Path $port.stage 'plugins'
    foreach($entry in $port.plugins){if((Get-FileHash -LiteralPath $entry.path -Algorithm SHA256).Hash -ne $entry.sha256){throw 'JAR do porte alterado.'}}
}
if(!(Test-Path -LiteralPath (Join-Path $plugins ('com.jaspersoft.studio_'+$targetVersion+'.final.jar')))){throw 'O build dos complementos não corresponde à plataforma fornecida.'}
$java=if($RuntimeJava){(Resolve-Path -LiteralPath $RuntimeJava).Path}elseif(Test-Path -LiteralPath (Join-Path $studio 'features')){Get-ChildItem -LiteralPath (Join-Path $studio 'features') -Recurse -File -Filter java.exe|Select-Object -First 1 -ExpandProperty FullName}else{$null}
$ecj=Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1 -ExpandProperty FullName
$buildJava=$java
if($PortBuild){$runtimeConfig=Get-Content -LiteralPath (Join-Path $root '.compatibility/tools.json') -Raw|ConvertFrom-Json;$buildJava=($runtimeConfig.java|Where-Object major -eq 17|Select-Object -First 1).executable;$ecj=Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Recurse -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1 -ExpandProperty FullName}
if(!$java -or !$ecj -or !$buildJava){throw 'Runtime/compilador do Studio ausente.'}
$stage=Join-Path $root ('build/studio-components-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path (Join-Path $stage 'classes'),(Join-Path $stage 'native') -Force|Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$nested=@()
foreach($spec in @(@{Jar=('com.jaspersoft.studio.server_'+$targetVersion+'.final.jar');Pattern='^lib/jasperserver-dto-.*\.jar$'},@{Jar=('net.sf.jasperreports_'+$targetVersion+'.final.jar');Pattern='^lib/jasperreports-[\d.]+\.jar$'})){
    $zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $plugins $spec.Jar))
    try{$entry=$zip.Entries|Where-Object FullName -match $spec.Pattern|Select-Object -First 1;if(!$entry){throw 'Dependência interna não encontrada.'};$destination=Join-Path $stage ([IO.Path]::GetFileName($entry.FullName));[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$destination);$nested+=$destination}finally{$zip.Dispose()}
}
$swt=Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.swt.win32.win32.x86_64_*.jar'|Select-Object -First 1
if(!$swt){throw 'SWT Windows x64 ausente.'}
$zip=[IO.Compression.ZipFile]::OpenRead($swt.FullName)
try{foreach($entry in $zip.Entries|Where-Object FullName -match '^[^/]+\.dll$'){[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $stage ('native/'+$entry.Name)))}}finally{$zip.Dispose()}
$names=@('CheckAccounts','CheckNativeSelection','CheckReview270','CheckPublishing','CheckThemeSwitch','CheckRepositoryOrder','CheckExplorerSearch','CheckDisabledRendering','GlobalSearchEngineTest','NumericOrderTest','SubreportPathsTest','SubreportInputsTest','RemoteSubreportMatcherTest','GestaoCatalogTest','CheckCustomColors','CheckSaveProgress','CheckPublicationHistory')
if($PortBuild){$names+='PortResourceBindings'}
if($Tests){foreach($test in $Tests){if($test -notin ($names+@('CheckGestaoImport'))){throw 'Teste desconhecido.'}};$names=$Tests}
$sources=@(($names+@('Review270Fixture','PopupColorsFixture','CheckAccounts')|Where-Object{$_ -ne 'PortResourceBindings'}|Select-Object -Unique)|ForEach-Object{Join-Path $root ('tests/'+$_+'.java')})
if($PortBuild){
    $hasNoData=$false;$hasEmpty=$false
    foreach($bundleId in @('com.jaspersoft.studio.data','com.jaspersoft.studio')){
        $dataZip=[IO.Compression.ZipFile]::OpenRead((Join-Path $plugins ($bundleId+'_'+$targetVersion+'.final.jar')))
        try{$hasNoData=$hasNoData -or $null -ne $dataZip.GetEntry('com/jaspersoft/studio/data/noda/NoDataAdapterDescriptor.class');$hasEmpty=$hasEmpty -or $null -ne $dataZip.GetEntry('com/jaspersoft/studio/data/empty/EmptyDataAdapterDescriptor.class')}finally{$dataZip.Dispose()}
    }
    if(!$hasNoData){
        if(!$hasEmpty){throw 'Descritor de dados sintéticos não disponível nesta plataforma.'}
        $fixtureSources=Join-Path $stage 'fixture-sources';New-Item -ItemType Directory -Path $fixtureSources|Out-Null
        $sources=@(foreach($source in $sources){$text=[IO.File]::ReadAllText($source).Replace('com.jaspersoft.studio.data.noda.NoDataAdapterDescriptor','com.jaspersoft.studio.data.empty.EmptyDataAdapterDescriptor').Replace('NoDataAdapterDescriptor','EmptyDataAdapterDescriptor');$copy=Join-Path $fixtureSources ([IO.Path]::GetFileName($source));[IO.File]::WriteAllText($copy,$text,[Text.UTF8Encoding]::new($false));$copy})
    }
    $adaptedSources=Join-Path $stage 'adapted-sources';New-Item -ItemType Directory -Path $adaptedSources|Out-Null
    $sources=@(foreach($source in $sources){
        $text=[IO.File]::ReadAllText($source)
        if([version]$targetVersion -ge [version]'6.21.0'){$text=$text.Replace('new Display()','Display.getDefault()')}
        $text=$text.Replace('Field dark=Display.class.getDeclaredField("useDarkModeExplorerTheme");dark.setAccessible(true);if(!dark.getBoolean(d))','if(!com.salutem.tests.LegacyAssertions.dark(d,"useDarkModeExplorerTheme"))')
        $text=$text.Replace('dark.setBoolean(d,false)','com.salutem.tests.LegacyAssertions.setDark(d,"useDarkModeExplorerTheme",false)').Replace('dark.getBoolean(d)','com.salutem.tests.LegacyAssertions.dark(d,"useDarkModeExplorerTheme")')
        $text=$text.Replace('d.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme",Boolean.FALSE)','com.salutem.tests.LegacyAssertions.setDark(d,"useDarkModeExplorerTheme",false)')
        $text=$text.Replace('display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme",Boolean.FALSE)','com.salutem.tests.LegacyAssertions.setDark(display,"useDarkModeExplorerTheme",false)').Replace('display.setData("org.eclipse.swt.internal.win32.Combo.useDarkTheme",Boolean.FALSE)','com.salutem.tests.LegacyAssertions.setDark(display,"comboUseDarkTheme",false)')
        $text=$text.Replace('Field mode=Display.class.getDeclaredField(name);mode.setAccessible(true);if(!mode.getBoolean(display))','if(!com.salutem.tests.LegacyAssertions.dark(display,name))')
        $text=$text.Replace('Field nativeColor=Display.class.getDeclaredField("disabledLabelForegroundPixel");nativeColor.setAccessible(true);','')
        $text=$text.Replace('nativeColor.getInt(display)','com.salutem.tests.LegacyAssertions.disabledForeground(display)').Replace('cor nativa desabilitada aplicada','cor desabilitada configurada')
        $copy=Join-Path $adaptedSources ([IO.Path]::GetFileName($source));[IO.File]::WriteAllText($copy,$text,[Text.UTF8Encoding]::new($false));$copy
    })
    $sources+=Join-Path $root 'ports/studio6/LegacyAssertions.java'
    $sources+=@((Join-Path $root 'ports/studio6/NativeSuiteApplication.java'),(Join-Path $root 'tests/ImportRegistryTestLauncher.java'))
}
$extra=if($PortBuild){@($port.dependencies)}else{@()}
$currentOrder=(Get-Content (Join-Path $root 'versions.json') -Raw|ConvertFrom-Json).connectionOrder
$moduleJars=@(Get-ChildItem -LiteralPath $bundlePath -Filter '*.jar'|Where-Object {$PortBuild -or $_.Name -notlike 'com.salutem.jasper.connectionorder_*' -or $_.Name -eq ('com.salutem.jasper.connectionorder_'+$currentOrder+'.jar')}|ForEach-Object FullName)
$classpath=($moduleJars+@(Get-ChildItem -LiteralPath $plugins -Filter '*.jar'|ForEach-Object FullName)+$nested+$extra) -join ';'
$arguments=@('-17','-encoding','UTF-8','-classpath',('"'+$classpath.Replace('\','/')+'"'),'-d',('"'+(Join-Path $stage 'classes').Replace('\','/')+'"'))+@($sources|ForEach-Object{'"'+$_.Replace('\','/')+'"'})
$argfile=Join-Path $stage 'compiler.args';[IO.File]::WriteAllLines($argfile,$arguments,[Text.UTF8Encoding]::new($false))
if($PortBuild){$compilerModules=$port.compilerModules;if(!$compilerModules){$compilerModules='java.base,java.desktop,java.xml,java.sql,java.naming,java.management,java.scripting,jdk.unsupported';if([version]$targetVersion -ge [version]'6.21.0'){$compilerModules+=',jdk.xml.dom'}};$arguments+=@('--limit-modules',$compilerModules);[IO.File]::WriteAllLines($argfile,$arguments,[Text.UTF8Encoding]::new($false))}
$sourceHashes=@{};foreach($source in $sources){$sourceHashes[[IO.Path]::GetFileName($source)]=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()}
& $buildJava '-Dfile.encoding=UTF-8' -jar $ecj ('@'+$argfile)
if($LASTEXITCODE -ne 0){throw 'Compilação dos testes nativos falhou.'}
$packageNames=@{'CheckAccounts'='com.salutem.order.';'CheckReview270'='com.salutem.order.';'CheckExplorerSearch'='com.salutem.order.';'GlobalSearchEngineTest'='com.salutem.order.';'CheckCustomColors'='com.salutem.theme.';'CheckPublicationHistory'='com.salutem.order.';'CheckGestaoImport'='com.salutem.order.'}
$testClasses=Join-Path $stage 'classes'
if($PortBuild){
    . (Join-Path $root 'ports/studio6/Backport.ps1')
    $testClasses=Join-Path $stage 'tests-java8.jar'
    $toolJar=Join-Path $root ('.compatibility/studio-tools/jvmdowngrader-'+$port.tool.version+'-all.jar')
    Convert-ClassesToJava8 -InputDirectory (Join-Path $stage 'classes') -OutputJar $testClasses -Classpath $classpath -ApiJar (Join-Path $port.stage 'java8-api-verified.jar') -Prefix 'com/salutem/portcompat/tests' -JavaExecutable $buildJava -ToolJar $toolJar
}
$runtimeClasspath=($testClasses+';'+(($moduleJars) -join ';')+';'+(Join-Path $plugins '*')+';'+(($nested+$extra) -join ';'))
if($PortBuild){
    # Equinox embeds its resolver. The separately signed Felix resolver must
    # stay outside this flat bootstrap loader; never strip signatures.
    $runtimePlatform=@(foreach($jar in Get-ChildItem -LiteralPath $plugins -Filter '*.jar'){
        $embeddedResolver=$false
        if($jar.Name -notlike 'org.eclipse.osgi_*' -and [version]$targetVersion -ge [version]'6.21.0'){$zip=[IO.Compression.ZipFile]::OpenRead($jar.FullName);try{$embeddedResolver=$null -ne $zip.GetEntry('org/apache/felix/resolver/ResolverImpl.class')}finally{$zip.Dispose()}}
        if(!$embeddedResolver){$jar.FullName}
    })
    $runtimeEntries=@($testClasses)+@($port.plugins|ForEach-Object path)+$runtimePlatform+$nested+$extra
    $attribute='Class-Path: '+(($runtimeEntries|ForEach-Object{if(Test-Path -LiteralPath $_ -PathType Container){([uri]($_+'/')).AbsoluteUri}else{([uri]$_).AbsoluteUri}}) -join ' ')
    $manifest="Manifest-Version: 1.0`r`n";while($attribute.Length -gt 70){$manifest+=$attribute.Substring(0,70)+"`r`n";$attribute=' '+$attribute.Substring(70)};$manifest+=$attribute+"`r`n`r`n"
    $runtimeClasspath=Join-Path $stage 'runtime-classpath.jar';$zip=[IO.Compression.ZipFile]::Open($runtimeClasspath,'Create')
    try{$entry=$zip.CreateEntry('META-INF/MANIFEST.MF');$writer=[IO.StreamWriter]::new($entry.Open(),[Text.Encoding]::ASCII);try{$writer.Write($manifest)}finally{$writer.Dispose()}}finally{$zip.Dispose()}
}
$failures=@()
$executionModes=@{}
# These four tests exercise pure path/comparison functions and Eclipse input
# interfaces, without services or UI. Do not boot the entire product for them.
# UI, preference/service and resource-binding tests keep their real container.
$serviceIndependent=@('SubreportPathsTest','SubreportInputsTest','RemoteSubreportMatcherTest','NumericOrderTest')
if($PortBuild -and ([version]$targetVersion -ge [version]'6.21.0' -or 'PortResourceBindings' -in $names -or 'CheckPublicationHistory' -in $names -or 'CheckGestaoImport' -in $names)){
    . (Join-Path $root 'ports/studio6/TestContainer.ps1')
    $container=New-StudioTestContainer -Stage $stage -Plugins $plugins -TestJar $testClasses -Port $port
}
foreach($name in $names){
    $fullName=if($packageNames.ContainsKey($name)){$packageNames[$name]+$name}else{$name}
    # No console window, but do not force SW_HIDE on the SWT fixture's first
    # window: pixel assertions require that synthetic control to be drawable.
    $start=[Diagnostics.ProcessStartInfo]::new($java);$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    # Eclipse and Studio also consult Windows profile variables, not only
    # user.home. Keep all synthetic preferences outside the user's profile.
    foreach($variable in @('APPDATA','LOCALAPPDATA','USERPROFILE')){
        $isolated=Join-Path $stage ('user/'+$variable)
        New-Item -ItemType Directory -Force $isolated|Out-Null
        $start.Environment[$variable]=$isolated
    }
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $start.StandardOutputEncoding=[Text.Encoding]::UTF8;$start.StandardErrorEncoding=[Text.Encoding]::UTF8
    $launchArguments=@('-Dfile.encoding=UTF-8',('-Duser.home='+(Join-Path $stage 'user')),('-Dswt.library.path='+(Join-Path $stage 'native')),'-cp',$runtimeClasspath)
    $useContainer=$PortBuild -and (([version]$targetVersion -ge [version]'6.21.0' -and $name -notin $serviceIndependent) -or $name -in @('PortResourceBindings','CheckPublicationHistory','CheckGestaoImport'))
    $executionModes[$name]=if($useContainer){'real-osgi-container'}else{'target-api-classpath'}
    if($useContainer){
        $configuration=Join-Path $stage ($name+'-configuration');New-Item -ItemType Directory -Force $configuration|Out-Null
        $properties=@('osgi.bundles.defaultStartLevel=4','osgi.sharedConfiguration.area=@none','osgi.framework.extensions=','eclipse.product=com.jaspersoft.studio.rcp.product',('osgi.bundles=reference:'+([uri]$container.Simple).AbsoluteUri+'@1:start'),('osgi.framework='+([uri]$container.Framework).AbsoluteUri),('org.eclipse.equinox.simpleconfigurator.configUrl='+([uri]$container.Info).AbsoluteUri))
        [IO.File]::WriteAllLines((Join-Path $configuration 'config.ini'),$properties,[Text.Encoding]::ASCII)
        $launchArguments=@('-Dfile.encoding=UTF-8',('-Duser.home='+(Join-Path $stage 'user')),('-Dswt.library.path='+(Join-Path $stage 'native')),('-Dsalutem.test.class='+$fullName),('-Dsalutem.test.resources='+(Join-Path $stage 'expected-resources.tsv')),'-cp',($container.Framework+';'+$container.Bootstrap),'ImportRegistryTestLauncher','-configuration',$configuration,'-data',(Join-Path $configuration 'workspace'),'-application','com.salutem.tests.nativeapp.nativeSuite','-consoleLog')
    }else{$launchArguments+=$fullName}
    # The real distribution exports/opens JDK internals used by its bundled
    # Castor serializer. Reuse only these compatibility switches, never paths,
    # credentials or arbitrary -D options from the product INI.
    $runtimeCompatibilityArguments=@()
    if($useContainer -and [version]$targetVersion -ge [version]'6.21.0'){
        $runtimeCompatibilityArguments=@(Get-Content -LiteralPath (Join-Path $studio 'Jaspersoft Studio.ini')|Where-Object{$_ -match '^--add-(exports|opens|modules)='})
        foreach($argument in $runtimeCompatibilityArguments){$start.ArgumentList.Add($argument)}
    }
    $start.ArgumentList.Add('-Dsalutem.test.studio='+$targetVersion)
    if($name -eq 'PortResourceBindings'){$launchArguments+=@('-nl','pt_BR')}
    foreach($argument in $launchArguments){$start.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::Start($start);$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
    if(!$process.WaitForExit(30000)){$process.Kill($true);$process.WaitForExit();$failures+=$name+' (timeout)'}elseif($process.ExitCode -ne 0){$failures+=$name}
    [IO.File]::WriteAllText((Join-Path $stage ($name+'.out.log')),$stdout.Result,[Text.Encoding]::UTF8)
    [IO.File]::WriteAllText((Join-Path $stage ($name+'.err.log')),$stderr.Result,[Text.Encoding]::UTF8)
    $process.Dispose()
    Get-Content -LiteralPath (Join-Path $stage ($name+'.out.log')) -Encoding utf8
}
$evidence=@{schema=2;studio=$targetVersion;testedAt=(Get-Date).ToUniversalTime().ToString('o');tests=$names;failures=$failures;scope='Componentes reais em instâncias sintéticas; não valida workbench completo';portBuild=$PortBuild;runtimeJava=$java;runtimeJavaSha256=(Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash.ToLowerInvariant();swt=$swt.Name;swtSha256=(Get-FileHash -LiteralPath $swt.FullName -Algorithm SHA256).Hash.ToLowerInvariant();sourceHashes=$sourceHashes;testRunnerSha256=$testRunnerSha256;osgiContainer=($PortBuild -and [version]$targetVersion -ge [version]'6.21.0');moduleHashes=@(if($PortBuild){$port.plugins|ForEach-Object{@{name=[IO.Path]::GetFileName($_.path);sha256=$_.sha256}}})}
$evidence['executionModes']=$executionModes
  $evidence['runtimeCompatibilityArguments']=$runtimeCompatibilityArguments
$evidence|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $stage 'results.json') -Encoding utf8
if($failures.Count){throw ('Testes com falha: '+($failures -join ', ')+'. Logs: '+$stage)}
Write-Output ('PASS: '+$names.Count+' testes de componentes reais em instâncias sintéticas; não substituem inspeção visual do Studio completo. Logs: '+$stage)
}finally{if($ownsNativeLock){$nativeLock.ReleaseMutex()};$nativeLock.Dispose()}
