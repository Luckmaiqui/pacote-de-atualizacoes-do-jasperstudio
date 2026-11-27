#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath,[Parameter(Mandatory=$true)][string]$PortBuild,[switch]$SourceCandidate)
$ErrorActionPreference='Stop'
$mutex=[Threading.Mutex]::new($false,'Local\SalutemStudioNativeRegressionTests');$owned=$false
try{
 $owned=$mutex.WaitOne(0);if(!$owned){throw 'Execute as suítes nativas sequencialmente.'}
 $root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
 $build=Get-Content $PortBuild -Raw|ConvertFrom-Json
 if($build.studio -notin @('6.6.0','6.16.0','6.21.5') -or $build.platformKind -ne 'windows-installation'){throw 'Este teste é específico das distribuições Windows 6.6.0, 6.16.0 e 6.21.5.'}
 $theme=@($build.plugins|Where-Object module -eq 'theme')[0]
 if((Get-FileHash $theme.path).Hash -ne $theme.sha256){throw 'Artefato alterado.'}
 $stage=Join-Path $root ('build/native-popup-'+[guid]::NewGuid());New-Item -ItemType Directory -Force (Join-Path $stage 'classes'),(Join-Path $stage 'native')|Out-Null
 $swt=Get-ChildItem (Join-Path $StudioPath 'plugins') -Filter 'org.eclipse.swt.win32.win32.x86_64_*.jar'|Select-Object -First 1
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 $zip=[IO.Compression.ZipFile]::OpenRead($swt.FullName)
 try{foreach($entry in $zip.Entries|Where-Object FullName -match '^[^/]+\.dll$'){[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $stage ('native/'+$entry.Name)))}}finally{$zip.Dispose()}
 $java=((Get-Content (Join-Path $root '.compatibility/tools.json') -Raw|ConvertFrom-Json).java|Where-Object major -eq 17|Select-Object -First 1).executable
 $ecj=Get-ChildItem (Join-Path $StudioPath 'plugins') -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1
 if(!$ecj){$ecj=Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Recurse -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1}
 if(!$ecj){throw 'Compilador de testes ausente; o runtime do Studio não será substituído.'}
 $cp=$theme.path+';'+$swt.FullName+';'+$build.dependencies[0]
 $sources=@((Join-Path $PSScriptRoot 'NativePopupTest.java'));if($SourceCandidate){$sources+=Join-Path $PSScriptRoot 'NativePopupColors.java'}
 & $java -jar $ecj.FullName -8 -encoding UTF-8 -cp $cp -d (Join-Path $stage 'classes') @sources *> (Join-Path $stage 'compile.log')
 if($LASTEXITCODE -ne 0){Get-Content (Join-Path $stage 'compile.log');throw 'Falha de compilação do teste dos menus.'}
 $runtime=Get-ChildItem (Join-Path $StudioPath 'features') -Recurse -Filter java.exe|Select-Object -First 1
 $start=[Diagnostics.ProcessStartInfo]::new($runtime.FullName);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 foreach($argument in @(('-Djava.library.path='+(Join-Path $stage 'native')),'-cp',((Join-Path $stage 'classes')+';'+$cp+';'+(Join-Path $StudioPath 'plugins/*')),'com.salutem.theme.NativePopupTest')){$start.ArgumentList.Add($argument)}
 $process=[Diagnostics.Process]::Start($start);$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
 $timedOut=!$process.WaitForExit(30000);if($timedOut){$process.Kill($true);$process.WaitForExit()}
 $code=$process.ExitCode;$process.Dispose();$log=$stdout.Result+"`n"+$stderr.Result
 [IO.File]::WriteAllText((Join-Path $stage 'test.log'),$log,[Text.Encoding]::UTF8)
 @{schema=1;studio=$build.studio;passed=($code -eq 0 -and !$timedOut);timedOut=$timedOut;sourceCandidate=[bool]$SourceCandidate;runtimeJavaSha256=(Get-FileHash $runtime.FullName).Hash;themeSha256=$theme.sha256;testSourceSha256=(Get-FileHash (Join-Path $PSScriptRoot 'NativePopupTest.java')).Hash;adapterSourceSha256=(Get-FileHash (Join-Path $PSScriptRoot 'NativePopupColors.java')).Hash;scope='Native bar menu rows in seven palettes; context styles unchanged; separate from complete Studio and remote workflows';log=$log}|ConvertTo-Json -Depth 4|Set-Content (Join-Path $stage 'results.json') -Encoding utf8
 Write-Output $log;Write-Output ('Evidência: '+(Join-Path $stage 'results.json'))
 if($timedOut){throw ('Timeout no teste dos menus: '+$stage)}
 if($code -ne 0){throw 'Regressão da pintura nativa dos menus.'}
}finally{if($owned){$mutex.ReleaseMutex()};$mutex.Dispose()}
