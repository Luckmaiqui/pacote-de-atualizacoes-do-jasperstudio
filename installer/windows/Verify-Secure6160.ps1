#Requires -Version 5.1
[CmdletBinding()]
param([string]$StudioPath='C:\Program Files\TIBCO\Jaspersoft Studio-6.16.0')
$ErrorActionPreference='Stop'
# Read-only installation checks. Only diagnostic files in this package are created.
& (Join-Path $PSScriptRoot 'Atualizar-6.16.0.ps1') -StudioPath $StudioPath -SomenteVerificar
$payload=Join-Path $PSScriptRoot 'correcao-6160-1.9.3/instalacao-6160'
$inventory=Get-Content (Join-Path $payload 'pacote/arquivos.json') -Raw|ConvertFrom-Json
$pending=@()
foreach($entry in $inventory){
 $file=Join-Path $StudioPath ('dropins/eclipse/plugins/'+$entry.name)
 if(!(Test-Path -LiteralPath $file) -or (Get-FileHash -LiteralPath $file).Hash -ne $entry.sha256){$pending+=$entry.name}
}
$ini=[IO.File]::ReadAllText((Join-Path $StudioPath 'Jaspersoft Studio.ini'))
if($ini -notmatch '(?m)^-nl\r?\npt_BR\r?$'){$pending+='Idioma pt_BR no INI'}
$reader=Join-Path $payload 'fontes/adapted-modules/connection-order/resources/gestao-reader/GestaoReader.exe'
$output=Join-Path $PSScriptRoot ('verificacao-leitor-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'.json')
$arguments=@('--self-test','--output',('"'+$output+'"'),'--studio-exe',('"'+(Join-Path $StudioPath 'Jaspersoft Studio.exe')+'"'),'--background','"#090e18"','--foreground','"#ffffff"')
$process=Start-Process -FilePath $reader -ArgumentList $arguments -WindowStyle Hidden -PassThru
if(!$process.WaitForExit(30000)){Stop-Process -Id $process.Id;throw 'O diagnóstico do leitor excedeu 30 segundos.'}
if($process.ExitCode -ne 0 -or !(Test-Path -LiteralPath $output)){throw 'O leitor WebView2 não iniciou; consulte o diagnóstico nesta pasta.'}
$result=Get-Content -LiteralPath $output -Raw|ConvertFrom-Json
if(!$result.passed){throw 'O diagnóstico do leitor falhou.'}
Write-Host 'PASS: WebView2, botão independente da rota, ícone do Jasper e aparência compartilhada.'
if($pending.Count){Write-Host 'PACOTE PRONTO, MAS A ATUALIZAÇÃO AINDA NÃO ESTÁ APLICADA INTEGRALMENTE:';foreach($name in $pending){Write-Host (' - '+$name)};Write-Host 'Feche o Studio e execute Atualizar-6.16.0.ps1 como administrador.';return}
Write-Host 'PASS: 137 JARs instalados conferidos por SHA-256 e idioma pt_BR configurado.'
Write-Host 'A leitura real do Gestão e o tempo de pesquisa nos servidores devem ser conferidos no Studio.'
