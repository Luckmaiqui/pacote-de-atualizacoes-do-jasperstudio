#Requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath,[switch]$ValidateOnly,[string]$RegistryUserProfile=[Environment]::GetFolderPath('UserProfile'))
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'installer/common/RegistryCache.ps1')
$studio=(Resolve-Path -LiteralPath $StudioPath).Path
if(-not(Test-Path -LiteralPath (Join-Path $studio 'Jaspersoft Studio.ini')) -or -not(Test-Path -LiteralPath (Join-Path $studio 'plugins/com.jaspersoft.studio_6.20.6.final.jar'))){throw 'Selecione uma instalação do Studio 6.20.6.'}
$paths=@(Get-StudioRegistryCachePaths $studio $RegistryUserProfile)
if($ValidateOnly){$paths|Select-Object Configuration,Cache,@{n='Existe';e={Test-Path -LiteralPath $_.Cache}};exit 0}
$running=@(Get-Process -ErrorAction SilentlyContinue|Where-Object{$_.ProcessName -like '*Jaspersoft*' -and $_.Path -and $_.Path.StartsWith($studio+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)})
if($running.Count){throw 'Salve seu trabalho e feche esta instalação do Jaspersoft Studio antes de reconstruir o registro de extensões.'}
$backup=Join-Path $root ('build/registry-repair-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$saved=@(Move-StudioRegistryCache $paths $backup)
Write-Output ('Caches preparados para reconstrução: '+$saved.Count)
if($saved.Count){$saved|ConvertTo-Json -Depth 3|Set-Content -LiteralPath (Join-Path $backup 'backup.json') -Encoding UTF8;Write-Output ('Backup: '+$backup)}
Write-Output 'Reabra o JasperStudio. O Eclipse reconstruirá seu registro de extensões sem alterar relatórios, conexões, preferências ou armazenamento seguro.'
