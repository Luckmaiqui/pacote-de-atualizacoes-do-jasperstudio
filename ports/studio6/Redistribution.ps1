#Requires -Version 7.2
function Copy-PortSources {
    param([object]$Build,[string]$Repository,[string]$Destination)
    $lockFile=Join-Path $Repository 'ports/studio6/redistribution.json'
    $lock=Get-Content $lockFile -Raw|ConvertFrom-Json
    $cache=Join-Path $Repository '.compatibility/studio-tools/redistribution'
    foreach($file in $lock.files){
        $source=Join-Path $cache $file.name
        if(!(Test-Path $source) -or (Get-FileHash $source -Algorithm SHA256).Hash -ne $file.sha256){throw ('Fonte/licença ausente ou alterada: '+$file.name)}
    }
    if(Test-Path $Destination){throw 'Destino dos fontes já existe; não sobrescrever.'}
    New-Item -ItemType Directory -Force (Join-Path $Destination 'upstream'),(Join-Path $Destination 'objects'),(Join-Path $Destination 'build-tools')|Out-Null
    foreach($file in $lock.files){Copy-Item -LiteralPath (Join-Path $cache $file.name) -Destination (Join-Path $Destination 'upstream')}
    Copy-Item -LiteralPath $lockFile -Destination $Destination
    Copy-Item -LiteralPath (Join-Path $Build.stage 'sources') -Destination (Join-Path $Destination 'adapted-modules') -Recurse
    foreach($module in @('theme','language','connection-order')){Copy-Item -LiteralPath (Join-Path $Build.stage ($module+'-raw.jar')) -Destination (Join-Path $Destination 'objects')}
    foreach($name in @('Backport.ps1','Relink-Module.ps1','toolchain.json','PortTranslations.java','README-SOURCES.txt')){Copy-Item -LiteralPath (Join-Path $Repository ('ports/studio6/'+$name)) -Destination (Join-Path $Destination 'build-tools')}
    Copy-Item -LiteralPath (Join-Path $Build.stage 'translation-fragments') -Destination (Join-Path $Destination 'translation-fragments') -Recurse
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $cache 'jvmdowngrader-2.0.1-source.zip'))
    try{foreach($entryName in @('JvmDowngrader-2.0.1/LICENSE.md','JvmDowngrader-2.0.1/license/LGPLv2.1.md')){
        $entry=$zip.GetEntry($entryName);if(!$entry){throw 'Licença LGPL não localizada no arquivo de fontes.'}
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $Destination ([IO.Path]::GetFileName($entryName))))
    }}finally{$zip.Dispose()}
    $hashes=@(Get-ChildItem $Destination -Recurse -File|Sort-Object FullName|ForEach-Object{@{path=[IO.Path]::GetRelativePath($Destination,$_.FullName).Replace('\','/');sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
    $hashes|ConvertTo-Json -Depth 4|Set-Content (Join-Path $Destination 'source-files.json') -Encoding utf8
}
