#Requires -Version 7.2
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop';$root=Split-Path $PSScriptRoot -Parent
if(!$JavaHome){throw 'Informe um JDK para executar os testes independentes de publicação.'}
$classes=Join-Path $root ('build/publication-history-'+[guid]::NewGuid());New-Item -ItemType Directory $classes|Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $root 'modules/connection-order/src/com/salutem/order/PublicationHistory.java') (Join-Path $PSScriptRoot 'PublicationHistoryTest.java')
if($LASTEXITCODE -ne 0){throw 'Compilação dos testes de histórico falhou.'}
& (Join-Path $JavaHome 'bin/java.exe') -cp $classes PublicationHistoryTest
if($LASTEXITCODE -ne 0){throw 'Regressão do histórico de publicação.'}
