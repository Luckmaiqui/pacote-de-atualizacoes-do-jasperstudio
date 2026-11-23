#Requires -Version 5.1
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'installer/common/RegistryCache.ps1')
function Assert($Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Make-Cache($Path){
    New-Item -ItemType Directory -Force -Path (Join-Path $Path.Cache '.manager')|Out-Null
    'synthetic-contributor'|Set-Content -LiteralPath (Join-Path $Path.Cache '.contributors.1')
    'synthetic-table'|Set-Content -LiteralPath (Join-Path $Path.Cache '.table.1')
}
$fixture=Join-Path $root ('build/registry-cache-test-'+[guid]::NewGuid())
$studio=Join-Path $fixture 'Studio';$profile=Join-Path $fixture 'User'
New-Item -ItemType Directory -Force -Path $studio,$profile|Out-Null
$paths=@(Get-StudioRegistryCachePaths $studio $profile)
Assert ($paths.Count -eq 2) 'Plano deve conter somente configuração compartilhada e a deste usuário/produto.'
Assert ((Get-StudioInstallHash 'C:\Program Files\Jaspersoft\Jaspersoft Studio-6.20.6') -eq '133910603') 'Hash diverge do Equinox real.'
foreach($path in $paths){Make-Cache $path}
$preferences=Join-Path $paths[1].Configuration '.settings\preferences.ini'
$secure=Join-Path $profile '.eclipse\org.eclipse.equinox.security\secure_storage'
$report=Join-Path $fixture 'Workspace\MyReports\example.jrxml'
$other=Join-Path $profile '.eclipse\other-product\configuration\org.eclipse.core.runtime\.table.1'
foreach($file in @($preferences,$secure,$report,$other)){New-Item -ItemType Directory -Force -Path (Split-Path -Parent $file)|Out-Null;'preserve'|Set-Content -LiteralPath $file}
$before=(Get-FileHash -LiteralPath (Join-Path $paths[1].Cache '.contributors.1')).Hash
$saved=@(Move-StudioRegistryCache $paths (Join-Path $fixture 'backup-ok'))
Assert ($saved.Count -eq 2) 'Os dois caches deveriam ser preparados para reconstrução.'
Assert ((Get-FileHash -LiteralPath (Join-Path $saved[1].Backup '.contributors.1')).Hash -eq $before) 'Backup mudou o conteúdo.'
foreach($path in $paths){Assert (-not(Test-Path -LiteralPath $path.Cache)) 'Cache antigo permaneceu ativo.'}
foreach($file in @($preferences,$secure,$report,$other)){Assert ((Get-Content -LiteralPath $file) -eq 'preserve') 'Arquivo fora do cache foi alterado.'}
Assert (@(Move-StudioRegistryCache $paths (Join-Path $fixture 'backup-repeat')).Count -eq 0) 'Repetição deveria ser idempotente.'

# Reject an unknown cache shape before moving any of the other valid caches.
foreach($path in $paths){Make-Cache $path}
'preserve'|Set-Content -LiteralPath (Join-Path $paths[1].Cache 'unknown-preferences.ini')
$failed=$false;try{Move-StudioRegistryCache $paths (Join-Path $fixture 'backup-unknown')}catch{$failed=$true}
Assert $failed 'Conteúdo desconhecido foi aceito.'
foreach($path in $paths){Assert (Test-Path -LiteralPath $path.Cache) 'Validação moveu um cache antes de terminar.'}
# Keep the rejected fixture intact; use new independent paths for rollback.
$rollbackStudio=Join-Path $fixture 'RollbackStudio';New-Item -ItemType Directory -Path $rollbackStudio|Out-Null
$rollback=@(Get-StudioRegistryCachePaths $rollbackStudio (Join-Path $fixture 'RollbackUser'))
foreach($path in $rollback){Make-Cache $path}
$backup=Join-Path $fixture 'backup-rollback';New-Item -ItemType Directory -Force -Path (Join-Path $backup 'registry-cache-1')|Out-Null
$failed=$false;try{Move-StudioRegistryCache $rollback $backup}catch{$failed=$true}
Assert $failed 'Falha de segundo destino não foi propagada.'
foreach($path in $rollback){Assert (Test-Path -LiteralPath (Join-Path $path.Cache '.contributors.1')) 'Falha não restaurou o primeiro cache.'}
$failed=$false;try{Move-StudioRegistryCache @([pscustomobject]@{Configuration=$studio;Cache=(Join-Path $fixture 'Workspace')}) (Join-Path $fixture 'backup-unsafe')}catch{$failed=$true}
Assert $failed 'Caminho fora da configuração foi aceito.'
$failed=$false;try{Move-StudioRegistryCache $rollback (Join-Path $rollback[0].Cache 'backup-inside')}catch{$failed=$true}
Assert $failed 'Backup dentro do cache foi aceito.'
Write-Output 'PASS: hash Equinox, dois caches exatos, backup, preservação de preferências/relatórios/segredos/outro produto, idempotência, validação e rollback.'
