#Requires -Version 5.1
function Set-PrivateStudioRuntime([string]$Root,[string]$Source,[string]$Inventory,[string]$Backup){
 if(!(Test-Path -LiteralPath $Source -PathType Container)){throw 'Java privado não incluído no pacote.'}
 $entries=Get-Content -LiteralPath $Inventory -Raw|ConvertFrom-Json
 $target=Join-Path $Root 'runtimes/salutem-java11-11.0.33-10'
 foreach($entry in $entries){
  if($entry.path -match '(^[\\/]|:|(?:^|[\\/])\.\.(?:[\\/]|$))'){throw 'Caminho inválido no Java privado.'}
  $from=Join-Path $Source $entry.path
  if((Get-FileHash -LiteralPath $from).Hash -ne $entry.sha256){throw 'Java privado com integridade inválida.'}
  $to=Join-Path $target $entry.path
  if(Test-Path -LiteralPath $to){if((Get-FileHash -LiteralPath $to).Hash -ne $entry.sha256){throw 'Runtime privado existente diferente. Não será substituído.'};continue}
  New-Item -ItemType Directory -Force (Split-Path $to -Parent)|Out-Null;Copy-Item -LiteralPath $from -Destination $to
  if((Get-FileHash -LiteralPath $to).Hash -ne $entry.sha256){throw 'Falha ao conferir o Java privado copiado.'}
 }
 $ini=Join-Path $Root 'Jaspersoft Studio.ini';$original=[IO.File]::ReadAllBytes($ini);$text=[IO.File]::ReadAllText($ini)
 if([regex]::Matches($text,'(?m)^-vm\r?$').Count -ne 1){throw 'INI sem uma configuração -vm única.'}
 $java=Join-Path $target 'bin/javaw.exe'
 $updated=[regex]::Replace($text,'(?m)(^-vm\r?\n)[^\r\n]+',[Text.RegularExpressions.MatchEvaluator]{param($m)$m.Groups[1].Value+$java})
 New-Item -ItemType Directory -Force $Backup|Out-Null;[IO.File]::WriteAllBytes((Join-Path $Backup 'Jaspersoft Studio.ini'),$original)
 $temporary=$ini+'.salutem-'+[guid]::NewGuid().ToString('N');$atomicBackup=$ini+'.salutem-backup-'+[guid]::NewGuid().ToString('N')
 [IO.File]::WriteAllText($temporary,$updated,(New-Object Text.UTF8Encoding($false)))
 try{[IO.File]::Replace($temporary,$ini,$atomicBackup)}finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}}
 return [pscustomobject]@{Ini=$ini;Original=$original;Backup=$atomicBackup}
}
