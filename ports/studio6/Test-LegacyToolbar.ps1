#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath,[Parameter(Mandatory=$true)][string]$PortBuild)
$ErrorActionPreference='Stop'
$nativeLock=[Threading.Mutex]::new($false,'Local\SalutemStudioNativeRegressionTests')
$ownsNativeLock=$false
try{
$ownsNativeLock=$nativeLock.WaitOne(0)
if(!$ownsNativeLock){throw 'Outra suíte nativa está em execução; execute os testes sequencialmente.'}
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$build=Get-Content -LiteralPath $PortBuild -Raw|ConvertFrom-Json
if($build.studio -notin @('6.6.0','6.21.5') -or $build.platformKind -ne 'windows-installation'){throw 'Este teste cobre as distribuições Windows 6.6.0 e 6.21.5 disponíveis.'}
$test=if($build.studio -eq '6.6.0'){'LegacyToolbarTest'}else{'ModernToolbarTest'}
$theme=@($build.plugins|Where-Object module -eq 'theme')
if($theme.Count -ne 1 -or (Get-FileHash $theme[0].path -Algorithm SHA256).Hash -ne $theme[0].sha256){throw 'Tema diferente do inventário compilado.'}
$stage=Join-Path $root ('build/legacy-toolbar-'+[guid]::NewGuid())
New-Item -ItemType Directory -Force (Join-Path $stage 'classes'),(Join-Path $stage 'native')|Out-Null
$swt=Get-ChildItem (Join-Path $StudioPath 'plugins') -Filter 'org.eclipse.swt.win32.win32.x86_64_*.jar'|Select-Object -First 1
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($swt.FullName)
try{foreach($entry in $zip.Entries|Where-Object FullName -match '^[^/]+\.dll$'){[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $stage ('native/'+$entry.Name)))}}finally{$zip.Dispose()}
$tools=Get-Content (Join-Path $root '.compatibility/tools.json') -Raw|ConvertFrom-Json
$buildJava=($tools.java|Where-Object major -eq 17|Select-Object -First 1).executable
$ecj=Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Recurse -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1
$runtime=Get-ChildItem (Join-Path $StudioPath 'features') -Recurse -Filter java.exe|Select-Object -First 1
$jna=Join-Path $root '.compatibility/studio-tools/jna-5.14.0.jar'
$classpath=$theme[0].path+';'+$swt.FullName+';'+$jna+';'+(Join-Path $StudioPath 'plugins/*')
& $buildJava -jar $ecj.FullName -8 -encoding UTF-8 -classpath $classpath -d (Join-Path $stage 'classes') (Join-Path $PSScriptRoot ($test+'.java')) *> (Join-Path $stage 'compile.log')
if($LASTEXITCODE -ne 0){throw ('Falha na compilação do teste: '+$stage)}
& $runtime.FullName ('-Djava.library.path='+(Join-Path $stage 'native')) -cp ((Join-Path $stage 'classes')+';'+$classpath) ('com.salutem.theme.'+$test) *> (Join-Path $stage 'test.log')
$code=$LASTEXITCODE
$result=@{schema=1;studio=$build.studio;scope='Toolbar text/selection colours, item state and version-specific API; not complete UI approval';passed=($code -eq 0);themeSha256=$theme[0].sha256;runtimeJavaSha256=(Get-FileHash $runtime.FullName -Algorithm SHA256).Hash;testSourceSha256=(Get-FileHash (Join-Path $PSScriptRoot ($test+'.java')) -Algorithm SHA256).Hash;log=(Get-Content (Join-Path $stage 'test.log') -Raw)}
$result|ConvertTo-Json -Depth 4|Set-Content (Join-Path $stage 'results.json') -Encoding utf8
Get-Content (Join-Path $stage 'test.log')
if($code -ne 0){throw ('Regressão da barra de ferramentas: '+$stage)}
Write-Output ('Evidência: '+(Join-Path $stage 'results.json'))
}finally{if($ownsNativeLock){$nativeLock.ReleaseMutex()};$nativeLock.Dispose()}
