#Requires -Version 7.2
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$stage=Join-Path $root ('build/compatibility-package-'+[guid]::NewGuid())
$package=Join-Path $stage 'Compatibilidade-Jasper-Java'
New-Item -ItemType Directory -Force $package,(Join-Path $package 'docs'),(Join-Path $root 'dist') | Out-Null
Copy-Item -LiteralPath $PSScriptRoot -Destination (Join-Path $package 'compatibility') -Recurse
New-Item -ItemType Directory -Force (Join-Path $package 'tests') | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'tests/compatibility.test.mjs') -Destination (Join-Path $package 'tests')
foreach($name in @('ARQUITETURA-COMPATIBILIDADE.md','COMPATIBILIDADE-JASPER-JAVA.md')){Copy-Item -LiteralPath (Join-Path (Join-Path $root 'docs') $name) -Destination (Join-Path $package 'docs')}
$readme=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'README.md')).Replace('../docs/','docs/')
[IO.File]::WriteAllText((Join-Path $package 'LEIA-ME.md'),$readme,[Text.UTF8Encoding]::new($false))
$archive=Join-Path $root 'dist/Compatibilidade-Jasper-Java-estudo-local.zip'
if(Test-Path $archive){$archive=Join-Path $root ('dist/Compatibilidade-Jasper-Java-estudo-local-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.zip')}
Compress-Archive -LiteralPath $package -DestinationPath $archive
Get-FileHash $archive
Write-Output 'Ferramenta em desenvolvimento; não é instalador dos complementos para outras versões do Studio.'
