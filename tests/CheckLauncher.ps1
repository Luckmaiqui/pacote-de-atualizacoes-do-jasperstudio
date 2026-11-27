#Requires -Version 5.1
param([string]$StudioPath,[string]$PackageRoot)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$installer=Join-Path (Split-Path $PSScriptRoot -Parent) 'installer/common/Instalar.ps1'
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($installer,[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
# The extracted functions share the installer's default profile variables.
# Evaluate only their top-level literal defaults, never the installation body.
foreach($name in @('expectedStudioVersion','expectedJavaMajor')){
 $defaults=@($ast.EndBlock.Statements|Where-Object{$_ -is [Management.Automation.Language.AssignmentStatementAst] -and $_.Left.VariablePath.UserPath -eq $name})
 if($defaults.Count -ne 1){throw ('Default profile assignment missing: '+$name)}
 Invoke-Expression $defaults[0].Extent.Text
}
foreach($definition in $ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false)){
 if($definition.Name -in @('Read-Manifest','Get-JavaMajor','Get-Plan','Get-LaunchPlan')){Invoke-Expression $definition.Extent.Text}
}
$tempRoot=(Resolve-Path -LiteralPath $env:TEMP).Path
$fixture=Join-Path $tempRoot ('js-launch-test-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path $fixture|Out-Null
try{
 $plugins=Join-Path $fixture 'plugins';$dropins=Join-Path $fixture 'dropins/eclipse/plugins';$state=Join-Path $fixture 'Temas-Ação';$vm=Join-Path $fixture 'vm/bin'
 New-Item -ItemType Directory -Path $plugins,$dropins,$state,$vm -Force|Out-Null
 [IO.File]::WriteAllText((Join-Path $fixture 'Jaspersoft Studio.exe'),'fixture')
 [IO.File]::WriteAllText((Join-Path $vm 'java.exe'),'fixture')
 [IO.File]::WriteAllText((Join-Path $fixture 'vm/release'),'JAVA_VERSION="17.0.8"')
 [IO.File]::WriteAllText((Join-Path $fixture 'Jaspersoft Studio.ini'),"-vm`r`nvm/bin/java.exe`r`n-vmargs`r`n-Xmx2048m")
 $jar=[IO.Compression.ZipFile]::Open((Join-Path $plugins 'com.jaspersoft.studio_6.20.6.final.jar'),'Create')
 try{$entry=$jar.CreateEntry('META-INF/MANIFEST.MF');$writer=New-Object IO.StreamWriter($entry.Open());$writer.Write("Bundle-SymbolicName: com.jaspersoft.studio`r`nBundle-Version: 6.20.6.final`r`n");$writer.Dispose()}finally{$jar.Dispose()}
 $first=Get-LaunchPlan $fixture $state 'translation'
 if(-not $first.NeedsClean -or ($first.Arguments -join ' ') -ne '-clean -nl pt_BR'){throw 'Primeira abertura/idioma incorretos.'}
 @{signature=$first.Signature}|ConvertTo-Json|Set-Content -LiteralPath $first.StateFile -Encoding UTF8
 $next=Get-LaunchPlan $fixture $state 'translation'
 if($next.NeedsClean -or ($next.Arguments -join ' ') -ne '-nl pt_BR'){throw 'Limpeza repetida sem atualização.'}
 $theme=Get-LaunchPlan $fixture $state 'themes'
 if($theme.NeedsClean -or $theme.Arguments[0] -ne '-pluginCustomization' -or $theme.Arguments[1] -notlike '*Temas-Ação*'){throw 'Tema/caminho com espaços e acentos.'}
 $adapter=Get-LaunchPlan $fixture $state 'connection-order'
 if($adapter.Arguments.Count -ne 0){throw 'Ordenação alterou idioma/tema ou limpou cache.'}
 $changed=Join-Path $dropins 'com.salutem.fixture_1.0.0.jar';[IO.File]::WriteAllText($changed,'v1')
 $updated=Get-LaunchPlan $fixture $state 'themes';if(-not $updated.NeedsClean){throw 'Complemento novo não invalidou cache.'}
 @{signature=$updated.Signature}|ConvertTo-Json|Set-Content -LiteralPath $updated.StateFile -Encoding UTF8
 [IO.File]::WriteAllText($changed,'v2-changed');if(-not (Get-LaunchPlan $fixture $state 'themes').NeedsClean){throw 'Arquivo alterado não invalidou cache.'}
 [IO.File]::WriteAllText((Join-Path $fixture 'vm/release'),'JAVA_VERSION="11.0.0"');$rejected=$false
 try{Get-LaunchPlan $fixture $state 'themes'|Out-Null}catch{$rejected=$true};if(-not $rejected){throw 'Java incompatível aceito.'}
 Write-Output 'PASS: primeira abertura limpa; próximas reutilizam cache; mudanças invalidam; idioma e tema separados; acentos preservados; Java incompatível rejeitado.'
 if($StudioPath -and $PackageRoot){
  Get-LaunchPlan $StudioPath $state 'translation'|Out-Null;Get-Plan $StudioPath $PackageRoot|Out-Null
  $slow=Measure-Command{1..3|ForEach-Object{Get-Plan $StudioPath $PackageRoot|Out-Null}}
  $fast=Measure-Command{1..3|ForEach-Object{Get-LaunchPlan $StudioPath $state 'translation'|Out-Null}}
  [pscustomobject]@{ValidationAverageMs=[math]::Round($slow.TotalMilliseconds/3,1);LaunchPreparationAverageMs=[math]::Round($fast.TotalMilliseconds/3,1)}
 }
}finally{
 $resolved=(Resolve-Path -LiteralPath $fixture).Path
 if((Split-Path $resolved -Parent) -eq $tempRoot -and (Split-Path $resolved -Leaf).StartsWith('js-launch-test-')){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
