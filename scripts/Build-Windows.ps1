#Requires -Version 7.2
[CmdletBinding()]
param([string]$OutputDirectory,[string[]]$PortBuilds,[string[]]$ExperimentalPortBuilds)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$versions=Get-Content -LiteralPath (Join-Path $root 'versions.json') -Raw|ConvertFrom-Json
& node (Join-Path $PSScriptRoot 'Verify.mjs')
if($LASTEXITCODE -ne 0){throw 'Artefatos não verificados; executável não gerado.'}
$compiler=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
if(!(Test-Path -LiteralPath $compiler)){throw 'Compilador do .NET Framework 4.x não encontrado.'}
$stage=Join-Path $root ('build/windows-'+[guid]::NewGuid())
$payload=Join-Path $stage 'payload'
New-Item -ItemType Directory -Path $payload,(Join-Path $payload 'common') -Force|Out-Null
$utf8=[Text.UTF8Encoding]::new($true)
foreach($name in @('Instalar.ps1','RegistryCache.ps1','PrivateRuntime.ps1')){Copy-Item -LiteralPath (Join-Path $root ('installer/common/'+$name)) -Destination (Join-Path $payload 'common')}
Copy-Item -LiteralPath (Join-Path $root 'installer/windows/Unified.ps1') -Destination $payload
Copy-Item -LiteralPath (Join-Path $root 'installer/windows/Experimental.ps1') -Destination $payload
Copy-Item -LiteralPath (Join-Path $root 'compatibility/studio-profiles.json') -Destination $payload
$snapshot=Get-Content -LiteralPath (Join-Path $root 'artifacts/index.json') -Raw|ConvertFrom-Json
foreach($id in @('themes','translation','connection-order')){
    $destination=Join-Path $payload ('modules/'+$id)
    New-Item -ItemType Directory -Path (Join-Path $destination 'pacote/plugins') -Force|Out-Null
    $files=@($snapshot.files|Where-Object{
        if($id -eq 'themes'){$_.path -eq ('artifacts/plugins/com.salutem.jasper.theme.deepblue_'+$versions.theme+'.jar')}
        elseif($id -eq 'translation'){$_.path -match '^vendor/' -or $_.path -eq ('artifacts/plugins/com.salutem.jasper.language.pt_BR_'+$versions.language+'.jar') -or ($_.path -match '^artifacts/plugins/com\.salutem\.l10n\.' -and $_.path.EndsWith('_'+$versions.translationFragments+'.jar'))}
        else{$_.path -eq ('artifacts/plugins/com.salutem.jasper.connectionorder_'+$versions.connectionOrder+'.jar')}
    })
    if(!$files.Count){throw "Componente vazio: $id"}
    $inventory=@(foreach($file in $files){
        Copy-Item -LiteralPath (Join-Path $root $file.path) -Destination (Join-Path $destination 'pacote/plugins')
        @{name=[IO.Path]::GetFileName($file.path);sha256=$file.sha256}
    })
    [IO.File]::WriteAllText((Join-Path $destination 'pacote/arquivos.json'),(ConvertTo-Json -InputObject $inventory -Depth 3),$utf8)
}
Copy-Item -LiteralPath (Join-Path $root 'installer/themes/padroes.ini') -Destination (Join-Path $payload 'modules/themes')
$profileSet=Get-Content -LiteralPath (Join-Path $payload 'studio-profiles.json') -Raw|ConvertFrom-Json
. (Join-Path $root 'ports/studio6/Package-VerifiedPort.ps1')
$included=@()
foreach($file in @($PortBuilds)){
    if(!$file){continue}
    $build=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    $profile=@($profileSet.profiles|Where-Object studio -eq ($build.studio+'.final'))
    if($profile.Count -ne 1 -or $build.studio -in $included){throw 'Perfil de porte ausente ou repetido.'}
    Copy-VerifiedStudioPort -BuildFile $file -Payload $payload -Repository $root -Profile $profile[0]|Out-Null
    $included+=$build.studio
}
foreach($profile in $profileSet.profiles){if($profile.studio -ne '6.20.6.final' -and $profile.studio.Replace('.final','') -notin $included -and $profile.uiStatus -eq 'validated-port'){$profile.uiStatus='not-included';$profile.reason='Os binários desta versão não foram incluídos neste executável.'}}
[IO.File]::WriteAllText((Join-Path $payload 'studio-profiles.json'),($profileSet|ConvertTo-Json -Depth 6),$utf8)
$experimental=@(@{studio='6.20.6.final';path='.';minimumJava=17;status='experimental-reference'})
$runtimePackage=Join-Path (Split-Path $root -Parent) 'JS-6.16.0-Teste/correcao-6160-1.9.3'
if(@($ExperimentalPortBuilds|Where-Object {$_ -and (Get-Content $_ -Raw|ConvertFrom-Json).studio -eq '6.16.0'}).Count){
 $runtimeFiles=@(Get-Content (Join-Path $runtimePackage 'security-files.json') -Raw|ConvertFrom-Json|Where-Object {$_.path.StartsWith('runtime/')})
 if(!$runtimeFiles.Count){throw 'Runtime privado 6.16.0 ausente.'}
 $runtimeIndex=@();foreach($entry in $runtimeFiles){$source=Join-Path $runtimePackage $entry.path;if((Get-FileHash $source).Hash -ne $entry.sha256){throw 'Java privado divergente.'};$target=Join-Path $payload ('privateJava/'+$entry.path);New-Item -ItemType Directory -Force (Split-Path $target -Parent)|Out-Null;Copy-Item $source $target;$runtimeIndex+=@{path=$entry.path.Substring(8);sha256=$entry.sha256}}
 $runtimeIndex|ConvertTo-Json -Depth 4|Set-Content (Join-Path $payload 'privateJava/files.json') -Encoding utf8BOM
}
. (Join-Path $root 'ports/studio6/Package-ExperimentalPort.ps1')
foreach($file in @($ExperimentalPortBuilds)){if($file){$experimental+=Copy-ExperimentalStudioPort -BuildFile $file -Payload $payload -Repository $root}}
[IO.File]::WriteAllText((Join-Path $payload 'experimental-profiles.json'),(ConvertTo-Json -InputObject @($experimental) -Depth 4),$utf8)
Copy-Item -LiteralPath (Join-Path $root 'THIRD_PARTY_NOTICES.md') -Destination $payload
New-Item -ItemType Directory -Path (Join-Path $payload 'docs')|Out-Null
foreach($name in @('INSTALADOR-WINDOWS.md','COMPATIBILIDADE-JASPER-JAVA.md','ARQUITETURA-COMPATIBILIDADE.md','PORTES-STUDIO-6.md')){Copy-Item -LiteralPath (Join-Path $root ('docs/'+$name)) -Destination (Join-Path $payload 'docs')}
# Windows PowerShell 5.1 requires a BOM for non-ASCII source files.
Get-ChildItem -LiteralPath $payload -Recurse -File -Filter '*.ps1'|ForEach-Object{[IO.File]::WriteAllText($_.FullName,[IO.File]::ReadAllText($_.FullName),$utf8)}
$hashes=[ordered]@{}
Get-ChildItem -LiteralPath $payload -Recurse -File|Sort-Object FullName|ForEach-Object{$relative=[IO.Path]::GetRelativePath($payload,$_.FullName).Replace('\','/');$hashes[$relative]=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText((Join-Path $payload 'payload-files.json'),($hashes|ConvertTo-Json -Depth 3),$utf8)
$archive=Join-Path $stage 'payload.zip'
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($payload,$archive)
$hashFile=Join-Path $stage 'payload.sha256'
[IO.File]::WriteAllText($hashFile,(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant())
if(!$OutputDirectory){$OutputDirectory=Join-Path $root ('dist/JS-Instalador-local-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Diretório de saída existente. Escolha um novo para preservar o pacote anterior.'}
New-Item -ItemType Directory -Path $OutputDirectory|Out-Null
$exe=Join-Path $OutputDirectory 'JasperStudio.exe'
$icon=Join-Path $root 'installer/windows/assets/jaspersoftstudio.ico'
$iconOrigin=Get-Content -LiteralPath (Join-Path $root 'installer/windows/assets/provenance.json') -Raw|ConvertFrom-Json
if((Get-FileHash -LiteralPath $icon -Algorithm SHA256).Hash -ne $iconOrigin.iconSha256){throw 'O ícone difere do recurso original conferido.'}
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /codepage:65001 ('/out:'+$exe) ('/win32icon:'+$icon) ('/win32manifest:'+(Join-Path $root 'installer/windows/app.manifest')) /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Web.Extensions.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll ('/resource:'+$icon+',JasperIcon') ('/resource:'+$archive+',Payload') ('/resource:'+$hashFile+',PayloadHash') (Join-Path $root 'installer/windows/Program.cs')
if($LASTEXITCODE -ne 0){throw 'Falha ao compilar o executável Windows.'}
Get-ChildItem -LiteralPath $OutputDirectory -File|ForEach-Object{(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$_.Name}|Set-Content -LiteralPath (Join-Path $OutputDirectory 'SHA256SUMS.txt') -Encoding ascii
[IO.File]::WriteAllText((Join-Path $root 'build/latest-windows-package.txt'),[IO.Path]::GetFullPath($OutputDirectory),$utf8)
$zipFile=[IO.Path]::GetFullPath($OutputDirectory)+'.zip'
if(Test-Path -LiteralPath $zipFile){throw 'ZIP de saída já existe; nenhum pacote anterior foi substituído.'}
$releaseZip=[IO.Compression.ZipFile]::Open($zipFile,[IO.Compression.ZipArchiveMode]::Create)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($releaseZip,$exe,'JasperStudio.exe')|Out-Null}finally{$releaseZip.Dispose()}
((Get-FileHash -LiteralPath $zipFile -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+[IO.Path]::GetFileName($zipFile))|Set-Content -LiteralPath ($zipFile+'.sha256') -Encoding ascii
Write-Output "Pacote local criado: $OutputDirectory"
