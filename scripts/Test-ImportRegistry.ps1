#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath,[string]$RegistryUserProfile=[Environment]::GetFolderPath('UserProfile'),[string]$ConnectionOrderJar)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'installer/common/RegistryCache.ps1')
$studio=(Resolve-Path -LiteralPath $StudioPath).Path
$test=Join-Path $root ('build/import-registry-test-'+[guid]::NewGuid())
$stage=Join-Path $test 'bundle';$config=Join-Path $test 'configuration';$workspace=Join-Path $test 'workspace';$testUserHome=Join-Path $test 'user'
New-Item -ItemType Directory -Force -Path $stage,$config,$workspace,$testUserHome,(Join-Path $stage 'META-INF')|Out-Null
$plugins=Join-Path $studio 'plugins'
$java=Get-ChildItem -LiteralPath (Join-Path $studio 'features') -Recurse -File -Filter java.exe|Select-Object -First 1 -ExpandProperty FullName
$compiler=Get-ChildItem -LiteralPath $plugins -File -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1 -ExpandProperty FullName
$classpath=(Get-ChildItem -LiteralPath $plugins -File -Filter '*.jar'|ForEach-Object{$_.FullName}) -join ';'
$classpath+=';'+(Join-Path $root 'build/jasperreports-6.20.6.jar')
$argfile=Join-Path $test 'compile.args'
$compileArgs=@('-17','-encoding','UTF-8','-classpath',('"'+$classpath.Replace('\','/')+'"'),'-d',('"'+$stage.Replace('\','/')+'"'),('"'+(Join-Path $root 'tests/ImportRegistrySmokeApplication.java').Replace('\','/')+'"'),('"'+(Join-Path $root 'tests/ImportRegistryTestLauncher.java').Replace('\','/')+'"'),('"'+(Join-Path $root 'tests/GestaoManagementNativeTest.java').Replace('\','/')+'"'))
[IO.File]::WriteAllLines($argfile,$compileArgs,[Text.UTF8Encoding]::new($false))
& $java '-Dfile.encoding=UTF-8' -jar $compiler ('@'+$argfile)
if($LASTEXITCODE -ne 0){throw 'Compilação do teste falhou.'}
$manifest=@'
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-SymbolicName: com.salutem.tests.importregistry;singleton:=true
Bundle-Version: 1.0.0
Bundle-RequiredExecutionEnvironment: JavaSE-17
Require-Bundle: org.eclipse.equinox.app,org.eclipse.core.runtime,
 org.eclipse.core.commands,org.eclipse.core.expressions,org.eclipse.ui,org.eclipse.swt,
 com.jaspersoft.studio,com.jaspersoft.studio.data,com.jaspersoft.studio.server,
 net.sf.jasperreports,com.google.gson
Bundle-ClassPath: .

'@
[IO.File]::WriteAllText((Join-Path $stage 'META-INF/MANIFEST.MF'),$manifest+[Environment]::NewLine,[Text.Encoding]::ASCII)
[IO.File]::WriteAllText((Join-Path $stage 'plugin.xml'),'<plugin><extension id="importRegistrySmoke" point="org.eclipse.core.runtime.applications"><application><run class="com.salutem.tests.ImportRegistrySmokeApplication"/></application></extension></plugin>')
$bundle=Join-Path $test 'com.salutem.tests.importregistry_1.0.0.jar'
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$bundle)
$areas=@(Get-StudioRegistryCachePaths $studio $RegistryUserProfile)
$inventory=Join-Path $areas[1].Configuration 'org.eclipse.equinox.simpleconfigurator/bundles.info'
if(-not(Test-Path -LiteralPath $inventory)){$inventory=Join-Path $studio 'configuration/org.eclipse.equinox.simpleconfigurator/bundles.info'}
$lines=@('#version=1')
$candidateVersion=$null
if($ConnectionOrderJar){
    $ConnectionOrderJar=(Resolve-Path -LiteralPath $ConnectionOrderJar).Path
    $zip=[IO.Compression.ZipFile]::OpenRead($ConnectionOrderJar)
    try{$reader=[IO.StreamReader]::new($zip.GetEntry('META-INF/MANIFEST.MF').Open());try{$manifestText=$reader.ReadToEnd()}finally{$reader.Dispose()}}finally{$zip.Dispose()}
    if($manifestText -notmatch '(?m)^Bundle-SymbolicName: com\.salutem\.jasper\.connectionorder[;\r\n]'){throw 'Bundle de teste incorreto.'}
    $candidateVersion=[regex]::Match($manifestText,'(?m)^Bundle-Version: ([^\r\n]+)').Groups[1].Value
}
foreach($line in Get-Content -LiteralPath $inventory){
    if(-not $line -or $line.StartsWith('#')){continue}
    $fields=$line.Split(',');if($fields.Count -ne 5){throw 'Inventário nativo inválido.'}
    if($ConnectionOrderJar -and $fields[0] -eq 'com.salutem.jasper.connectionorder'){$fields[1]=$candidateVersion;$fields[2]=([uri]$ConnectionOrderJar).AbsoluteUri}
    if(-not $fields[2].StartsWith('file:')){$fields[2]=([uri](Join-Path $studio $fields[2])).AbsoluteUri}
    $lines+=$fields -join ','
}
$lines+='com.salutem.tests.importregistry,1.0.0,'+([uri]$bundle).AbsoluteUri+',4,false'
$info=Join-Path $config 'bundles.info';[IO.File]::WriteAllLines($info,$lines,[Text.UTF8Encoding]::new($false))
$simple=Get-ChildItem -LiteralPath $plugins -File -Filter 'org.eclipse.equinox.simpleconfigurator_*.jar'|Select-Object -First 1 -ExpandProperty FullName
$framework=Get-ChildItem -LiteralPath $plugins -File -Filter 'org.eclipse.osgi_*.jar'|Select-Object -First 1 -ExpandProperty FullName
$properties=@('osgi.bundles.defaultStartLevel=4','osgi.sharedConfiguration.area=@none','osgi.framework.extensions=','eclipse.product=com.jaspersoft.studio.rcp.product',('osgi.bundles=reference:'+([uri]$simple).AbsoluteUri.Replace('%20',' ')+'@1:start'),('osgi.framework='+([uri]$framework).AbsoluteUri.Replace('%20',' ')),('org.eclipse.equinox.simpleconfigurator.configUrl='+([uri]$info).AbsoluteUri),'eclipse.application=com.salutem.tests.importregistry.importRegistrySmoke')
[IO.File]::WriteAllLines((Join-Path $config 'config.ini'),$properties,[Text.Encoding]::ASCII)
$log=Join-Path $test 'result.log'
$appData=Join-Path $testUserHome 'AppData/Roaming';New-Item -ItemType Directory -Force -Path $appData|Out-Null
$processInfo=[Diagnostics.ProcessStartInfo]::new($java);$processInfo.UseShellExecute=$false;$processInfo.CreateNoWindow=$true;$processInfo.RedirectStandardOutput=$true;$processInfo.RedirectStandardError=$true
$processInfo.Environment['APPDATA']=$appData
foreach($option in Get-Content -LiteralPath (Join-Path $studio 'Jaspersoft Studio.ini')){if($option -match '^--add-(exports|opens)='){$processInfo.ArgumentList.Add($option.Trim())}}
foreach($argument in @('-Dfile.encoding=UTF-8',('-Duser.home='+$testUserHome),('-Dswt.library.path='+(Join-Path $root 'build/native')),('-Dosgi.install.area='+([uri]($studio+'/')).AbsoluteUri),'-cp',($framework+';'+$stage),'ImportRegistryTestLauncher','-configuration',$config,'-data',$workspace,'-application','com.salutem.tests.importregistry.importRegistrySmoke','-consoleLog')){$processInfo.ArgumentList.Add($argument)}
$process=[Diagnostics.Process]::Start($processInfo);$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
try{if(-not $process.WaitForExit(60000)){$process.Kill($true);throw 'Tempo limite do teste de importação excedido.'};$exitCode=$process.ExitCode;$output=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult();[IO.File]::WriteAllText($log,$output,[Text.UTF8Encoding]::new($false))}finally{$process.Dispose()}
Write-Output $output
Write-Output ('Log: '+$log)
if($exitCode -ne 0 -or $output -notmatch 'PASS: registry-owned command' -or $output -notmatch 'PASS: related accounts command' -or $output -match '!STACK|!ENTRY\s+\S+\s+4\s'){throw 'Teste de abertura pelo registro do Equinox falhou.'}
