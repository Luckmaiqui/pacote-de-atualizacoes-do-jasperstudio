#Requires -Version 5.1
[CmdletBinding()]
param([string]$StudioPath='C:\Program Files\TIBCO\Jaspersoft Studio-6.16.0',[switch]$SomenteVerificar)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$payload=Join-Path $PSScriptRoot 'correcao-6160-1.9.3'
$expectedIndex='__SECURITY_INDEX_SHA256__'
$log=Join-Path $PSScriptRoot ('correcao-conexoes-6160-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'.log')
function Report([string]$Text){Write-Host $Text;Add-Content -LiteralPath $log -Value $Text -Encoding UTF8}
function Exec([string]$File,[string[]]$Values){
 $start=New-Object Diagnostics.ProcessStartInfo;$start.FileName=$File
 $start.Arguments=(@($Values|ForEach-Object{'"'+$_+'"'}) -join ' ')
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 $start.StandardOutputEncoding=[Text.Encoding]::UTF8;$start.StandardErrorEncoding=[Text.Encoding]::UTF8
 $p=[Diagnostics.Process]::Start($start)
 try{$out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync();$p.WaitForExit();$code=$p.ExitCode;$text=$out.Result+"`n"+$err.Result}finally{$p.Dispose()}
 Report $text;if($code -ne 0){throw ('Verificação/instalação recusada (código '+$code+'). Consulte o log.')}
}
function Package([switch]$Check){
 $values=@('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $payload 'Instalar-6.16.0.ps1'),'-StudioPath',$StudioPath)
 if($Check){$values+='-SomenteVerificar'}
 Exec (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe') $values
}
function Within([string]$Path,[string]$Root){$full=[IO.Path]::GetFullPath($Path);if(!$full.StartsWith($Root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Caminho fora do destino autorizado.'};return $full}
function Replace-IniFile([string]$Temporary,[string]$Destination,[string]$Backup){
 # Windows PowerShell 5.1 marshals $null as an empty string for this overload.
 # A real, unique backup path keeps File.Replace atomic and avoids that binder issue.
 if([string]::IsNullOrWhiteSpace($Backup)){throw 'Caminho de backup obrigatório para substituir o INI.'}
 if(Test-Path -LiteralPath $Backup){throw 'O backup de substituição já existe; não será sobrescrito.'}
 [IO.File]::Replace($Temporary,$Destination,$Backup)
}
function Runtime-Ini([string]$Text,[string]$Java){
 if([regex]::Matches($Text,'(?m)^-vm\r?$').Count -ne 1 -or $Text -notmatch '(?m)^-vm\r?\n[^\r\n]+'){throw 'O INI precisa conter exatamente uma configuração -vm com um caminho.'}
 if($Java.Contains("`r") -or $Java.Contains("`n")){throw 'Caminho Java inválido.'}
 return [regex]::Replace($Text,'(?m)(^-vm\r?\n)[^\r\n]+',[Text.RegularExpressions.MatchEvaluator]{param($match)$match.Groups[1].Value+$Java})
}
function Locale-Ini([string]$Text){
 $newline=if($Text.Contains("`r`n")){"`r`n"}else{"`n"}
 $lines=@($Text -split '\r?\n');$output=New-Object 'Collections.Generic.List[string]';$vm=$false
 for($i=0;$i -lt $lines.Count;$i++){
  if(!$vm -and $lines[$i] -eq '-vmargs'){$output.Add('-nl');$output.Add('pt_BR');$vm=$true}
  if(!$vm -and $lines[$i] -eq '-nl'){
   if($i+1 -ge $lines.Count -or !$lines[$i+1] -or $lines[$i+1].StartsWith('-')){throw 'Argumento -nl inválido no INI.'}
   $i++;continue
  }
  $output.Add($lines[$i])
 }
 if(!$vm){throw 'INI sem separador -vmargs.'}
 return $output -join $newline
}
try{
 Report 'CORREÇÃO EXCLUSIVA DO STUDIO 6.16.0: tradução 2.2.3, conexões 1.9.3 e Java 11.0.33+10 privado.'
 Report 'Não altera o Java do Windows, certificados, senhas ou configurações de confiança.'
 $index=Join-Path $payload 'security-files.json'
 if((Get-FileHash -LiteralPath $index).Hash -ne $expectedIndex){throw 'Inventário da correção inválido.'}
 $files=Get-Content -LiteralPath $index -Raw|ConvertFrom-Json
 foreach($entry in $files){
  if($entry.path -match '(^[\\/]|:|(?:^|[\\/])\.\.(?:[\\/]|$))'){throw 'Caminho inválido no pacote.'}
  $source=Within (Join-Path $payload $entry.path) $payload
  if((Get-FileHash -LiteralPath $source).Hash -ne $entry.sha256){throw ('Arquivo alterado: '+$entry.path)}
 }
 $StudioPath=(Resolve-Path -LiteralPath $StudioPath).Path
 Package -Check
 Exec (Join-Path $payload 'runtime/bin/java.exe') @('-cp',$payload,'CryptoRuntimeProbe')
 Report 'PASS: pacote 6.16.0 e suporte a HmacPBESHA256 conferidos.'
 if($SomenteVerificar){Report 'VERIFICAÇÃO CONCLUÍDA; nenhuma instalação ou alteração de INI realizada.';return}
 $principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
 if(!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Abra o PowerShell como administrador para aplicar a correção.'}
 $running=@(Get-Process|Where-Object {$_.Path -and $_.Path.StartsWith($StudioPath+'\',[StringComparison]::OrdinalIgnoreCase)})
 if($running.Count){throw 'Salve e feche o Studio 6.16.0 e suas prévias antes de continuar.'}
 $ini=Join-Path $StudioPath 'Jaspersoft Studio.ini'
 $original=[IO.File]::ReadAllBytes($ini);$text=[IO.File]::ReadAllText($ini)
 [void](Runtime-Ini $text 'probe-only')
 $runtime=Within (Join-Path $StudioPath 'runtimes/salutem-java11-11.0.33-10') $StudioPath
 foreach($entry in $files|Where-Object {$_.path.StartsWith('runtime/')} ){
  $relative=$entry.path.Substring('runtime/'.Length)
  $target=Within (Join-Path $runtime $relative) $runtime
  if(Test-Path -LiteralPath $target){if((Get-FileHash -LiteralPath $target).Hash -ne $entry.sha256){throw ('Runtime existente diferente: '+$relative)};continue}
  New-Item -ItemType Directory -Force (Split-Path $target -Parent)|Out-Null
  Copy-Item -LiteralPath (Join-Path $payload $entry.path) -Destination $target
  if((Get-FileHash -LiteralPath $target).Hash -ne $entry.sha256){throw ('Falha ao copiar Java: '+$relative)}
 }
 Exec (Join-Path $runtime 'bin/java.exe') @('-cp',$payload,'CryptoRuntimeProbe')
 $backup=Join-Path $payload ('backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'));New-Item -ItemType Directory -Force $backup|Out-Null
 [IO.File]::WriteAllBytes((Join-Path $backup 'Jaspersoft Studio.ini'),$original)
 $updated=Locale-Ini (Runtime-Ini $text (Join-Path $runtime 'bin/javaw.exe'))
 $temp=Within (Join-Path $StudioPath ('Jaspersoft Studio.ini.salutem-'+[guid]::NewGuid().ToString('N'))) $StudioPath
 [IO.File]::WriteAllText($temp,$updated,[Text.UTF8Encoding]::new($false))
 $changed=$false
 try{
  Replace-IniFile $temp $ini (Join-Path $backup 'Jaspersoft Studio.pre-replace.ini');$changed=$true
  Package
 }catch{
  $failure=$_.Exception.Message
  if($changed){try{[IO.File]::WriteAllBytes($ini,$original);Report 'O INI original foi restaurado após a falha. O Java privado foi mantido para diagnóstico.'}catch{throw ($failure+"`nNão foi possível restaurar o INI: "+$_.Exception.Message+"`nBackup preservado: "+$backup)}}
  throw
 }finally{if(Test-Path -LiteralPath $temp){Remove-Item -LiteralPath $temp -Force}}
 Report 'SUCESSO: Java privado, Português (Brasil) e complementos port6160 atualizados. Reinicie pelo Abrir JS 6.16 Português.bat.'
 Report ('Backup do INI: '+$backup)
}catch{Report ('FALHA: '+$_.Exception.Message);throw}
finally{Write-Host ('Log: '+$log)}
