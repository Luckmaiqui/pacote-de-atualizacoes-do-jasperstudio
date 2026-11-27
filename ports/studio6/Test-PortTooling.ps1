#Requires -Version 7.2
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$stage=Join-Path $root ('build/port-tooling-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path (Join-Path $stage 'classes') -Force|Out-Null
if(!$JavaHome){throw 'Informe -JavaHome de um JDK; nenhum Java do sistema será alterado.'}
$java=Join-Path $JavaHome 'bin/java.exe';$javac=Join-Path $JavaHome 'bin/javac.exe'
if(!(Test-Path $java) -or !(Test-Path $javac)){throw 'JDK incompleto.'}
& $javac --release 8 -encoding UTF-8 -d (Join-Path $stage 'classes') (Join-Path $PSScriptRoot 'PortTranslations.java') (Join-Path $root 'tests/PortTranslationsTest.java')
if($LASTEXITCODE -ne 0){throw 'Falha de compilação do teste das traduções por versão.'}
& $java -cp (Join-Path $stage 'classes') PortTranslationsTest (Join-Path $stage 'fixture')
if($LASTEXITCODE -ne 0){throw 'Falha de regressão das traduções por versão.'}
& node (Join-Path $PSScriptRoot 'prepare-sources.mjs') (Join-Path $stage 'adapted-sources')
if($LASTEXITCODE -ne 0){throw 'Os adaptadores não correspondem mais às fontes atuais.'}
& node (Join-Path $PSScriptRoot 'prepare-sources.mjs') (Join-Path $stage 'adapted-6215') '6.21.5'
if($LASTEXITCODE -ne 0){throw 'Falha do adaptador de controles da 6.21.5.'}
$modern=Get-Content (Join-Path $stage 'adapted-6215/theme/src/com/salutem/theme/Startup.java') -Raw
$legacy=Get-Content (Join-Path $stage 'adapted-sources/theme/src/com/salutem/theme/Startup.java') -Raw
if(!$modern.Contains('ModernCompositeColors.supports(c)') -or $legacy.Contains('ModernCompositeColors')){throw 'Adaptação de widget vazou entre perfis.'}
if(!$modern.Contains('NativePopupColors.install') -or !$legacy.Contains('NativePopupColors.install')){throw 'Pintura dos menus vazou entre perfis.'}
$legacyXml=Get-Content (Join-Path $stage 'adapted-sources/language/resources/plugin.xml') -Raw
$modernXml=Get-Content (Join-Path $stage 'adapted-6215/language/resources/plugin.xml') -Raw
if(!$legacyXml.Contains('defaultHandler="com.salutem.l10n.SelectedLocaleHandler"') -or !$legacyXml.Contains('<commandParameter id="com.jaspersoft.studio.switchlanguage.locale"') -or $modernXml.Contains('SelectedLocaleHandler')){throw 'Registro do seletor de idioma legado inválido.'}
$legacyImport=Get-Content (Join-Path $stage 'adapted-sources/connection-order/src/com/salutem/order/GestaoImportDialog.java') -Raw
if(!$legacyImport.Contains('ExternalGestaoReader.read') -or !(Test-Path (Join-Path $stage 'adapted-sources/connection-order/src/com/salutem/order/ExternalGestaoReader.java'))){throw 'Leitor compartilhado da importação legado ausente.'}
Write-Output 'PASS: ferramentas de porte. Não comprova a interface do Studio.'
