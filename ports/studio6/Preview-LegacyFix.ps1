#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory)][string]$PreviewDirectory,[Parameter(Mandatory)][string]$PortBuild,[Parameter(Mandatory)][string]$ComponentResults,[Parameter(Mandatory)][string]$MenuResults,[switch]$Launch)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$stage=(Resolve-Path -LiteralPath $PreviewDirectory).Path
if(!$stage.StartsWith([IO.Path]::GetFullPath((Join-Path $root 'build/')),[StringComparison]::OrdinalIgnoreCase)){throw 'Use apenas uma prévia privada dentro de build.'}
$metadata=Get-Content (Join-Path $stage 'preview.json') -Raw|ConvertFrom-Json
if($metadata.installed -ne $false -or $metadata.studio -ne '6.6.0' -or $metadata.stage -ne $stage){throw 'Prévia privada 6.6.0 não identificada.'}
foreach($p in @($metadata.workspace,$metadata.configuration)){if(![IO.Path]::GetFullPath($p).StartsWith($stage+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Caminho fora da prévia.'}}
$loadedPath=Join-Path $stage 'loaded-port-build.json'
if(Test-Path $loadedPath){$loaded=Get-Content $loadedPath -Raw|ConvertFrom-Json}else{
 $themeLine=@(Get-Content (Join-Path $metadata.configuration 'bundles.info')|Where-Object{$_ -like 'com.salutem.jasper.theme.deepblue,*'})
 if($themeLine.Count -ne 1){throw 'Tema anterior não identificado.'}
 $oldTheme=([uri]($themeLine[0].Split(',')[2])).LocalPath
 $oldBuild=Join-Path (Split-Path (Split-Path $oldTheme -Parent) -Parent) 'build.json'
 if(!$oldBuild.StartsWith([IO.Path]::GetFullPath((Join-Path $root 'build/')),[StringComparison]::OrdinalIgnoreCase)){throw 'Artefato fora de build.'}
 $loaded=Get-Content $oldBuild -Raw|ConvertFrom-Json
}
$basePath=Join-Path $PSScriptRoot 'results/studio-6.6.0.json';$base=Get-Content $basePath -Raw|ConvertFrom-Json
if(@($base.tests|Select-Object -Unique).Count -ne 17){throw 'Referência anterior de componentes incompleta.'}
foreach($entry in $loaded.plugins){
 if((Get-FileHash $entry.path).Hash -ne $entry.sha256){throw 'Artefato da prévia alterado.'}
 $proof=@($base.artifacts|Where-Object name -eq ([IO.Path]::GetFileName($entry.path)))
 if($proof.Count -ne 1 -or $proof[0].sha256 -ne $entry.sha256){
  $priorPath=Join-Path $stage 'legacy-component-proof.json';$priorMenuPath=Join-Path $stage 'legacy-menu-proof.json'
  if(!$loaded.manualLegacyPatch -or !(Test-Path $priorPath) -or !(Test-Path $priorMenuPath) -or $entry.module -notin @('theme','language','connection-order')){throw 'Prévia diverge da referência anterior.'}
  if((Get-FileHash $priorPath).Hash -ne $loaded.manualLegacyPatch.componentProofSha256 -or (Get-FileHash $priorMenuPath).Hash -ne $loaded.manualLegacyPatch.menuProofSha256 -or (Get-FileHash $basePath).Hash -ne $loaded.manualLegacyPatch.baseEvidenceSha256){throw 'Evidência anterior alterada.'}
  $prior=Get-Content $priorPath -Raw|ConvertFrom-Json;$priorMenu=Get-Content $priorMenuPath -Raw|ConvertFrom-Json;$priorProof=@($prior.moduleHashes|Where-Object name -eq ([IO.Path]::GetFileName($entry.path)))
  if(@($prior.failures).Count -or $priorProof.Count -ne 1 -or $priorProof[0].sha256 -ne $entry.sha256 -or $priorMenu.passed -ne $true -or $priorMenu.sourceCandidate -ne $false){throw 'Evidência anterior inválida.'}
 }
 $registered=@(Get-Content (Join-Path $metadata.configuration 'bundles.info')|Where-Object{$_ -notlike '#*' -and $_.Split(',').Length -eq 5 -and $_.Split(',')[2] -eq ([uri]$entry.path).AbsoluteUri});if($registered.Count -ne 1){throw 'Registro anterior divergente.'}
}
$port=Get-Content $PortBuild -Raw|ConvertFrom-Json;$tests=Get-Content $ComponentResults -Raw|ConvertFrom-Json;$menu=Get-Content $MenuResults -Raw|ConvertFrom-Json
if($port.studio -ne '6.6.0' -or $port.platformKind -ne 'windows-installation' -or $tests.studio -ne '6.6.0' -or @($tests.failures).Count){throw 'Resultados incompatíveis.'}
foreach($test in @('CheckReview270','CheckNativeSelection','PortResourceBindings')){if($test -notin $tests.tests){throw 'Teste focado ausente.'}}
$modules=@($port.plugins|Where-Object module -in @('theme','language','connection-order'))
if($modules.Count -ne 3){throw 'Conjunto de módulos inválido.'}
foreach($entry in $modules){$proof=@($tests.moduleHashes|Where-Object name -eq ([IO.Path]::GetFileName($entry.path)));if($proof.Count -ne 1 -or $proof[0].sha256 -ne $entry.sha256 -or (Get-FileHash $entry.path).Hash -ne $entry.sha256){throw 'JAR diferente do teste focado.'}}
$theme=@($modules|Where-Object module -eq 'theme')[0]
if($menu.passed -ne $true -or $menu.sourceCandidate -ne $false -or $menu.studio -ne '6.6.0' -or $menu.themeSha256 -ne $theme.sha256){throw 'Teste dos menus ausente ou divergente.'}
foreach($proof in @($tests,$menu,$base)){if($proof.runtimeJavaSha256 -ne (Get-FileHash $metadata.java).Hash){throw 'JVM diferente da evidência.'}}
$info=Join-Path $metadata.configuration 'bundles.info';$previous=[IO.File]::ReadAllBytes($info);$oldLoaded=if(Test-Path $loadedPath){[IO.File]::ReadAllBytes($loadedPath)}else{$null}
$proofBackups=@{};foreach($name in @('legacy-component-proof.json','legacy-menu-proof.json')){$p=Join-Path $stage $name;$proofBackups[$p]=if(Test-Path $p){[IO.File]::ReadAllBytes($p)}else{$null}}
$lock=[IO.File]::Open((Join-Path $metadata.workspace '.metadata/.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
 $replacements=@{};foreach($entry in $modules){$replacements[([IO.Path]::GetFileName($entry.path) -replace '_[^_]+\.jar$','')]=([uri]$entry.path).AbsoluteUri}
 $count=0;$lines=@(foreach($line in [IO.File]::ReadAllLines($info)){$parts=$line.Split(',');if($replacements.ContainsKey($parts[0])){$parts[2]=$replacements[$parts[0]];$count++;$parts -join ','}else{$line}})
 if($count -ne 3){throw 'Registro dos módulos ausente ou duplicado.'}
 Copy-Item $ComponentResults (Join-Path $stage 'legacy-component-proof.json');Copy-Item $MenuResults (Join-Path $stage 'legacy-menu-proof.json')
 $loaded.plugins=@(foreach($entry in $loaded.plugins){$replacement=@($modules|Where-Object module -eq $entry.module);if($replacement.Count -eq 1){$replacement[0]}else{$entry}})
 $loaded|Add-Member -Force -NotePropertyName manualLegacyPatch -NotePropertyValue @{status='manual-validation-pending';baseEvidenceSha256=(Get-FileHash $basePath).Hash;componentProofSha256=(Get-FileHash $ComponentResults).Hash;menuProofSha256=(Get-FileHash $MenuResults).Hash;scope='Three modules only; previous translation fragments and workspace retained; no installation/release approval'}
 if(!(Test-Path ($info+'.before-legacy-fix'))){[IO.File]::WriteAllBytes($info+'.before-legacy-fix',$previous)}
 [IO.File]::WriteAllLines($info,$lines,[Text.UTF8Encoding]::new($false));$loaded|ConvertTo-Json -Depth 10|Set-Content $loadedPath -Encoding utf8
}catch{[IO.File]::WriteAllBytes($info,$previous);if($null -ne $oldLoaded){[IO.File]::WriteAllBytes($loadedPath,$oldLoaded)}elseif(Test-Path $loadedPath){Remove-Item -LiteralPath $loadedPath};foreach($p in $proofBackups.Keys){if($null -ne $proofBackups[$p]){[IO.File]::WriteAllBytes($p,$proofBackups[$p])}elseif(Test-Path $p){Remove-Item -LiteralPath $p}};throw}finally{$lock.Dispose()}
if($Launch){
 $start=[Diagnostics.ProcessStartInfo]::new($metadata.java);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WorkingDirectory=$stage
 foreach($variable in @('APPDATA','LOCALAPPDATA','USERPROFILE')){$start.Environment[$variable]=Join-Path $stage ('user/'+$variable)}
 foreach($argument in (Get-Content (Join-Path $stage 'launch-arguments.json') -Raw|ConvertFrom-Json)){$start.ArgumentList.Add($argument)}
 $start.ArgumentList.Add('-clean');$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 $process=[Diagnostics.Process]::Start($start);Write-Output ('Prévia 6.6.0 iniciada: '+$process.Id)
 $out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync();$process.WaitForExit();[IO.File]::WriteAllText((Join-Path $stage 'legacy-fix-stdout.log'),$out.Result);[IO.File]::WriteAllText((Join-Path $stage 'legacy-fix-stderr.log'),$err.Result)
}
Write-Output 'Somente a prévia privada foi alterada. A validação manual permanece separada.'
