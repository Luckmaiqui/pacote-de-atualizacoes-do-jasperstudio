#Requires -Version 7.2
function Copy-ExperimentalStudioPort {
 param([string]$BuildFile,[string]$Payload,[string]$Repository)
 $build=Get-Content $BuildFile -Raw|ConvertFrom-Json
 if($build.platformKind -ne 'windows-installation' -or $build.studio -notin @('6.6.0','6.16.0','6.21.0','6.21.5')){throw 'Referência experimental desconhecida.'}
 $location=Join-Path $Payload ('experimental/studio-'+$build.studio)
 if(Test-Path $location){throw 'Referência experimental repetida.'}
 $inventories=@{'themes'=@();'translation'=@();'connection-order'=@()};$names=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
 foreach($module in @('theme','language','connection-order')){if(@($build.plugins|Where-Object module -eq $module).Count -ne 1){throw 'Módulo experimental ausente ou repetido.'}}
 foreach($entry in $build.plugins){
  $name=[IO.Path]::GetFileName($entry.path)
  if(!$names.Add($name) -or !$name.EndsWith('.port'+$build.studio.Replace('.','')+'.jar') -or (Get-FileHash $entry.path).Hash -ne $entry.sha256){throw 'Integridade inválida na referência experimental.'}
  $id=switch($entry.module){'theme'{'themes'}'language'{'translation'}'translation-fragment'{'translation'}'connection-order'{'connection-order'}default{throw 'Tipo desconhecido.'}}
  $destination=Join-Path $location ('modules/'+$id+'/pacote/plugins');New-Item -ItemType Directory -Force $destination|Out-Null;Copy-Item $entry.path $destination
  $inventories[$id]+=@{name=$name;sha256=$entry.sha256}
 }
 foreach($id in $inventories.Keys){[IO.File]::WriteAllText((Join-Path $location ('modules/'+$id+'/pacote/arquivos.json')),(ConvertTo-Json -InputObject @($inventories[$id]) -Depth 3),[Text.UTF8Encoding]::new($true))}
 Copy-Item (Join-Path $Repository 'installer/themes/padroes.ini') (Join-Path $location 'modules/themes')
 . (Join-Path $PSScriptRoot 'Redistribution.ps1');Copy-PortSources -Build $build -Repository $Repository -Destination (Join-Path $location 'sources')
 Copy-Item $BuildFile (Join-Path $location 'source-build.json')
 return @{studio=($build.studio+'.final');path=('experimental/studio-'+$build.studio);minimumJava=$(if($build.studio -eq '6.6.0'){8}elseif($build.studio -eq '6.16.0'){11}else{17});status='experimental-not-certified'}
}
