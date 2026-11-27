#Requires -Version 5.1
[CmdletBinding()]
param(
 [ValidateSet('Diagnosticar','Instalar','Abrir')][string]$Acao='Diagnosticar',
 [string]$Instalador=(Join-Path $PSScriptRoot 'JasperStudio.exe'),
 [string[]]$StudioPath
)
$ErrorActionPreference='Stop'
if(!(Test-Path -LiteralPath $Instalador -PathType Leaf)){throw 'Extraia JasperStudio.exe do ZIP e informe -Instalador com o caminho completo.'}
$Instalador=(Resolve-Path -LiteralPath $Instalador).Path
$logs=Join-Path $PSScriptRoot ('Diagnosticos/'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force $logs|Out-Null
function Executar([string[]]$Argumentos){
 $p=Start-Process -FilePath $Instalador -ArgumentList $Argumentos -WindowStyle Hidden -PassThru
 if(!$p.WaitForExit(180000)){Stop-Process -Id $p.Id;throw 'A verificação excedeu três minutos. Consulte os diagnósticos.'}
 return $p.ExitCode
}
if($Acao -ne 'Diagnosticar'){
 $argumentos=if($Acao -eq 'Abrir'){@('--open')}else{@()}
 if($argumentos.Count){Start-Process -FilePath $Instalador -ArgumentList $argumentos -WindowStyle Normal}else{Start-Process -FilePath $Instalador -WindowStyle Normal}
 Write-Host 'Interface aberta. Marque as instalações e use os botões do instalador.'
 return
}
$detected=Join-Path $logs 'instalacoes.json'
if((Executar @('--detect','--report',('"'+$detected+'"'))) -ne 0){throw 'Falha ao detectar instalações.'}
$found=@(Get-Content -LiteralPath $detected -Raw -Encoding UTF8|ConvertFrom-Json)
if(!$StudioPath){$StudioPath=@($found|ForEach-Object path)}
if(!$StudioPath.Count){Write-Host 'Nenhuma instalação encontrada. Abra o instalador e utilize Adicionar Pasta.';return}
$resumo=@();$n=0
foreach($path in $StudioPath){
 $n++;$path=(Resolve-Path -LiteralPath $path).Path
 $report=Join-Path $logs ('compatibilidade-'+$n+'.json')
 $code=Executar @('--inspect',('"'+$path+'"'),'--report',('"'+$report+'"'))
 if(!(Test-Path -LiteralPath $report)){throw ('Sem relatório para '+$path)}
 $result=Get-Content -LiteralPath $report -Raw -Encoding UTF8|ConvertFrom-Json
 $java='Não identificado';$ini=Get-Content -LiteralPath (Join-Path $path 'Jaspersoft Studio.ini')
 $vm=[Array]::IndexOf($ini,'-vm')
 if($vm -ge 0 -and $vm+1 -lt $ini.Count){
  $jvm=$ini[$vm+1].Trim('"');if(![IO.Path]::IsPathRooted($jvm)){$jvm=Join-Path $path $jvm}
  if(Test-Path -LiteralPath $jvm -PathType Container){$jvm=Join-Path $jvm 'java.exe'}
  $release=Join-Path (Split-Path (Split-Path $jvm -Parent) -Parent) 'release'
  if(Test-Path -LiteralPath $release){$line=Get-Content -LiteralPath $release|Where-Object {$_ -like 'JAVA_VERSION=*'}|Select-Object -First 1;$java=$line}
 }
 $resumo+=[pscustomobject]@{Studio=$path;Java=$java;Verificacao=if($code -eq 0 -and $result.ok){'Aprovada'}else{'Falhou'};Instalado=($result.installed -eq $true);Detalhe=$result.message}
}
$resumo|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $logs 'resumo.json') -Encoding UTF8
$resumo|Format-Table Studio,Java,Verificacao,Instalado -AutoSize
foreach($row in $resumo){Write-Host ($row.Studio+': '+$row.Detalhe)}
Write-Host ('Diagnóstico concluído sem instalar componentes. Logs: '+$logs)
Write-Host 'Para instalar, execute este script com -Acao Instalar. Para abrir a interface de abertura, use -Acao Abrir.'
