#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$PreviewDirectory,[Parameter(Mandatory=$true)][string]$PortBuild,[Parameter(Mandatory=$true)][string]$FocusedResults,[switch]$Launch,[switch]$Wait)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$stage=(Resolve-Path -LiteralPath $PreviewDirectory).Path
$buildRoot=[IO.Path]::GetFullPath((Join-Path $root 'build/'))
if(!$stage.StartsWith($buildRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Use somente uma prévia privada dentro de build.'}
$metadata=Get-Content (Join-Path $stage 'preview.json') -Raw|ConvertFrom-Json
if($metadata.installed -ne $false -or $metadata.studio -ne '6.21.5' -or $metadata.stage -ne $stage){throw 'Não é a prévia privada 6.21.5.'}
foreach($path in @($metadata.workspace,$metadata.configuration)){if(![IO.Path]::GetFullPath($path).StartsWith($stage+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Configuração fora da prévia.'}}
$loadedPath=Join-Path $stage 'loaded-port-build.json'
$loaded=Get-Content $loadedPath -Raw|ConvertFrom-Json
$base=Get-Content (Join-Path $PSScriptRoot 'results/studio-6.21.5.json') -Raw|ConvertFrom-Json
if(@($base.tests|Select-Object -Unique).Count -ne 17 -or $base.platform -ne 'windows-x64'){throw 'A referência completa de componentes está ausente.'}
# Keep ordering/translation from the already tested base. This is a manual
# theme candidate, never an approval or replacement of the 17-case evidence.
foreach($entry in $loaded.plugins){
 $proof=@($base.artifacts|Where-Object name -eq ([IO.Path]::GetFileName($entry.path)))
 if((Get-FileHash $entry.path).Hash -ne $entry.sha256){throw 'Binário da prévia alterado.'}
 if($proof.Count -eq 1 -and $proof[0].sha256 -eq $entry.sha256){continue}
 # Repeated manual theme fixes must retain the previous exact-JAR proof.
 $priorFile=Join-Path $stage ('theme-proof-'+$entry.sha256+'.json')
 if($entry.module -ne 'theme' -or !$loaded.manualThemePatch -or $loaded.manualThemePatch.themeSha256 -ne $entry.sha256 -or !(Test-Path $priorFile)){throw 'Prévia diverge da referência de componentes.'}
 $prior=Get-Content $priorFile -Raw|ConvertFrom-Json
 if((Get-FileHash $priorFile).Hash -ne $loaded.manualThemePatch.focusedProofSha256 -or $prior.passed -ne $true -or $prior.sourceCandidate -ne $false -or $prior.themeSha256 -ne $entry.sha256 -or $loaded.manualThemePatch.baseComponentEvidenceSha256 -ne (Get-FileHash (Join-Path $PSScriptRoot 'results/studio-6.21.5.json')).Hash){throw 'Evidência da candidata manual anterior inválida.'}
}
$port=Get-Content $PortBuild -Raw|ConvertFrom-Json
$focused=Get-Content $FocusedResults -Raw|ConvertFrom-Json
$theme=@($port.plugins|Where-Object module -eq 'theme')
if($port.studio -ne '6.21.5' -or $port.platformKind -ne 'windows-installation' -or $theme.Count -ne 1){throw 'Porte incompatível.'}
$theme=$theme[0]
if($focused.studio -ne '6.21.5' -or $focused.passed -ne $true -or $focused.sourceCandidate -ne $false -or $focused.themeSha256 -ne $theme.sha256 -or (Get-FileHash $theme.path).Hash -ne $theme.sha256){throw 'Falta o teste focado do JAR exato do tema.'}
if($focused.runtimeJavaSha256 -ne (Get-FileHash $metadata.java).Hash -or $base.runtimeJavaSha256 -ne (Get-FileHash $metadata.java).Hash){throw 'JVM diferente das evidências.'}
if($Launch){try{[void][Net.Dns]::GetHostAddresses('gestao.sistemasalutem.com.br')}catch{throw 'A prévia precisa ser iniciada com acesso à rede. O DNS do Gestão não pôde ser consultado; nenhuma configuração de DNS/proxy será alterada.'}}
$info=Join-Path $metadata.configuration 'bundles.info'
$oldInfo=[IO.File]::ReadAllBytes($info);$oldLoaded=[IO.File]::ReadAllBytes($loadedPath)
$lock=[IO.File]::Open((Join-Path $metadata.workspace '.metadata/.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
 $count=0;$lines=@(foreach($line in [IO.File]::ReadAllLines($info)){
  $parts=$line.Split(',');if($parts[0] -eq 'com.salutem.jasper.theme.deepblue'){$parts[2]=([uri]$theme.path).AbsoluteUri;$count++;$parts -join ','}else{$line}
 })
 if($count -ne 1){throw 'Registro do tema ausente ou duplicado.'}
 $patch=@{schema=1;studio='6.21.5';installedIntoOriginalDistribution=$false;status='manual-validation-pending';themeSha256=$theme.sha256;focusedProofSha256=(Get-FileHash $FocusedResults).Hash;baseComponentEvidenceSha256=(Get-FileHash (Join-Path $PSScriptRoot 'results/studio-6.21.5.json')).Hash;scope='Only the theme JAR changed; ordering/translation remain the tested base; no release/install approval';testedAt=(Get-Date).ToUniversalTime().ToString('o')}
 $loaded.plugins=@(foreach($entry in $loaded.plugins){if($entry.module -eq 'theme'){$theme}else{$entry}})
 $loaded|Add-Member -NotePropertyName manualThemePatch -NotePropertyValue $patch -Force
 $proofCopy=Join-Path $stage ('theme-proof-'+$theme.sha256+'.json')
 if(Test-Path $proofCopy){if((Get-FileHash $proofCopy).Hash -ne $patch.focusedProofSha256){throw 'Evidência anterior divergente; não sobrescrever.'}}else{Copy-Item -LiteralPath $FocusedResults -Destination $proofCopy}
 $backup=$info+'.before-'+$theme.sha256.Substring(0,12)
 if(!(Test-Path $backup)){[IO.File]::WriteAllBytes($backup,$oldInfo)}
 [IO.File]::WriteAllLines($info,$lines,[Text.UTF8Encoding]::new($false))
 $loaded|ConvertTo-Json -Depth 10|Set-Content $loadedPath -Encoding utf8
 $patch|ConvertTo-Json -Depth 5|Set-Content (Join-Path $stage 'manual-theme-patch.json') -Encoding utf8
}catch{[IO.File]::WriteAllBytes($info,$oldInfo);[IO.File]::WriteAllBytes($loadedPath,$oldLoaded);throw}finally{$lock.Dispose()}
if($Launch){
 $start=[Diagnostics.ProcessStartInfo]::new($metadata.java);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WorkingDirectory=$stage
 foreach($variable in @('APPDATA','LOCALAPPDATA','USERPROFILE')){$start.Environment[$variable]=Join-Path $stage ('user/'+$variable)}
 foreach($argument in (Get-Content (Join-Path $stage 'launch-arguments.json') -Raw|ConvertFrom-Json)){$start.ArgumentList.Add($argument)}
 $start.ArgumentList.Add('-clean')
 if($Wait){$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true}
 $process=[Diagnostics.Process]::Start($start);Write-Output ('Prévia manual iniciada: '+$process.Id)
 if($Wait){$out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync();$process.WaitForExit();[IO.File]::WriteAllText((Join-Path $stage 'menu-fix-stdout.log'),$out.Result);[IO.File]::WriteAllText((Join-Path $stage 'menu-fix-stderr.log'),$err.Result);if($process.ExitCode -ne 0){throw ('A prévia encerrou com erro: '+$process.ExitCode)}}
}
Write-Output 'Somente a prévia privada foi atualizada. Instalador público e evidência completa permanecem sem aprovação nova.'
