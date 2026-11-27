#Requires -Version 5.1
[CmdletBinding()]
param([string]$StudioPath='C:\Program Files\TIBCO\Jaspersoft Studio-6.16.0',[switch]$SomenteVerificar)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$package=Join-Path $PSScriptRoot 'instalacao-6160'
$expectedIndex='__INDEX_SHA256__'
$log=Join-Path $PSScriptRoot ('instalacao-6160-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'.log')
function Record([string]$Text){Write-Host $Text;Add-Content -LiteralPath $log -Value $Text -Encoding UTF8}
function Run-Installer([switch]$Check){
 $exe=Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
 $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $package 'Instalar.ps1'),'-StudioPath',$StudioPath,'-Quiet','-ValidatedOnly','-Experimental')
 if($Check){$arguments+=@('-ValidateOnly','-PlanJson')}
 $start=New-Object Diagnostics.ProcessStartInfo
 $start.FileName=$exe;$start.Arguments=(@($arguments|ForEach-Object{'"'+$_+'"'}) -join ' ')
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 $start.StandardOutputEncoding=[Text.Encoding]::UTF8;$start.StandardErrorEncoding=[Text.Encoding]::UTF8
 $process=[Diagnostics.Process]::Start($start)
 try{$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync();$process.WaitForExit();$output=$stdout.Result;$errors=$stderr.Result;$code=$process.ExitCode}finally{$process.Dispose()}
 if($errors){Add-Content -LiteralPath $log -Value $errors -Encoding UTF8}
 if($code -ne 0){throw ("O instalador recusou a operação (código $code).`n"+$output+"`n"+$errors)}
 return $output
}
try{
 Record 'PACOTE FIXO: Jaspersoft Studio 6.16.0 / port6160 / Java 11 / Windows x64'
 Record 'Não existe seleção automática nem fallback para 6.6.0 neste script.'
 $index=Join-Path $package 'hashes-pacote.json'
 if((Get-FileHash -LiteralPath $index -Algorithm SHA256).Hash -ne $expectedIndex){throw 'O inventário não corresponde ao pacote fixo 6.16.0.'}
 $files=Get-Content -LiteralPath $index -Raw|ConvertFrom-Json
 foreach($file in $files){
  if($file.path -match '(^[\\/]|:|(?:^|[\\/])\.\.(?:[\\/]|$))'){throw 'Caminho inválido no inventário.'}
  $full=Join-Path $package $file.path
  if((Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash -ne $file.sha256){throw ('Arquivo alterado: '+$file.path)}
 }
 $config=Get-Content -LiteralPath (Join-Path $package 'componente.json') -Raw|ConvertFrom-Json
 if($config.requiredStudioVersion -ne '6.16.0.final' -or $config.requiredJavaMajor -ne 11){throw 'Perfil diferente de Studio 6.16.0 / Java 11.'}
 $inventory=Get-Content -LiteralPath (Join-Path $package 'pacote/arquivos.json') -Raw|ConvertFrom-Json
 foreach($file in $inventory){if(!$file.name.EndsWith('.port6160.jar',[StringComparison]::Ordinal)){throw ('JAR de outra versão bloqueado: '+$file.name)}}
 foreach($id in @('com.salutem.jasper.theme.deepblue','com.salutem.jasper.language.pt_BR','com.salutem.jasper.connectionorder')){
  $matching=@($inventory|Where-Object {$_.name.StartsWith($id+'_',[StringComparison]::Ordinal)})
  if($matching.Count -ne 1){throw ('Complemento obrigatório ausente ou duplicado: '+$id)}
  Record ('Arquivo específico: '+$matching[0].name)
 }
 $StudioPath=(Resolve-Path -LiteralPath $StudioPath).Path
 $plan=(Run-Installer -Check)|ConvertFrom-Json
 if($plan.Version -ne '6.16.0.final' -or $plan.Java -ne 11 -or $plan.Plugins -ne $inventory.Count -or $plan.OpcionaisAusentes -ne 0){throw 'Plano diferente do pacote completo 6.16.0; nada foi instalado.'}
 Record ('Destino conferido: '+$plan.Root)
 Record ('Versão conferida: '+$plan.Version+'; Java: '+$plan.Java+'; JARs: '+$plan.Plugins)
 if($SomenteVerificar){Record 'VERIFICAÇÃO CONCLUÍDA. Nenhum complemento instalado.';return}
 $principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
 if(!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Abra o PowerShell como administrador para instalar. A verificação pode ser executada sem administrador.'}
 Record 'Instalando tema, tradução e conexões. O instalador mantém backup e recuperação em falhas.'
 Record (Run-Installer)
 $destination=Join-Path $StudioPath 'dropins\eclipse\plugins'
 foreach($file in $inventory){if((Get-FileHash -LiteralPath (Join-Path $destination $file.name) -Algorithm SHA256).Hash -ne $file.sha256){throw ('Falha de conferência após a instalação: '+$file.name)}}
 Record 'SUCESSO: arquivos port6160 instalados e conferidos por SHA-256. Reinicie o Studio 6.16.0.'
 Record ('Backup, quando necessário: '+(Join-Path $package 'backups'))
}catch{Record ('FALHA: '+$_.Exception.Message);throw}
finally{Write-Host ('Log: '+$log)}
