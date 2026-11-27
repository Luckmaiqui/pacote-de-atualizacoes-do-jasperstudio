#Requires -Version 7.2
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Results)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$result=Get-Content -LiteralPath $Results -Raw|ConvertFrom-Json
if($result.schema -ne 2 -or @($result.failures).Count){throw 'É necessário um resultado completo, sem falhas, do runner com evidências.'}
$required=@('CheckAccounts','CheckNativeSelection','CheckReview270','CheckPublishing','CheckThemeSwitch','CheckRepositoryOrder','CheckExplorerSearch','CheckDisabledRendering','GlobalSearchEngineTest','NumericOrderTest','SubreportPathsTest','SubreportInputsTest','RemoteSubreportMatcherTest','GestaoCatalogTest','CheckCustomColors','CheckSaveProgress')
$required+='PortResourceBindings'
foreach($test in $required){if($test -notin $result.tests){throw ('Teste obrigatório ausente: '+$test)}}
if(!$result.portBuild){throw 'Este registro é para candidatos de porte, não para a referência original.'}
$port=Get-Content -LiteralPath $result.portBuild -Raw|ConvertFrom-Json
if($port.platformKind -ne 'windows-installation'){throw 'A montagem de APIs macOS/Windows não valida a distribuição Windows.'}
if($port.studio -ne $result.studio){throw 'Plataforma do resultado diverge do build.'}
foreach($entry in $port.plugins){
    if((Get-FileHash -LiteralPath $entry.path -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Binário testado foi alterado.'}
    $tested=@($result.moduleHashes|Where-Object name -eq ([IO.Path]::GetFileName($entry.path)))
    if($tested.Count -ne 1 -or $tested[0].sha256 -ne $entry.sha256){throw 'O relatório não comprova o mesmo conjunto de módulos.'}
}
$coverage=Get-Content -LiteralPath (Join-Path $port.stage 'translation-fragments/coverage.tsv')|ConvertFrom-Csv -Delimiter "`t"
$counts=@{};foreach($field in @('keys','translated','englishForReview')){$counts[$field]=[int](($coverage|Measure-Object -Property $field -Sum).Sum)}
$proof=[ordered]@{
    schema=1;studio=$result.studio;status='components-passed-workbench-pending';testedAt=$result.testedAt
    platform='windows-x64';windowsVersionObservedAtRecording=[Environment]::OSVersion.Version.ToString();osgiContainer=$result.osgiContainer;tests=$result.tests
    scope=$result.scope;manualWorkbench='pending';installed=$false
    runtimeJavaSha256=$result.runtimeJavaSha256;swt=$result.swt;swtSha256=$result.swtSha256
    sourceHashes=$result.sourceHashes;sourceHashesScope='compiled inputs after fixture adaptation';testRunnerSha256=$result.testRunnerSha256
    executionModes=$result.executionModes
    runtimeCompatibilityArguments=$result.runtimeCompatibilityArguments
    artifacts=$result.moduleHashes;translationCoverage=$counts;toolchain=$port.toolchain
    nativeLogs=@(Get-ChildItem -LiteralPath (Split-Path (Resolve-Path $Results).Path -Parent) -File -Filter '*.log'|ForEach-Object{@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
}
$directory=Join-Path $PSScriptRoot 'results';New-Item -ItemType Directory -Force $directory|Out-Null
$path=Join-Path $directory ('studio-'+$result.studio+'.json')
$proof|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $path -Encoding utf8
Write-Output ('Evidência dos componentes registrada: '+$path+'. Revisão visual e fluxos reais continuam pendentes.')
