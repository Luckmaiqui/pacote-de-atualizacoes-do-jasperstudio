#Requires -Version 7.2
[CmdletBinding()]
param([string]$PackageDirectory,[string]$StudioPath,[string]$PortStudioPath)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
if(!$PackageDirectory){$PackageDirectory=(Get-Content -LiteralPath (Join-Path $root 'build/latest-windows-package.txt') -Raw).Trim()}
foreach($line in Get-Content -LiteralPath (Join-Path $PackageDirectory 'SHA256SUMS.txt')){if($line -notmatch '^([a-f0-9]{64})  (.+)$'){throw 'Inventário externo inválido.'};if((Get-FileHash -LiteralPath (Join-Path $PackageDirectory $matches[2]) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $matches[1]){throw 'Integridade do pacote externo inválida.'}}
$pe=[IO.File]::ReadAllBytes((Join-Path $PackageDirectory 'JasperStudio.exe'));$header=[BitConverter]::ToInt32($pe,0x3c)
if([BitConverter]::ToUInt16($pe,$header+4) -ne 0x8664){throw 'O executável deve ser Windows x64.'}
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('jsi-'+[guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $testRoot|Out-Null
$payload=Join-Path $testRoot 'Pacote Ação'
$process=Start-Process -FilePath (Join-Path $PackageDirectory 'JasperStudio.exe') -ArgumentList @('--self-test',('"'+$payload+'"')) -WindowStyle Hidden -Wait -PassThru
if($process.ExitCode -ne 0){if(Test-Path (Join-Path $testRoot 'self-test-error.txt')){Get-Content (Join-Path $testRoot 'self-test-error.txt')};throw 'Autoteste do executável falhou.'}
if(!(Get-Content -LiteralPath (Join-Path $testRoot 'self-test.json') -Raw|ConvertFrom-Json).ok){throw 'Autoteste sem evidência.'}
$expectedOrder=(Get-Content (Join-Path $root 'versions.json') -Raw|ConvertFrom-Json).connectionOrder
$orderFiles=@(Get-Content (Join-Path $payload 'modules/connection-order/pacote/arquivos.json') -Raw|ConvertFrom-Json|Where-Object name -like 'com.salutem.jasper.connectionorder_*')
if($orderFiles.Count -ne 1 -or $orderFiles[0].name -ne ('com.salutem.jasper.connectionorder_'+$expectedOrder+'.jar')){throw 'O pacote deve conter somente a versão atual do complemento de conexões.'}
Write-Output 'PASS: somente a versão atual de conexões empacotada; sem IDs duplicados'
$zip=[IO.Compression.ZipFile]::OpenRead(([IO.Path]::GetFullPath($PackageDirectory)+'.zip'));try{if($zip.Entries.Count -ne 1 -or $zip.Entries[0].FullName -ne 'JasperStudio.exe'){throw 'ZIP deve conter somente o executável.'}}finally{$zip.Dispose()}
function Bundle($Studio,$Id,$Version){
    $directory=Join-Path $Studio ('plugins/'+$Id+'_'+$Version+'/META-INF')
    New-Item -ItemType Directory -Path $directory -Force|Out-Null
    [IO.File]::WriteAllText((Join-Path $directory 'MANIFEST.MF'),"Bundle-SymbolicName: $Id`r`nBundle-Version: $Version`r`n")
}
$studio=Join-Path $testRoot 'Studio Sintético'
$syntheticStudio=$studio
New-Item -ItemType Directory -Path (Join-Path $studio 'vm/bin') -Force|Out-Null
[IO.File]::WriteAllText((Join-Path $studio 'vm/bin/java.exe'),'not-executable-fixture')
[IO.File]::WriteAllText((Join-Path $studio 'Jaspersoft Studio.exe'),'not-executable-fixture')
[IO.File]::WriteAllText((Join-Path $studio 'vm/release'),"JAVA_VERSION=`"17.0.8.1`"`r`nOS_ARCH=`"amd64`"")
[IO.File]::WriteAllText((Join-Path $studio 'Jaspersoft Studio.ini'),"-vm`r`nvm/bin/java.exe`r`n-vmargs`r`n-Xmx2048m")
foreach($id in @('com.jaspersoft.studio','com.jaspersoft.studio.data','com.jaspersoft.studio.server')){Bundle $studio $id '6.20.6.final'}
Bundle $studio 'org.eclipse.swt' '3.124.0.v20230523-1044'
Bundle $studio 'org.eclipse.swt.win32.win32.x86_64' '3.124.0.v20230523-1044'
function Inspect([string[]]$Components,[bool]$Expected,[string]$Case){
    $state=Join-Path $testRoot ([guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path $state|Out-Null
    $request=Join-Path $state 'request.json'
    @{action='Inspect';studio=$studio;components=$Components}|ConvertTo-Json|Set-Content -LiteralPath $request -Encoding utf8
    & (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe') -NoProfile -ExecutionPolicy Bypass -File (Join-Path $payload 'Unified.ps1') -RequestFile $request
    $code=$LASTEXITCODE
    $response=Get-Content -LiteralPath (Join-Path $state 'result.json') -Raw|ConvertFrom-Json
    if($response.ok -ne $Expected -or (($code -eq 0) -ne $Expected)){throw ($Case+': '+$response.message)}
    if($studio -eq $syntheticStudio -and (Test-Path -LiteralPath (Join-Path $studio 'dropins'))){throw 'Inspeção alterou o Studio sintético.'}
    Write-Output ('PASS: '+$Case)
}
Inspect @('themes') $true 'Pedido legado de tema convertido em pacote completo'
Inspect @('themes','translation','connection-order') $true 'Plano único dos três módulos; opcionais ausentes tratados'
[IO.File]::WriteAllText((Join-Path $studio 'vm/release'),'JAVA_VERSION="21.0.2"')
Inspect @('themes') $false 'Java sem validação de interface bloqueado'
[IO.File]::WriteAllText((Join-Path $studio 'vm/release'),"JAVA_VERSION=`"17.0.13`"`r`nOS_ARCH=`"amd64`"")
Inspect @('themes') $false 'Patch de Java não comprovado bloqueado'
[IO.File]::WriteAllText((Join-Path $studio 'vm/release'),"JAVA_VERSION=`"17.0.8.1`"`r`nOS_ARCH=`"x86`"")
Inspect @('themes') $false 'Arquitetura Java incompatível bloqueada'
[IO.File]::WriteAllText((Join-Path $studio 'vm/release'),"JAVA_VERSION=`"17.0.8.1`"`r`nOS_ARCH=`"amd64`"")
$originalIni=[IO.File]::ReadAllText((Join-Path $studio 'Jaspersoft Studio.ini'))
[IO.File]::WriteAllText((Join-Path $studio 'Jaspersoft Studio.ini'),'-vmargs')
Inspect @('themes') $false 'JVM implícita bloqueada sem executar Java do PATH'
[IO.File]::WriteAllText((Join-Path $studio 'Jaspersoft Studio.ini'),$originalIni)
[IO.File]::WriteAllText((Join-Path $studio 'plugins/com.jaspersoft.studio_6.20.6.final/META-INF/MANIFEST.MF'),"Bundle-SymbolicName: com.jaspersoft.studio`r`nBundle-Version: 6.6.0.final`r`n")
Inspect @('themes') $false 'Studio legado bloqueado'
Bundle $studio 'com.jaspersoft.studio' '6.20.6.final'
[IO.File]::WriteAllText((Join-Path $studio 'plugins/com.jaspersoft.studio.server_6.20.6.final/META-INF/MANIFEST.MF'),"Bundle-SymbolicName: com.jaspersoft.studio.server`r`nBundle-Version: 0.0.0`r`n")
Inspect @('connection-order') $false 'Host obrigatório incompatível bloqueado'
Inspect @('not-a-module') $false 'Componente desconhecido bloqueado'
$profilesFile=Join-Path $payload 'studio-profiles.json'
$originalProfiles=[IO.File]::ReadAllText($profilesFile)
try{
    foreach($version in @('6.6.0.final','6.16.0.final','6.21.5.final')){
        $studio=Join-Path $testRoot ('Studio '+$version)
        Bundle $studio 'com.jaspersoft.studio' $version
        Inspect @('themes') $false ('Perfil não aprovado ou não incluído bloqueado: '+$version)
    }
    $profiles=ConvertFrom-Json $originalProfiles
    ($profiles.profiles|Where-Object studio -eq '6.6.0.final').uiStatus='validated-port'
    $profiles|ConvertTo-Json -Depth 6|Set-Content $profilesFile -Encoding utf8
    $studio=Join-Path $testRoot 'Studio 6.6.0.final'
    Inspect @('themes') $false 'Perfil sem seus próprios binários não reutiliza módulos de 6.20.6'
}finally{
    [IO.File]::WriteAllText($profilesFile,$originalProfiles,[Text.UTF8Encoding]::new($true))
    $studio=$syntheticStudio
}
if($StudioPath){try{$studio=$StudioPath;Inspect @('themes','translation','connection-order') $true 'Inspeção somente leitura da instalação real'}finally{$studio=$syntheticStudio}}
if($PortStudioPath){try{$studio=$PortStudioPath;Inspect @('themes','translation','connection-order') $true 'Inspeção somente leitura do porte real e seus próprios módulos'}finally{$studio=$syntheticStudio}}
$themeJar=Get-ChildItem -LiteralPath (Join-Path $payload 'modules/themes/pacote/plugins') -Filter '*.jar'|Select-Object -First 1
[IO.File]::AppendAllText($themeJar.FullName,'corruption-fixture')
Inspect @('themes') $false 'JAR corrompido bloqueado antes da cópia'
Write-Output 'PASS: executável x64, extração, formulário e planos sintéticos. Nenhuma instalação/abertura real foi executada.'
