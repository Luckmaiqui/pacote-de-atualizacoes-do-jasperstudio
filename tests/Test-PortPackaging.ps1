#Requires -Version 7.2
# Entirely synthetic approval records: never promotes a real Studio profile.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $root 'ports/studio6/Package-VerifiedPort.ps1')
$stage=Join-Path $root ('build/port-packaging-'+[guid]::NewGuid())
$repo=Join-Path $stage 'repository'
New-Item -ItemType Directory -Force (Join-Path $repo 'ports/studio6/results'),(Join-Path $repo 'installer/themes'),(Join-Path $stage 'inputs')|Out-Null
[IO.File]::WriteAllText((Join-Path $repo 'installer/themes/padroes.ini'),'synthetic-defaults')
[IO.File]::WriteAllText((Join-Path $repo 'ports/studio6/toolchain.json'),'{}')
Copy-Item (Join-Path $root 'ports/studio6/Redistribution.ps1') (Join-Path $repo 'ports/studio6')
foreach($name in @('Backport.ps1','Relink-Module.ps1','PortTranslations.java','README-SOURCES.txt')){[IO.File]::WriteAllText((Join-Path $repo ('ports/studio6/'+$name)),'synthetic-build-tool')}
$cache=Join-Path $repo '.compatibility/studio-tools/redistribution'
New-Item -ItemType Directory -Force $cache,(Join-Path $stage 'sources'),(Join-Path $stage 'translation-fragments')|Out-Null
[IO.File]::WriteAllText((Join-Path $stage 'sources/Fixture.java'),'synthetic-source')
foreach($id in @('theme','language','connection-order')){[IO.File]::WriteAllText((Join-Path $stage ($id+'-raw.jar')),'synthetic-object')}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$upstream=Join-Path $cache 'jvmdowngrader-2.0.1-source.zip'
$zip=[IO.Compression.ZipFile]::Open($upstream,'Create')
try{foreach($name in @('JvmDowngrader-2.0.1/LICENSE.md','JvmDowngrader-2.0.1/license/LGPLv2.1.md')){$writer=[IO.StreamWriter]::new($zip.CreateEntry($name).Open());$writer.Write('SYNTHETIC TEST LICENSE');$writer.Dispose()}}finally{$zip.Dispose()}
@{files=@(@{name=[IO.Path]::GetFileName($upstream);sha256=(Get-FileHash $upstream -Algorithm SHA256).Hash})}|ConvertTo-Json -Depth 4|Set-Content (Join-Path $repo 'ports/studio6/redistribution.json')
$plugins=@(foreach($module in @('theme','language','translation-fragment','connection-order')){
    $file=Join-Path $stage ('inputs/com.salutem.fixture.'+$module+'_1.0.port6215.jar')
    [IO.File]::WriteAllText($file,('synthetic-'+$module))
    @{path=$file;module=$module;sha256=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()}
})
$build=@{studio='6.21.5';platformKind='windows-installation';plugins=$plugins;stage=$stage}
$tests=@('CheckAccounts','CheckNativeSelection','CheckReview270','CheckPublishing','CheckThemeSwitch','CheckRepositoryOrder','CheckExplorerSearch','CheckDisabledRendering','GlobalSearchEngineTest','NumericOrderTest','SubreportPathsTest','SubreportInputsTest','RemoteSubreportMatcherTest','GestaoCatalogTest','CheckCustomColors','CheckSaveProgress','PortResourceBindings')
$proof=@{studio='6.21.5';platform='windows-x64';tests=$tests;artifacts=@($plugins|ForEach-Object{@{name=[IO.Path]::GetFileName($_.path);sha256=$_.sha256}})}
$actual=@($plugins|Sort-Object{[IO.Path]::GetFileName($_.path)}|ForEach-Object{[IO.Path]::GetFileName($_.path)+'|'+$_.sha256}) -join "`n"
$setHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($actual))).ToLowerInvariant()
$visual=@{observations=@(@{studio='6.21.5';basicWorkbenchSmoke='passed';blockingFindings=@();artifactSetSha256=$setHash;observedChecks=@{themeApplied=$true;reportOpened=$true;paletteVisible=$true;menusTranslated=$true}})}
$profile=[pscustomobject]@{studio='6.21.5.final';uiStatus='validated-port'}
$file=Join-Path $stage 'build.json'
function Check([string]$Case,[bool]$Expected){
    $build|ConvertTo-Json -Depth 8|Set-Content $file
    $proof|ConvertTo-Json -Depth 8|Set-Content (Join-Path $repo 'ports/studio6/results/studio-6.21.5.json')
    $visual|ConvertTo-Json -Depth 8|Set-Content (Join-Path $repo 'ports/studio6/results/ui-initialization.json')
    $payload=Join-Path $stage ('output-'+[guid]::NewGuid())
    $ok=$true
    try{$location=Copy-VerifiedStudioPort -BuildFile $file -Payload $payload -Repository $repo -Profile $profile}catch{$ok=$false}
    if($ok -ne $Expected){throw ('Unexpected packaging outcome: '+$Case)}
    if($ok){
        if(@(Get-ChildItem (Join-Path $location 'modules') -Recurse -Filter '*.jar').Count -ne 4){throw 'Incorrect final inventory'}
        foreach($id in @('themes','translation','connection-order')){if(!(Test-Path (Join-Path $location ('modules/'+$id+'/pacote/arquivos.json')))){throw 'Module inventory missing'}}
        if(!(Test-Path (Join-Path $location 'evidence/studio-6.21.5.json'))){throw 'Evidence missing'}
        if(!(Test-Path (Join-Path $location 'sources/source-files.json'))){throw 'Corresponding source inventory missing'}
    }elseif(Test-Path (Join-Path $payload 'profiles')){throw ('Rejected input created a partial package: '+$Case)}
    Write-Output ('PASS: '+$Case)
}
Check 'Synthetic verified set creates separate modules and evidence' $true
$profile.uiStatus='ported-candidate';Check 'Candidate cannot be packaged' $false;$profile.uiStatus='validated-port'
$visual.observations[0].blockingFindings=@('low contrast');Check 'Pending visual issue blocks packaging' $false;$visual.observations[0].blockingFindings=@()
$visual.observations[0].artifactSetSha256='wrong';Check 'Visual evidence from another build blocked' $false;$visual.observations[0].artifactSetSha256=$setHash
$proof.tests=@($tests|Where-Object{$_ -ne 'CheckPublishing'});Check 'Missing required test blocked' $false;$proof.tests=$tests
$proof.studio='6.6.0';Check 'Evidence for another Studio blocked' $false;$proof.studio='6.21.5'
[IO.File]::AppendAllText($plugins[0].path,'corruption');Check 'Modified artifact blocked before copying' $false
Write-Output 'PASS: synthetic packaging gates; no real profile approved or installed.'
