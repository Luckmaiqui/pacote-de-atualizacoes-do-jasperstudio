#Requires -Version 7.2
$ErrorActionPreference='Stop';$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $root 'installer/windows/Experimental.ps1')
$stage=Join-Path $root ('build/experimental-metadata-'+[guid]::NewGuid());New-Item -ItemType Directory $stage,(Join-Path $stage 'first'),(Join-Path $stage 'second')|Out-Null
$source=Join-Path $stage 'source.jar';$zip=[IO.Compression.ZipFile]::Open($source,[IO.Compression.ZipArchiveMode]::Create)
try{$entry=$zip.CreateEntry('META-INF/MANIFEST.MF');$writer=[IO.StreamWriter]::new($entry.Open());try{$writer.Write("Manifest-Version: 1.0`r`nBundle-SymbolicName: com.salutem.fixture`r`nBundle-Version: 1.2.3.port660`r`nBundle-RequiredExecutionEnvironment: JavaSE-1.8`r`nRequire-Bundle: com.jaspersoft.studio;bundle-version=`"[6.6.0,6.6.1)`"`r`nFragment-Host: org.eclipse.ui;bundle-version=`"[3.0.0,3.0.0]`"`r`n`r`n") }finally{$writer.Dispose()};$entry=$zip.CreateEntry('content.txt');$writer=[IO.StreamWriter]::new($entry.Open());try{$writer.Write('preserved')}finally{$writer.Dispose()}}finally{$zip.Dispose()}
$original=(Get-FileHash $source).Hash;$hosts=@{'com.jaspersoft.studio'='6.16.0.final';'org.eclipse.ui'='3.99.0'}
$first=New-ExperimentJar $source (Join-Path $stage 'first') $hosts '6.16.0.final';$second=New-ExperimentJar $source (Join-Path $stage 'second') $hosts '6.16.0.final'
if((Get-FileHash $first).Hash -ne (Get-FileHash $second).Hash -or (Get-FileHash $source).Hash -ne $original){throw 'Reempacotamento não determinístico ou fonte alterada.'}
$mf=Read-ExperimentManifest $first
if($mf['Bundle-RequiredExecutionEnvironment'] -ne 'JavaSE-1.8' -or $mf['Fragment-Host'] -ne 'org.eclipse.ui;bundle-version="[3.99.0,3.99.0]"' -or $mf['Require-Bundle'] -ne 'com.jaspersoft.studio;bundle-version="[6.16.0.final,6.16.0.final]"'){throw 'Metadados inesperados.'}
$zip=[IO.Compression.ZipFile]::OpenRead($first);try{$reader=[IO.StreamReader]::new($zip.GetEntry('content.txt').Open());try{if($reader.ReadToEnd() -ne 'preserved'){throw 'Conteúdo alterado.'}}finally{$reader.Dispose()}}finally{$zip.Dispose()}
$signed=Join-Path $stage 'signed.jar';Copy-Item $source $signed;$zip=[IO.Compression.ZipFile]::Open($signed,[IO.Compression.ZipArchiveMode]::Update);try{[void]$zip.CreateEntry('META-INF/test.SF')}finally{$zip.Dispose()}
$rejected=$false;try{New-ExperimentJar $signed $stage $hosts '6.16.0.final'|Out-Null}catch{$rejected=$true};if(!$rejected){throw 'JAR assinado aceito para reescrita.'}
Write-Output 'PASS: metadados experimentais, determinismo, conteúdo original preservado e JAR assinado rejeitado.'
