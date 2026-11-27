#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath,[string]$BaselineStudioPath)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$studio=(Resolve-Path -LiteralPath $StudioPath).Path
$plugins=Join-Path $studio 'plugins'
$core=Get-ChildItem -LiteralPath $plugins -Filter 'com.jaspersoft.studio_*.jar'|Select-Object -First 1
if(!$core -or $core.Name -notmatch '^com\.jaspersoft\.studio_(6\.\d+\.\d+)\.final\.jar$'){throw 'É necessária uma plataforma real Studio 6.x; a linha 7 não é aceita.'}
$version=$matches[1];$versions=Get-Content (Join-Path $root 'versions.json') -Raw|ConvertFrom-Json
if([version]$version -lt [version]'6.6.0' -or [version]$version -gt [version]'6.21.5'){throw 'A faixa desta adaptação é Studio 6.6.0 até 6.21.5.'}
if(!$BaselineStudioPath){$BaselineStudioPath=Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Directory -Filter '*6.20.6*'|Select-Object -First 1 -ExpandProperty FullName}
if(!$BaselineStudioPath){throw 'Informe -BaselineStudioPath para validar traduções Eclipse contra o inglês de referência.'}
$tools=Get-Content (Join-Path $root '.compatibility/tools.json') -Raw|ConvertFrom-Json
$java=($tools.java|Where-Object major -eq 17|Select-Object -First 1).executable
if(!$java){throw 'Prepare o Java 17 de build; o runtime do Studio não será alterado.'}
$ecj=Join-Path $root '.compatibility/bootstrap-ecj-3.33.0.jar'
if(!(Test-Path $ecj)){$ecj=Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Recurse -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar' -ErrorAction SilentlyContinue|Select-Object -First 1 -ExpandProperty FullName}
if(!$ecj){throw 'Execute compatibility/Build-Worker.ps1 para preparar o compilador.'}
$toolchain=Get-Content (Join-Path $PSScriptRoot 'toolchain.json') -Raw|ConvertFrom-Json
$lock=$toolchain.downgrader
$tool=Join-Path $root ('.compatibility/studio-tools/jvmdowngrader-'+$lock.version+'-all.jar')
New-Item -ItemType Directory -Force (Split-Path $tool -Parent)|Out-Null
if(!(Test-Path $tool)){Invoke-WebRequest $lock.url -OutFile $tool}
if((Get-FileHash $tool -Algorithm SHA256).Hash.ToLowerInvariant() -ne $lock.sha256){throw 'Checksum inválido do compilador de compatibilidade.'}
$api=Join-Path (Split-Path $tool -Parent) ('jvmdowngrader-java-api-'+$lock.version+'-downgraded-8.jar')
if(!(Test-Path $api)){Invoke-WebRequest $lock.java8ApiUrl -OutFile $api}
if((Get-FileHash $api -Algorithm SHA256).Hash.ToLowerInvariant() -ne $lock.java8ApiSha256){throw 'Checksum inválido da API Java 8.'}
$jna=Join-Path (Split-Path $tool -Parent) ('jna-'+$toolchain.jna.version+'.jar')
if(!(Test-Path $jna)){Invoke-WebRequest $toolchain.jna.url -OutFile $jna}
if((Get-FileHash $jna -Algorithm SHA256).Hash.ToLowerInvariant() -ne $toolchain.jna.sha256){throw 'Checksum inválido da ponte nativa Windows.'}
$stage=Join-Path $root ('build/studio6-'+$version+'-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage,(Join-Path $stage 'plugins'),(Join-Path $stage 'dependencies')|Out-Null
& node (Join-Path $PSScriptRoot 'prepare-sources.mjs') (Join-Path $stage 'sources') $version
if($LASTEXITCODE -ne 0){throw 'Falha ao preparar adaptadores.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
# The published Java-8 API has classfile 52 but omits the marker consumed by
# ApiShader. Verify every class before adding that marker to a private copy.
# The checked, published input remains unchanged in the download cache.
$apiDirectory=Join-Path $stage 'java8-api'
[IO.Compression.ZipFile]::ExtractToDirectory($api,$apiDirectory)
if(Get-ChildItem (Join-Path $apiDirectory 'META-INF') -File|Where-Object Extension -in @('.SF','.RSA','.DSA')){throw 'Não modificar metadados de API assinada.'}
$apiClasses=@(Get-ChildItem -LiteralPath $apiDirectory -Recurse -File -Filter '*.class')
if(!$apiClasses.Count){throw 'API Java 8 sem classes.'}
foreach($file in $apiClasses){$stream=[IO.File]::OpenRead($file.FullName);try{$header=New-Object byte[] 8;if($stream.Read($header,0,8) -ne 8 -or ([int]$header[6]*256+$header[7]) -gt 52){throw 'API publicada incompatível com Java 8.'}}finally{$stream.Dispose()}}
$apiManifest=Join-Path $apiDirectory 'META-INF/MANIFEST.MF'
$apiText=[IO.File]::ReadAllText($apiManifest).TrimEnd()+"`r`nJvmDowngrader-Downgrade-Target: 52`r`n`r`n"
[IO.File]::WriteAllText($apiManifest,$apiText,[Text.Encoding]::ASCII)
$api=Join-Path $stage 'java8-api-verified.jar'
[IO.Compression.ZipFile]::CreateFromDirectory($apiDirectory,$api)
$nested=@()
foreach($bundle in Get-ChildItem -LiteralPath $plugins -Filter '*.jar'|Where-Object Name -match '^(net\.sf\.jasperreports_|com\.jaspersoft\.studio\.server_)'){
    $zip=[IO.Compression.ZipFile]::OpenRead($bundle.FullName)
    try{foreach($entry in $zip.Entries|Where-Object FullName -match '^lib/(jasperreports-[\d.]+|jasperserver-dto-.*)\.jar$'){$out=Join-Path $stage ('dependencies/'+$entry.Name);[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$out);$nested+=$out}}finally{$zip.Dispose()}
}
$classpath=(@(Get-ChildItem -LiteralPath $plugins -File -Filter '*.jar'|ForEach-Object FullName)+$nested+@($jna)) -join ';'
$hasCss=$false
foreach($jar in Get-ChildItem -LiteralPath $plugins -File -Filter '*.jar'){$zip=[IO.Compression.ZipFile]::OpenRead($jar.FullName);try{if($zip.GetEntry('org/w3c/dom/css/CSSStyleDeclaration.class')){$hasCss=$true;break}}finally{$zip.Dispose()}}
$compilerModules='java.base,java.desktop,java.xml,java.sql,java.naming,java.management,java.scripting,jdk.unsupported'
if(!$hasCss){$compilerModules+=',jdk.xml.dom'}
$results=@()
. (Join-Path $PSScriptRoot 'Backport.ps1')
foreach($module in @('theme','language','connection-order')){
    $source=Join-Path $stage ('sources/'+$module)
    $classes=Join-Path $stage ('classes-'+$module);New-Item -ItemType Directory -Path $classes|Out-Null
    Get-ChildItem -LiteralPath (Join-Path $source 'resources')|ForEach-Object{Copy-Item -LiteralPath $_.FullName -Destination $classes -Recurse}
    $manifest=Join-Path $classes 'META-INF/MANIFEST.MF'
    $mf=[IO.File]::ReadAllText($manifest)
    $moduleVersion=if($module -eq 'theme'){$versions.theme}elseif($module -eq 'language'){$versions.language}else{$versions.connectionOrder}
    $mf=$mf -replace '(?m)^Bundle-Version: [^\r\n]+',('Bundle-Version: '+$moduleVersion+'.port'+$version.Replace('.',''))
    $mf=$mf -replace 'JavaSE-17','JavaSE-1.8'
    if($module -eq 'theme'){
        $mf=$mf.Replace('Bundle-ClassPath: .',('Bundle-ClassPath: .,lib/jna-'+$toolchain.jna.version+'.jar'))
        New-Item -ItemType Directory -Path (Join-Path $classes 'lib')|Out-Null
        Copy-Item -LiteralPath $jna -Destination (Join-Path $classes 'lib')
    }
    [IO.File]::WriteAllText((Join-Path $classes 'THIRD-PARTY-PORT.txt'),"Backported Java API: LGPL-2.1-or-later; https://github.com/unimined/JvmDowngrader/tree/2.0.1`nJNA: Apache-2.0 OR LGPL-2.1-or-later; https://github.com/java-native-access/jna/tree/5.14.0`nOriginal sources and untransformed build are retained by the build workflow; this is an unpublished candidate.`n",[Text.Encoding]::UTF8)
    $bounds=$version.Split('.');$next=$bounds[0]+'.'+$bounds[1]+'.'+([int]$bounds[2]+1)
    $mf=$mf.Replace('[6.20.6,6.20.7)',('['+$version+','+$next+')'))
    [IO.File]::WriteAllText($manifest,$mf,[Text.Encoding]::ASCII)
    $argfile=Join-Path $stage ($module+'.args')
    $arguments=@('-17','--limit-modules',$compilerModules,'-encoding','UTF-8','-classpath',('"'+$classpath.Replace('\','/')+'"'),'-d',('"'+$classes.Replace('\','/')+'"'))+@(Get-ChildItem -LiteralPath (Join-Path $source 'src') -Recurse -Filter '*.java'|ForEach-Object{'"'+$_.FullName.Replace('\','/')+'"'})
    [IO.File]::WriteAllLines($argfile,$arguments,[Text.UTF8Encoding]::new($false))
    & $java '-Dfile.encoding=UTF-8' -jar $ecj ('@'+$argfile) *> (Join-Path $stage ($module+'-compile.log'))
    if($LASTEXITCODE -ne 0){throw "Falha de compilação do módulo $module. Consulte $stage"}
    $raw=Join-Path $stage ($module+'-raw.jar');[IO.Compression.ZipFile]::CreateFromDirectory($classes,$raw)
    $id=if($module -eq 'theme'){'com.salutem.jasper.theme.deepblue'}elseif($module -eq 'language'){'com.salutem.jasper.language.pt_BR'}else{'com.salutem.jasper.connectionorder'}
    $out=Join-Path $stage ('plugins/'+$id+'_'+$moduleVersion+'.port'+$version.Replace('.','')+'.jar')
    Convert-ClassesToJava8 -InputDirectory $classes -OutputJar $out -Classpath $classpath -ApiJar $api -Prefix ('com/salutem/portcompat/'+$module.Replace('-','')) -JavaExecutable $java -ToolJar $tool
    $zip=[IO.Compression.ZipFile]::OpenRead($out)
    try{foreach($entry in $zip.Entries|Where-Object FullName -like '*.class'){$stream=$entry.Open();try{$header=New-Object byte[] 8;if($stream.Read($header,0,8) -ne 8 -or (([int]$header[6]*256)+$header[7]) -gt 52){throw 'Classe incompatível com Java 8.'}}finally{$stream.Dispose()}}}finally{$zip.Dispose()}
    $results+=@{module=$module;path=$out;sha256=(Get-FileHash $out -Algorithm SHA256).Hash;status='compiled-not-runtime-validated'}
    Write-Output ($module+': compilado para a plataforma '+$version+'; bytecode Java 8 conferido.')
}
$translationClasses=Join-Path $stage 'translation-tool';New-Item -ItemType Directory -Path $translationClasses|Out-Null
& $java '-Dfile.encoding=UTF-8' -jar $ecj -8 -encoding UTF-8 -d $translationClasses (Join-Path $PSScriptRoot 'PortTranslations.java') *> (Join-Path $stage 'translations-compile.log')
if($LASTEXITCODE -ne 0){throw ('Falha do gerador de traduções. Consulte '+$stage)}
$fragments=Join-Path $stage 'translation-fragments'
& $java -cp $translationClasses PortTranslations $root $plugins $fragments (Join-Path $BaselineStudioPath 'plugins') ($versions.translationFragments+'.port'+$version.Replace('.','')) *> (Join-Path $stage 'translations-coverage.log')
if($LASTEXITCODE -ne 0){throw ('Falha das traduções específicas. Consulte '+$stage)}
foreach($fragment in Get-ChildItem -LiteralPath $fragments -Directory){
    $out=Join-Path $stage ('plugins/'+$fragment.Name+'_'+$versions.translationFragments+'.port'+$version.Replace('.','')+'.jar')
    [IO.Compression.ZipFile]::CreateFromDirectory($fragment.FullName,$out)
    $results+=@{module='translation-fragment';path=$out;sha256=(Get-FileHash $out -Algorithm SHA256).Hash;status='generated-host-resources-not-runtime-validated'}
}
Get-Content -LiteralPath (Join-Path $stage 'translations-coverage.log')
$platformKind=if((Test-Path -LiteralPath (Join-Path $studio 'Jaspersoft Studio.exe')) -and (Test-Path -LiteralPath (Join-Path $studio 'Jaspersoft Studio.ini'))){'windows-installation'}else{'api-assembly-not-a-validated-windows-distribution'}
$report=@{studio=$version;platformKind=$platformKind;builtAt=(Get-Date).ToUniversalTime().ToString('o');stage=$stage;plugins=$results;dependencies=@($jna);compilerModules=$compilerModules;status='candidate-not-installable-until-runtime-tests';tool=$lock;toolchain=$toolchain}
$report|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $stage 'build.json') -Encoding utf8
$report|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $root ('build/studio6-latest-'+$version+'.json')) -Encoding utf8
$report|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $root 'build/studio6-latest.json') -Encoding utf8
Write-Output 'Candidato separado. Nenhum complemento foi instalado; traduções por host e testes da plataforma ainda são obrigatórios.'
