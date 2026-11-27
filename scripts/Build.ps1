#Requires -Version 7.0
[CmdletBinding()]
param([string]$StudioPath,[switch]$PackageOnly,[switch]$LegacyPackages)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$versions=Get-Content -LiteralPath (Join-Path $root 'versions.json') -Raw|ConvertFrom-Json
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Same-JarContent([string]$Left,[string]$Right){
    $a=[IO.Compression.ZipFile]::OpenRead($Left);$b=[IO.Compression.ZipFile]::OpenRead($Right)
    try{
        $filesA=@($a.Entries|Where-Object{-not $_.FullName.EndsWith('/')});$filesB=@($b.Entries|Where-Object{-not $_.FullName.EndsWith('/')})
        if($filesA.Count -ne $filesB.Count){return $false}
        foreach($entry in $filesA){
            $other=$b.GetEntry($entry.FullName);if(-not $other){return $false}
            $hashes=@()
            foreach($current in @($entry,$other)){
                $stream=$current.Open()
                try{
                    $sha=[Security.Cryptography.SHA256]::Create()
                    try{
                        if($current.FullName -match '\.(properties|xml|css|MF)$'){
                            $reader=New-Object IO.StreamReader($stream)
                            try{$bytes=[Text.Encoding]::UTF8.GetBytes(($reader.ReadToEnd() -replace "`r`n","`n"))}finally{$reader.Dispose()}
                            $hashes+=[Convert]::ToHexString($sha.ComputeHash($bytes))
                        }else{$hashes+=[Convert]::ToHexString($sha.ComputeHash($stream))}
                    }finally{$sha.Dispose()}
                }finally{$stream.Dispose()}
            }
            if($hashes[0] -ne $hashes[1]){return $false}
        }
        return $true
    }finally{$a.Dispose();$b.Dispose()}
}
function Zip-Folder([string]$Folder,[string]$Destination){
    $temp=Join-Path (Join-Path $root 'build') ([guid]::NewGuid().ToString()+'.zip')
    [IO.Compression.ZipFile]::CreateFromDirectory($Folder,$temp)
    if($Destination.EndsWith('.jar') -and (Test-Path -LiteralPath $Destination)){
        if(Same-JarContent $temp $Destination){return}
        throw "O JAR $Destination mudou sem aumento de versão. Aumente a versão do componente em versions.json."
    }
    Copy-Item -LiteralPath $temp -Destination $Destination -Force
}
if(-not $PackageOnly){
    New-Item -ItemType Directory -Force -Path (Join-Path $root 'artifacts/plugins')|Out-Null
    & node (Join-Path $PSScriptRoot 'GenerateThemes.mjs')
    if($LASTEXITCODE -ne 0){throw 'Geração dos temas falhou.'}
    if(-not $StudioPath){$StudioPath=$env:JSS_HOME}
    if(-not $StudioPath){throw 'Informe -StudioPath ou a variável JSS_HOME apontando para o Studio 6.20.6.'}
    $StudioPath=(Resolve-Path -LiteralPath $StudioPath).Path
    $plugins=Join-Path $StudioPath 'plugins'
    if(-not(Test-Path -LiteralPath (Join-Path $plugins 'com.jaspersoft.studio_6.20.6.final.jar'))){throw 'É necessária uma instalação do Studio 6.20.6.'}
    $javaCandidates=@()
    if($env:JAVA_HOME){$javaCandidates+=Join-Path $env:JAVA_HOME 'bin/java.exe'}
    if(Test-Path -LiteralPath (Join-Path $StudioPath 'features')){$javaCandidates+=@(Get-ChildItem -LiteralPath (Join-Path $StudioPath 'features') -Recurse -File -Filter java.exe|ForEach-Object FullName)}
    $java=@($javaCandidates|Where-Object{Test-Path -LiteralPath $_})|Select-Object -First 1
    if(-not $java){$command=Get-Command java -ErrorAction SilentlyContinue;if($command){$java=$command.Source}}
    if(-not $java){throw 'Java 17 ou superior não encontrado.'}
    $compiler=Get-ChildItem -LiteralPath $plugins -File -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar'|Select-Object -First 1
    if(-not $compiler){throw 'Compilador ECJ não encontrado nos plugins do Studio.'}
    $classpath=(Get-ChildItem -LiteralPath $plugins -File -Filter '*.jar'|ForEach-Object{$_.FullName.Replace('\','/')}) -join [IO.Path]::PathSeparator
    # AFinderUI exposes DTO types from the server bundle's nested class path.
    $serverJar=Join-Path $plugins 'com.jaspersoft.studio.server_6.20.6.final.jar'
    $serverZip=[IO.Compression.ZipFile]::OpenRead($serverJar)
    try{
        $dto=$serverZip.Entries|Where-Object{$_.FullName -match '^lib/jasperserver-dto-.*\.jar$'}|Select-Object -First 1
        if(-not $dto){throw 'DTO JasperServer não encontrado.'}
        $dtoPath=Join-Path $root 'build/jasperserver-dto.jar'
        [IO.Compression.ZipFileExtensions]::ExtractToFile($dto,$dtoPath,$true)
        $classpath+=[IO.Path]::PathSeparator+$dtoPath.Replace('\','/')
    }finally{$serverZip.Dispose()}
    # The engine is nested inside its OSGi bundle, not on ECJ's flat JAR class path.
    $engineZip=[IO.Compression.ZipFile]::OpenRead((Join-Path $plugins 'net.sf.jasperreports_6.20.6.final.jar'))
    try{
        $engine=$engineZip.GetEntry('lib/jasperreports-6.20.6.jar')
        if(-not $engine){throw 'Engine JasperReports 6.20.6 não encontrado.'}
        $enginePath=Join-Path $root 'build/jasperreports-6.20.6.jar'
        [IO.Compression.ZipFileExtensions]::ExtractToFile($engine,$enginePath,$true)
        $classpath+=[IO.Path]::PathSeparator+$enginePath.Replace('\','/')
    }finally{$engineZip.Dispose()}
    $specs=@(@{Name='theme';Version=$versions.theme;Id='com.salutem.jasper.theme.deepblue'},@{Name='language';Version=$versions.language;Id='com.salutem.jasper.language.pt_BR'},@{Name='connection-order';Version=$versions.connectionOrder;Id='com.salutem.jasper.connectionorder'})
    foreach($spec in $specs){
        $module=Join-Path $root ('modules/'+$spec.Name)
        $stage=Join-Path $root ('build/compile-'+$spec.Name+'-'+[guid]::NewGuid())
        New-Item -ItemType Directory -Force -Path $stage|Out-Null
        Get-ChildItem -LiteralPath (Join-Path $module 'resources')|ForEach-Object{Copy-Item -LiteralPath $_.FullName -Destination $stage -Recurse}
        $argfile=Join-Path $stage 'compiler.args'
        $arguments=@('-17','-encoding','UTF-8','-classpath',('"'+$classpath+'"'),'-d',('"'+$stage.Replace('\','/')+'"'))
        $arguments+=@(Get-ChildItem -LiteralPath (Join-Path $module 'src') -Recurse -File -Filter '*.java'|ForEach-Object{'"'+$_.FullName.Replace('\','/')+'"'})
        [IO.File]::WriteAllText($argfile,($arguments -join "`n"),(New-Object Text.UTF8Encoding($false)))
        # The argument file is UTF-8; without this the JVM reads it in the Windows ANSI code page and accented paths break.
        & $java '-Dfile.encoding=UTF-8' -jar $compiler.FullName ('@'+$argfile)
        if($LASTEXITCODE -ne 0){throw "Compilação falhou: $($spec.Name)"}
        # Compiler arguments are build metadata, not part of the plug-in.
        $bundle=Join-Path $root ('build/bundle-'+$spec.Name+'-'+[guid]::NewGuid())
        New-Item -ItemType Directory -Force -Path $bundle|Out-Null
        Get-ChildItem -LiteralPath $stage|Where-Object{$_.Name -ne 'compiler.args'}|ForEach-Object{Copy-Item -LiteralPath $_.FullName -Destination $bundle -Recurse}
        $mf=Join-Path $bundle 'META-INF/MANIFEST.MF'
        $text=[IO.File]::ReadAllText($mf) -replace '(?m)^Bundle-Version: [^\r\n]+',('Bundle-Version: '+$spec.Version)
        [IO.File]::WriteAllText($mf,$text,[Text.Encoding]::ASCII)
        Zip-Folder $bundle (Join-Path $root ('artifacts/plugins/'+$spec.Id+'_'+$spec.Version+'.jar'))
    }
    foreach($fragment in Get-ChildItem -LiteralPath (Join-Path $root 'translations/fragments') -Directory){
        $stage=Join-Path $root ('build/fragment-'+[guid]::NewGuid());Copy-Item -LiteralPath $fragment.FullName -Destination $stage -Recurse
        $mf=Join-Path $stage 'META-INF/MANIFEST.MF';$text=[IO.File]::ReadAllText($mf) -replace '(?m)^Bundle-Version: [^\r\n]+',('Bundle-Version: '+$versions.translationFragments);[IO.File]::WriteAllText($mf,$text,[Text.Encoding]::ASCII)
        Zip-Folder $stage (Join-Path $root ('artifacts/plugins/'+$fragment.Name+'_'+$versions.translationFragments+'.jar'))
    }
    & node (Join-Path $PSScriptRoot 'Verify.mjs') --snapshot
    if($LASTEXITCODE -ne 0){throw 'Verificação dos fontes e artefatos falhou.'}
}else{
    & node (Join-Path $PSScriptRoot 'Verify.mjs')
    if($LASTEXITCODE -ne 0){throw 'Artefatos desatualizados. Faça um build completo.'}
}
$snapshot=Get-Content -LiteralPath (Join-Path $root 'artifacts/index.json') -Raw|ConvertFrom-Json
if(!$LegacyPackages){Write-Output 'Artefatos verificados. Distribuição única: scripts/Build-Windows.ps1';return}
$stage=Join-Path $root ('build/package-'+[guid]::NewGuid())
$package=Join-Path $stage 'Pacote de Atualizações do JasperStudio'
New-Item -ItemType Directory -Force -Path $package,(Join-Path $root 'dist')|Out-Null
$utf8=New-Object Text.UTF8Encoding($true)
$archives=@()
foreach($component in @('themes','translation','connection-order')){
    $config=Get-Content -LiteralPath (Join-Path $root "installer/$component/componente.json") -Raw|ConvertFrom-Json
    $destination=Join-Path $package $config.folder
    New-Item -ItemType Directory -Force -Path (Join-Path $destination 'pacote/plugins'),(Join-Path $destination 'fontes')|Out-Null
    Get-ChildItem -LiteralPath (Join-Path $root "installer/$component")|ForEach-Object{Copy-Item -LiteralPath $_.FullName -Destination $destination -Recurse}
    Copy-Item -LiteralPath (Join-Path $root 'installer/common/Instalar.ps1') -Destination $destination
    Copy-Item -LiteralPath (Join-Path $root 'installer/common/RegistryCache.ps1') -Destination $destination
    $entries=@($snapshot.files|Where-Object{
        if($component -eq 'themes'){$_.path -match '^artifacts/plugins/com\.salutem\.jasper\.theme\.'}
        elseif($component -eq 'translation'){$_.path -match '^vendor/|^artifacts/plugins/com\.salutem\.(l10n\.|jasper\.language\.)'}
        else{$_.path -eq ('artifacts/plugins/com.salutem.jasper.connectionorder_'+$versions.connectionOrder+'.jar')}
    })
    if($entries.Count -eq 0){throw "Pacote vazio: $component"}
    $inventory=@()
    foreach($entry in $entries){$source=Join-Path $root $entry.path;Copy-Item -LiteralPath $source -Destination (Join-Path $destination 'pacote/plugins');$inventory+=[pscustomobject]@{name=[IO.Path]::GetFileName($source);sha256=$entry.sha256.ToUpperInvariant()}}
    [IO.File]::WriteAllText((Join-Path $destination 'pacote/arquivos.json'),($inventory|ConvertTo-Json -Depth 3),$utf8)
    $module=if($component -eq 'themes'){'theme'}elseif($component -eq 'translation'){'language'}else{'connection-order'}
    Copy-Item -LiteralPath (Join-Path $root "modules/$module") -Destination (Join-Path $destination 'fontes') -Recurse
    if($component -eq 'translation'){Copy-Item -LiteralPath (Join-Path $root 'translations') -Destination (Join-Path $destination 'fontes') -Recurse}
    Copy-Item -LiteralPath (Join-Path $root 'THIRD_PARTY_NOTICES.md') -Destination $destination
    Copy-Item -LiteralPath (Join-Path $root 'docs/REVISAO-2.7.0.md') -Destination $destination
    if($component -eq 'connection-order'){Copy-Item -LiteralPath (Join-Path $root 'docs/IMPORTACAO-GESTAO.md') -Destination $destination}
    $singleStage=Join-Path $root ('build/single-'+$component+'-'+[guid]::NewGuid())
    New-Item -ItemType Directory -Force -Path $singleStage|Out-Null
    Copy-Item -LiteralPath $destination -Destination $singleStage -Recurse
    $name=if($component -eq 'themes'){'JS-Temas'}elseif($component -eq 'translation'){'JS-Traducao-ptBR'}else{'JS-Ordenacao'}
    $archive=Join-Path $root ('dist/'+$name+'-v'+$versions.packageVersion+'.zip')
    Zip-Folder $singleStage $archive;$archives+=$archive
}
$instructions=@'
ESCOLHA APENAS O QUE DESEJA INSTALAR
Temas: sete opções de cores em Janela > Preferências > Gerais > Aparência.
Traducao-ptBR: interface em Português (Brasil), independente dos temas.
Ordenacao-de-conexoes: ordem numérica dos adaptadores, independente dos demais.
Cada pasta tem seu próprio Instalar.bat. Feche o Studio antes de instalar.
O Abrir JS.bat de Temas não força idioma; o da Tradução inicia em pt_BR.
Compatibilidade: Jaspersoft Studio Community 6.20.6, Windows, Java 17 ou superior.
'@
[IO.File]::WriteAllText((Join-Path $package 'LEIA-ME.txt'),$instructions,$utf8)
$combined=Join-Path $root ('dist/JS-Pacotes-Separados-v'+$versions.packageVersion+'.zip')
Zip-Folder $stage $combined;$archives+=$combined
$lines=@($archives|ForEach-Object{(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+[IO.Path]::GetFileName($_)})
[IO.File]::WriteAllText((Join-Path $root 'dist/SHA256SUMS.txt'),($lines -join "`n")+"`n",[Text.Encoding]::ASCII)
[IO.File]::WriteAllText((Join-Path $root 'build/latest-package.txt'),$package,$utf8)
$archives|ForEach-Object{Write-Output "Pacote criado: $_"}
