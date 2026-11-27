#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Results)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$result=Get-Content $Results -Raw|ConvertFrom-Json
if($result.studio -ne '6.16.0' -or @($result.failures).Count -ne 0){throw 'Resultados da plataforma 6.16.0 sem falhas são obrigatórios.'}
& (Join-Path $root 'ports/studio6/Record-Evidence.ps1') -Results $Results
$build=Get-Content $result.portBuild -Raw|ConvertFrom-Json
$destination=Join-Path (Split-Path $root -Parent) 'JS-6.16.0-Teste'
$prior=Join-Path $destination 'correcao-conexoes-1.9.1'
$payload=Join-Path $destination 'correcao-6160-1.9.3'
if(Test-Path $payload){throw 'Pacote já existe; preserve o candidato e gere outra versão se necessário.'}
# Reuse only the previously locked private runtime; never user reports or credentials.
foreach($entry in (Get-Content (Join-Path $prior 'security-files.json') -Raw|ConvertFrom-Json)|Where-Object {$_.path.StartsWith('runtime/') -or $_.path -in @('CryptoRuntimeProbe.class','CryptoRuntimeProbe.java','runtime-origem.json')}){
 if($entry.path -match '(^[\\/]|:|(?:^|[\\/])\.\.(?:[\\/]|$))'){throw 'Caminho inválido.'}
 $source=Join-Path $prior $entry.path
 if((Get-FileHash $source).Hash -ne $entry.sha256){throw 'Runtime anterior diverge do inventário.'}
 $target=Join-Path $payload $entry.path;New-Item -ItemType Directory -Force (Split-Path $target -Parent)|Out-Null;Copy-Item $source $target
}
$package=Join-Path $payload 'instalacao-6160'
New-Item -ItemType Directory -Force (Join-Path $package 'pacote/plugins')|Out-Null
foreach($file in @('Instalar.ps1','RegistryCache.ps1')){Copy-Item (Join-Path $root ('installer/common/'+$file)) $package}
foreach($file in @('componente.json','padroes.ini')){Copy-Item (Join-Path $prior ('instalacao-6160/'+$file)) $package}
$inventory=@()
foreach($entry in $build.plugins){
 if((Get-FileHash $entry.path).Hash -ne $entry.sha256){throw 'JAR alterado após teste.'}
 Copy-Item $entry.path (Join-Path $package 'pacote/plugins')
 $inventory+=@{name=[IO.Path]::GetFileName($entry.path);sha256=$entry.sha256}
}
$inventory|ConvertTo-Json -Depth 4|Set-Content (Join-Path $package 'pacote/arquivos.json') -Encoding utf8BOM
Copy-Item $Results (Join-Path $package 'evidencia-componentes.json')
. (Join-Path $root 'ports/studio6/Redistribution.ps1')
Copy-PortSources -Build $build -Repository $root -Destination (Join-Path $package 'fontes')
function Index-Files([string]$Directory,[string]$Name){
 $items=@(Get-ChildItem $Directory -Recurse -File|Where-Object {$_.Name -ne $Name}|Sort-Object FullName|ForEach-Object{@{path=[IO.Path]::GetRelativePath($Directory,$_.FullName).Replace('\','/');sha256=(Get-FileHash $_.FullName).Hash}})
 $path=Join-Path $Directory $Name;$items|ConvertTo-Json -Depth 4|Set-Content $path -Encoding utf8BOM
 return (Get-FileHash $path).Hash
}
$index=Index-Files $package 'hashes-pacote.json'
$wrapper=[IO.File]::ReadAllText((Join-Path $root 'installer/windows/Install-Exact6160.ps1')).Replace('__INDEX_SHA256__',$index)
[IO.File]::WriteAllText((Join-Path $payload 'Instalar-6.16.0.ps1'),$wrapper,[Text.UTF8Encoding]::new($true))
$security=Index-Files $payload 'security-files.json'
$updater=[IO.File]::ReadAllText((Join-Path $root 'installer/windows/Update-Secure6160.ps1')).Replace('__SECURITY_INDEX_SHA256__',$security)
[IO.File]::WriteAllText((Join-Path $destination 'Atualizar-6.16.0.ps1'),$updater,[Text.UTF8Encoding]::new($true))
[IO.File]::WriteAllText((Join-Path $destination 'Verificar-Correcao-6.16.0.ps1'),[IO.File]::ReadAllText((Join-Path $root 'installer/windows/Verify-Secure6160.ps1')),[Text.UTF8Encoding]::new($true))
$launcher=@'
$ErrorActionPreference='Stop'
$studio='C:\Program Files\TIBCO\Jaspersoft Studio-6.16.0'
$exe=Join-Path $studio 'Jaspersoft Studio.exe'
if(!(Test-Path -LiteralPath $exe)){throw 'Studio 6.16.0 não encontrado no destino da correção.'}
Start-Process -FilePath $exe -ArgumentList @('-nl','pt_BR') -WorkingDirectory $studio -WindowStyle Normal
'@
[IO.File]::WriteAllText((Join-Path $destination 'Abrir-Instalado6160.ps1'),$launcher,[Text.UTF8Encoding]::new($true))
$bat="@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Abrir-Instalado6160.ps1`"`r`nif errorlevel 1 pause`r`n"
foreach($name in @('Abrir JS.bat','Abrir JS 6.16 Português.bat')){[IO.File]::WriteAllText((Join-Path $destination $name),$bat,[Text.Encoding]::ASCII)}
Write-Output ('Candidato preparado, sem instalação: '+$destination)
