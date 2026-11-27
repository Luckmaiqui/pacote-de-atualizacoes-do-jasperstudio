#Requires -Version 7.2
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME,[string]$StudioPath)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Push-Location $root
try{
    & node scripts/Verify.mjs
    if($LASTEXITCODE -ne 0){throw 'Verificação de artefatos falhou.'}
    $classes=Join-Path $root ('build/module-regression-'+[guid]::NewGuid())
    New-Item -ItemType Directory -Path $classes|Out-Null
    $java=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{(Get-Command java.exe -ErrorAction Stop).Source}
    $sources=@('modules/connection-order/src/com/salutem/order/NumericOrder.java','modules/theme/src/com/salutem/theme/ThemePalette.java','tests/ModuleRegressionTest.java','tests/NumericOrderTest.java')
    $argsList=@('-encoding','UTF-8','-d',$classes)+$sources
    $javac=if($JavaHome){Join-Path $JavaHome 'bin/javac.exe'}else{(Get-Command javac.exe -ErrorAction SilentlyContinue).Source}
    if($javac -and (Test-Path -LiteralPath $javac)){& $javac @argsList}else{
        if(!$StudioPath){throw 'Informe um JDK 17 ou -StudioPath para reutilizar o compilador ECJ.'}
        $ecj=Get-ChildItem -LiteralPath (Join-Path $StudioPath 'plugins') -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1
        if(!$ecj){throw 'Compilador ECJ ausente.'}
        & $java '-Dfile.encoding=UTF-8' -jar $ecj.FullName -17 @argsList
    }
    if($LASTEXITCODE -ne 0){throw 'Compilação dos testes falhou.'}
    Copy-Item -LiteralPath 'modules/theme/resources/palettes.properties' -Destination $classes
    foreach($name in @('ModuleRegressionTest','NumericOrderTest')){
        & $java -cp $classes $name $root
        if($LASTEXITCODE -ne 0){throw "Regressão: $name"}
    }
    & ./tests/CheckLauncher.ps1
    & ./tests/Test-RegistryCache.ps1
    & ./tests/Test-InstallRecovery.ps1
    & node tests/gestao-export.test.mjs
    if($LASTEXITCODE -ne 0){throw 'Regressão do coletor.'}
    & node --test tests/compatibility.test.mjs
    if($LASTEXITCODE -ne 0){throw 'Regressão de compatibilidade.'}
    Write-Output 'PASS: regressões automáticas. Conferência visual do Studio não incluída.'
}finally{Pop-Location}
