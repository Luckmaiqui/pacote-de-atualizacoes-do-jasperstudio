#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('theme','language','connection-order')][string]$Module,[Parameter(Mandatory=$true)][string]$RawJar,[Parameter(Mandatory=$true)][string]$ApiJar,[Parameter(Mandatory=$true)][string]$ToolJar,[Parameter(Mandatory=$true)][string]$JavaExecutable,[Parameter(Mandatory=$true)][string]$Classpath,[Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $OutputDirectory){throw 'Escolha um diretório novo para preservar arquivos anteriores.'}
New-Item -ItemType Directory -Path $OutputDirectory|Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$inputDirectory=Join-Path $OutputDirectory 'input'
[IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path $RawJar).Path,$inputDirectory)
. (Join-Path $PSScriptRoot 'Backport.ps1')
Convert-ClassesToJava8 -InputDirectory $inputDirectory -OutputJar (Join-Path $OutputDirectory ($Module+'-relinked.jar')) -Classpath $Classpath -ApiJar (Resolve-Path $ApiJar).Path -Prefix ('com/salutem/portcompat/'+$Module.Replace('-','')) -JavaExecutable (Resolve-Path $JavaExecutable).Path -ToolJar (Resolve-Path $ToolJar).Path
Write-Output 'Módulo religado em pasta separada. Não foi instalado nem validado para uso no Studio.'
