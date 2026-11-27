#Requires -Version 7.2
param([Parameter(Mandatory)][string]$PayloadDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent;$stage=Join-Path $root ('build/experimental-tests-'+[guid]::NewGuid());New-Item -ItemType Directory $stage|Out-Null
$script:cases=New-Object 'System.Collections.Generic.List[object]'
function Bundle($Studio,$Id,$Version){$dir=Join-Path $Studio ('plugins/'+$Id+'_'+$Version+'/META-INF');New-Item -ItemType Directory $dir -Force|Out-Null;[IO.File]::WriteAllText((Join-Path $dir 'MANIFEST.MF'),"Manifest-Version: 1.0`r`nBundle-SymbolicName: $Id`r`nBundle-Version: $Version`r`n`r`n")}
function Fixture($Version,$Java,$Arch='amd64'){
 $studio=Join-Path $stage ($Version+'-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory (Join-Path $studio 'vm/bin') -Force|Out-Null
 [IO.File]::WriteAllText((Join-Path $studio 'Jaspersoft Studio.exe'),'fixture');[IO.File]::WriteAllText((Join-Path $studio 'vm/bin/java.exe'),'fixture')
 [IO.File]::WriteAllText((Join-Path $studio 'vm/release'),"JAVA_VERSION=`"$Java`"`r`nOS_ARCH=`"$Arch`"")
 [IO.File]::WriteAllText((Join-Path $studio 'Jaspersoft Studio.ini'),"-vm`r`nvm/bin/java.exe`r`n-vmargs`r`n-Xmx1024m")
 foreach($id in @('com.jaspersoft.studio','com.jaspersoft.studio.data','com.jaspersoft.studio.server')){Bundle $studio $id $Version}
 Bundle $studio 'org.eclipse.swt' '3.999.0.fixture';Bundle $studio 'org.eclipse.swt.win32.win32.x86_64' '3.999.0.fixture';return $studio
}
function Inspect($Studio,$Experimental,$Success,$Case,$Modules=@('themes','translation','connection-order')){
 $state=Join-Path $stage ([guid]::NewGuid());New-Item -ItemType Directory $state|Out-Null
 @{action='Inspect';studio=$Studio;components=$Modules;experimental=$Experimental}|ConvertTo-Json|Set-Content (Join-Path $state 'request.json') -Encoding utf8
 & "$env:WINDIR/System32/WindowsPowerShell/v1.0/powershell.exe" -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PayloadDirectory 'Unified.ps1') -RequestFile (Join-Path $state 'request.json')
 $result=Get-Content (Join-Path $state 'result.json') -Raw|ConvertFrom-Json
 if($result.ok -ne $Success -or (($LASTEXITCODE -eq 0) -ne $Success)){throw ($Case+': '+$result.message)}
 if(Test-Path (Join-Path $Studio 'dropins')){throw 'Inspeção alterou a instalação.'}
 if($Success -and ($result.compatibility -ne 'not-certified' -or $result.experimental -ne $true)){throw 'Modo experimental foi apresentado como certificado.'}
 $script:cases.Add(@{case=$Case;expectedSuccess=$Success;passed=$true});Write-Host ('PASS: '+$Case);return $result
}
$legacy=Fixture '6.16.0.final' '11.0.8'
$a=Inspect $legacy $true $true '6.16 / Java 11: plano experimental completo';$b=Inspect $legacy $true $true 'Plano repetido determinístico'
$ia=Get-Content (Join-Path $a.transaction 'pacote/arquivos.json') -Raw|ConvertFrom-Json;$ib=Get-Content (Join-Path $b.transaction 'pacote/arquivos.json') -Raw|ConvertFrom-Json
if(($ia|ConvertTo-Json -Compress) -ne ($ib|ConvertTo-Json -Compress)){throw 'JARs experimentais não são determinísticos; Abrir falharia após Instalar.'}
. (Join-Path $PayloadDirectory 'Experimental.ps1')
$order=@($ia|Where-Object name -like 'com.salutem.jasper.connectionorder_*')[0];$mf=Read-ExperimentManifest (Join-Path $a.transaction ('pacote/plugins/'+$order.name))
if($mf['Require-Bundle'] -notlike '*[[]6.16.0.final,6.16.0.final[]]*'){throw 'Dependência do Studio não adaptada.'}
if($mf['Bundle-RequiredExecutionEnvironment'] -ne 'JavaSE-1.8'){throw 'A adaptação mudou o requisito de Java.'}
[void](Inspect $legacy $false $false 'Modo normal continua bloqueando perfil desconhecido')
[void](Inspect (Fixture '6.6.0.final' '1.8.0_171') $true $true 'Porte 6.6 aceito como experimental')
[void](Inspect (Fixture '6.21.5.final' '17.0.13') $true $true 'Porte 6.21.5 / outro patch Java aceito como experimental')
[void](Inspect (Fixture '7.0.0.final' '17.0.13') $true $true 'Outra versão numérica pode ser tentada, sem certificação')
[void](Inspect (Fixture '6.16.0.final' '1.7.0_80') $true $false 'Java abaixo do requisito continua bloqueado')
$references=Get-Content (Join-Path $PayloadDirectory 'experimental-profiles.json') -Raw|ConvertFrom-Json
if(@($references|Where-Object studio -eq '6.16.0.final').Count){[void](Inspect (Fixture '6.16.0.final' '1.8.0_402') $true $false 'Porte 6.16 exige o Java 11 oferecido e testado')}
[void](Inspect (Fixture '6.16.0.final' '1.8.0_402' 'x86') $true $false 'Arquitetura x86 continua bloqueada')
[void](Inspect $legacy $true $false 'Módulo desconhecido continua bloqueado' @('invalid'))
$testJar=Join-Path $stage 'bad.jar';[IO.File]::WriteAllText($testJar,'corrupt')
$failed=$false;try{New-ExperimentJar $testJar $stage @{} '6.16.0.final'}catch{$failed=$true};if(!$failed){throw 'JAR inválido aceito.'}
Write-Output 'PASS: planos experimentais sintéticos, determinismo, metadados e proteções. Não comprova funcionamento dos plug-ins nessas versões; nenhuma instalação foi executada.'
@{schema=1;passed=$true;scope='Synthetic installation plans and metadata only; no Studio installation or plugin runtime certification';cases=$script:cases.ToArray();payload=$PayloadDirectory}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $stage 'results.json') -Encoding utf8
Write-Output ('Evidência: '+(Join-Path $stage 'results.json'))
