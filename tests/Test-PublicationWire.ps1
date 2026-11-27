#Requires -Version 7.2
param([Parameter(Mandatory)][string]$StudioPath)
$ErrorActionPreference='Stop';$root=Split-Path $PSScriptRoot -Parent;$stage=Join-Path $root ('build/publication-wire-'+[guid]::NewGuid())
New-Item -ItemType Directory (Join-Path $stage 'lib'),(Join-Path $stage 'classes') -Force|Out-Null
$plugins=Join-Path $StudioPath 'plugins';Add-Type -AssemblyName System.IO.Compression.FileSystem
$server=Get-ChildItem $plugins -Filter 'com.jaspersoft.studio.server_*.jar'|Select-Object -First 1;$zip=[IO.Compression.ZipFile]::OpenRead($server.FullName)
$nested=@();try{
 $reader=[IO.StreamReader]::new($zip.GetEntry('META-INF/MANIFEST.MF').Open());$mf=$reader.ReadToEnd() -replace '\r?\n ','';$reader.Dispose();$paths=[regex]::Match($mf,'(?m)^Bundle-ClassPath: ([^\r\n]+)').Groups[1].Value.Split(',');$index=0
 foreach($path in $paths){if(!$path.EndsWith('.jar')){continue};$entry=$zip.GetEntry($path);if(!$entry){Write-Verbose ('Entrada não fornecida pela distribuição: '+$path);continue};$destination=Join-Path $stage ('lib/'+($index++).ToString('D3')+'-'+$entry.Name);[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$destination);$nested+=$destination}
}finally{$zip.Dispose()}
$cp=($nested+@(Get-ChildItem $plugins -Filter '*.jar'|Where-Object Name -match '^(com.google.gson_|com.sun.activation.javax.activation_|org.eclipse.equinox.common_|org.apache.httpcomponents.(httpclient_|httpcore_)|org.apache.commons.(logging_|codec_))'|ForEach-Object FullName)) -join ';'
$tools=Get-Content (Join-Path $root '.compatibility/tools.json') -Raw|ConvertFrom-Json;$java=($tools.java|Where-Object major -eq 17|Select-Object -First 1).executable
$ecj=Get-ChildItem $plugins -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1
$sources=@((Join-Path $root 'modules/connection-order/src/com/salutem/order/PublicationHistory.java'),(Join-Path $root 'modules/connection-order/src/com/salutem/order/PublicationDescriptionStore.java'),(Join-Path $PSScriptRoot 'PublicationWireTest.java'))
& $java -jar $ecj.FullName -17 -encoding UTF-8 -cp $cp -d (Join-Path $stage 'classes') @sources *> (Join-Path $stage 'compile.log')
if($LASTEXITCODE -ne 0){Get-Content (Join-Path $stage 'compile.log');throw 'Compilação do teste de transporte falhou.'}
$start=[Diagnostics.ProcessStartInfo]::new($java);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
foreach($argument in @('-Dfile.encoding=UTF-8','-cp',((Join-Path $stage 'classes')+';'+$cp),'com.salutem.order.PublicationWireTest')){$start.ArgumentList.Add($argument)}
$p=[Diagnostics.Process]::Start($start);$out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync();$timeout=!$p.WaitForExit(30000);if($timeout){$p.Kill($true);$p.WaitForExit()};$code=$p.ExitCode;$p.Dispose();$log=$out.Result+"`n"+$err.Result;[IO.File]::WriteAllText((Join-Path $stage 'test.log'),$log)
@{passed=(!$timeout -and $code -eq 0);scope='Synthetic loopback HTTP only; no real server contacted';log=$log}|ConvertTo-Json|Set-Content (Join-Path $stage 'results.json') -Encoding utf8
Write-Output $log;Write-Output ('Evidência: '+$stage);if($timeout -or $code -ne 0){throw 'Teste de transporte falhou.'}
