#Requires -Version 7.2
[CmdletBinding()]
param([int[]]$JavaVersions=@(8,11,17,21,25,27))
$ErrorActionPreference='Stop'
$cache=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.compatibility'))
New-Item -ItemType Directory -Force $cache | Out-Null
$mavenVersion='3.9.11';$maven=Join-Path $cache "apache-maven-$mavenVersion"
if(!(Test-Path (Join-Path $maven 'boot'))){
    $url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    $archive=Join-Path $cache 'maven.zip';Invoke-WebRequest $url -OutFile $archive
    $expected=((Invoke-WebRequest "$url.sha512").Content).Trim()
    if((Get-FileHash $archive -Algorithm SHA512).Hash -ne $expected){throw 'Checksum do Maven divergente'}
    Expand-Archive -LiteralPath $archive -DestinationPath $cache
}
$java=@();$failures=@()
$configFile=Join-Path $cache 'tools.json'
if(Test-Path $configFile){$existing=Get-Content $configFile -Raw | ConvertFrom-Json;$java=@($existing.java | Where-Object {$_.major -notin $JavaVersions})}
foreach($major in $JavaVersions){
    try{
    $found=$null
    $roots=@()
    if($major -eq 8){$roots=@(Get-ChildItem (Join-Path $env:ProgramFiles 'Java') -Directory -Filter 'jre1.8*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName)}
    if($major -eq 17){$roots=@(Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Directory -ErrorAction SilentlyContinue | ForEach-Object {Get-ChildItem (Join-Path $_.FullName 'features') -Directory -Filter 'jre*17*' -ErrorAction SilentlyContinue} | ForEach-Object {Join-Path $_.FullName 'eclipsetemurin_jre'})}
    foreach($root in $roots){$candidate=Join-Path $root 'bin/java.exe';if(Test-Path $candidate){$found=$candidate;break}}
    if(!$found){
        $root=Join-Path $cache "java-$major"
        $candidate=Get-ChildItem $root -Filter java.exe -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        if(!$candidate){
            $assets=Invoke-RestMethod "https://api.adoptium.net/v3/assets/latest/$major/hotspot?architecture=x64&image_type=jre&os=windows"
            $asset=$assets | Where-Object {$_.binary.package.link.EndsWith('.zip')} | Select-Object -First 1
            if(!$asset){$assets=Invoke-RestMethod "https://api.adoptium.net/v3/assets/latest/$major/hotspot?architecture=x64&image_type=jdk&os=windows";$asset=$assets | Where-Object {$_.binary.package.link.EndsWith('.zip')} | Select-Object -First 1}
            if(!$asset){throw "Sem JRE estável Windows x64 para Java $major"}
            $archive=Join-Path $cache "java-$major.zip"
            if(!(Test-Path $archive)){Invoke-WebRequest $asset.binary.package.link -OutFile $archive}
            if((Get-FileHash $archive).Hash -ne $asset.binary.package.checksum){throw "Checksum do Java $major divergente"}
            Expand-Archive -LiteralPath $archive -DestinationPath $root
            $asset | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $root 'source.json') -Encoding utf8
            $candidate=Get-ChildItem $root -Filter java.exe -File -Recurse | Select-Object -First 1
        }
        $found=$candidate.FullName
    }
    $text=(& $found -version 2>&1 | Out-String)
    if($LASTEXITCODE -ne 0 -or $text -notmatch 'version "([^"]+)"'){throw "Java $major não iniciou"}
    $version=$Matches[1];$detected=if($version.StartsWith('1.')){[int]$version.Split('.')[1]}else{[int]$version.Split('.')[0]}
    if($detected -ne $major){throw "Java esperado $major, detectado $detected"}
    $java+=@{major=$major;version=$version;executable=$found};Write-Output "Java $major $version disponível"
    }catch{$failures+=@{major=$major;reason=$_.Exception.Message};Write-Warning "Java $major indisponível: $($_.Exception.Message). Continuando com as demais versões."}
}
@{schema=1;maven=$maven;java=@($java | Sort-Object major -Unique);unavailable=$failures} | ConvertTo-Json -Depth 5 | Set-Content $configFile -Encoding utf8
if(!$java.Count){throw 'Nenhum runtime foi preparado'}
