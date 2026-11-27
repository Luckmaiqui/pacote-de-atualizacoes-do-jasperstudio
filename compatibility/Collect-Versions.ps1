#Requires -Version 7.2
[CmdletBinding()]
param([string]$CacheDirectory=(Join-Path $PSScriptRoot '../.compatibility'),[string]$AsOf=(Get-Date).ToString('yyyy-MM-dd'))
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$cache=[IO.Path]::GetFullPath($CacheDirectory)
New-Item -ItemType Directory -Force $cache | Out-Null
function Text([string]$Url,[string]$File){
    $path=Join-Path $cache $File
    if($File -in @('metadata.xml','releases.json','changes.txt') -or -not(Test-Path -LiteralPath $path)){Invoke-WebRequest -Uri $Url -OutFile $path}
    return [IO.File]::ReadAllText($path)
}
function Child($Node,[string]$Name){if(!$Node){return ''};$n=$Node.SelectSingleNode("*[local-name()='$Name']");if($n){return $n.InnerText};return ''}
function Xml([string]$Content){
    $settings=[Xml.XmlReaderSettings]::new();$settings.DtdProcessing=[Xml.DtdProcessing]::Prohibit;$settings.XmlResolver=$null
    $reader=[Xml.XmlReader]::Create([IO.StringReader]::new($Content),$settings)
    try{$doc=[Xml.XmlDocument]::new();$doc.Load($reader);return ,$doc}finally{$reader.Dispose()}
}
$metadataUrl='https://repo.maven.apache.org/maven2/net/sf/jasperreports/jasperreports/maven-metadata.xml'
$meta=Xml (Text $metadataUrl 'metadata.xml')
$versions=@($meta.SelectNodes("//*[local-name()='versions']/*[local-name()='version']") | ForEach-Object InnerText | Where-Object {$_ -match '^\d+\.\d+\.\d+$' -and [version]$_ -ge [version]'6.6.0'})
$releases=Text 'https://api.github.com/repos/Jaspersoft/jasperreports/releases?per_page=100' 'releases.json' | ConvertFrom-Json
$stable=@{};foreach($release in $releases){
    if($release.prerelease -or $release.draft -or ([datetime]$release.published_at).ToString('yyyy-MM-dd') -gt $AsOf){continue}
    $v=$release.tag_name -replace '^jr-','' -replace '-','.'
    if($v -match '^\d+\.\d+\.\d+$' -and [version]$v -ge [version]'6.6.0'){$stable[$v]=$release}
}
$versions=@(($versions+@($stable.Keys)) | Sort-Object {[version]$_} -Unique)
$changes=Text 'https://raw.githubusercontent.com/Jaspersoft/jasperreports/master/changes.txt' 'changes.txt'
$sections=@{};foreach($m in [regex]::Matches($changes,'(?ms)^JasperReports ([0-9.]+) \(([^)]+)\)\s*\r?\n-+\s*\r?\n(.*?)(?=^JasperReports [0-9.]|\z)')){$sections[$m.Groups[1].Value]=@{date=$m.Groups[2].Value;body=$m.Groups[3].Value.Trim()}}
$records=@()
foreach($v in $versions){
    $url="https://repo.maven.apache.org/maven2/net/sf/jasperreports/jasperreports/$v/jasperreports-$v"
    $release=$stable[$v];$record=[ordered]@{version=$v;publishedAt=if($release){([datetime]$release.published_at).ToString('yyyy-MM-dd')}else{$null};changeLogDate=if($sections[$v]){$sections[$v].date}else{$null};releaseUrl=if($release){$release.html_url}else{$null};pomUrl="$url.pom";format=if([version]$v -lt [version]'7.0.0'){'digester-6'}else{'jackson-7'};officialJavaCertifications=@();status='analyzed';dependencies=@();notes=@();available=$false}
    try{
        $pom=Xml (Text "$url.pom" "$v.pom");$record.available=$true
        $record.pomSha256=(Get-FileHash (Join-Path $cache "$v.pom") -Algorithm SHA256).Hash.ToLowerInvariant()
        $record.publishedExtensions=@();if($release){foreach($asset in $release.assets){if($asset.name -match ('^(jasperreports-.+)-'+[regex]::Escape($v)+'\.jar$')){$record.publishedExtensions+=@{artifact=$Matches[1];downloadUrl=$asset.browser_download_url;status='published-not-tested';pomUrl=('https://repo.maven.apache.org/maven2/net/sf/jasperreports/'+$Matches[1]+"/$v/"+$Matches[1]+"-$v.pom")}}}}
        $tag=Child ($pom.DocumentElement.SelectSingleNode("*[local-name()='scm']")) 'tag'
        if(!$tag){$tag=$v};$record.sourceTag=$tag
        foreach($dep in $pom.SelectNodes("/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency']")){
            $record.dependencies+=@{group=Child $dep 'groupId';artifact=Child $dep 'artifactId';version=Child $dep 'version';scope=Child $dep 'scope';optional=(Child $dep 'optional') -eq 'true'}
        }
        $target=$pom.SelectSingleNode("//*[local-name()='plugin'][*[local-name()='artifactId']='maven-compiler-plugin']/*[local-name()='configuration']/*[local-name()='target']")
        $record.compilerTarget=if($target){$target.InnerText}else{$null}
        if(!$record.compilerTarget -and $v.StartsWith('7.')){
            $parentUrl="https://raw.githubusercontent.com/Jaspersoft/jasperreports/$tag/pom-parent.xml"
            try{$parent=Xml (Text $parentUrl "$v-parent.xml");$target=$parent.SelectSingleNode("//*[local-name()='maven.compiler.release']");if($target){$record.compilerTarget=$target.InnerText};$record.compilerSourceUrl=$parentUrl}catch{$record.notes+='Não foi possível confirmar o alvo de compilação no POM pai.'}
        }
        $jarPath=Join-Path $cache "$v.jar"
        if(!(Test-Path $jarPath)){Invoke-WebRequest "$url.jar" -OutFile $jarPath}
        $sha1=(Text "$url.jar.sha1" "$v.jar.sha1").Trim().Split(' ')[0]
        if((Get-FileHash $jarPath -Algorithm SHA1).Hash -ne $sha1){throw 'Checksum publicado diverge'}
        $record.artifactSha256=(Get-FileHash $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $zip=[IO.Compression.ZipFile]::OpenRead($jarPath)
        try{$stream=$zip.GetEntry('net/sf/jasperreports/engine/JasperCompileManager.class').Open();try{$bytes=[byte[]]::new(8);if($stream.Read($bytes,0,8) -ne 8){throw 'Classe inválida'};$record.coreClassMajor=256*[int]$bytes[6]+[int]$bytes[7];$record.coreJavaMinimum=$record.coreClassMajor-44}finally{$stream.Dispose()}}finally{$zip.Dispose()}
        $record.notes+='Alvo/bytecode do núcleo não é certificação de todas as JVMs nem das extensões.'
        if($record.format -eq 'jackson-7'){$record.notes+='Formato 7 incompatível com JRXML/JRTX 6 sem conversão explícita no Studio 7; recompilar .jasper.'}
        $body=if($release -and $release.body){$release.body}elseif($sections[$v]){$sections[$v].body}else{''}
        $record.changeTopics=@();foreach($pair in @(@('Java/JVM','Java|JDK|JVM'),@('XML/modelo','JRXML|JRTX|XML|template|style|component'),@('Compilação','compil|Ant |Maven|JDT'),@('PDF','PDF|OpenPDF|iText'),@('Planilhas','XLS|Excel|POI'),@('Dependências','dependenc|upgrad|Jackson|Batik|Groovy'),@('Recursos removidos','remov|deprecat|breaking'),@('Segurança','CVE-|whitelist|filter|secur'))){if($body -match $pair[1]){$record.changeTopics+=$pair[0]}}
        $record.releaseNotesSha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body))).ToLowerInvariant()
        # Keep the unmodified upstream evidence in the ignored cache, never in installation packages.
        [IO.File]::WriteAllText((Join-Path $cache "$v-notes.txt"),$body,[Text.UTF8Encoding]::new($false))
    }catch{$record.status='metadata-incomplete';$record.notes+=($_.Exception.Message)}
    $records+=$record;Write-Output "$v $($record.status) Java mínimo do núcleo: $($record.coreJavaMinimum)"
}
$catalog=[ordered]@{schema=1;asOf=$AsOf;metadataUrl=$metadataUrl;changesUrl='https://github.com/Jaspersoft/jasperreports/blob/master/changes.txt';latest=($records | Where-Object available | Select-Object -Last 1).version;versions=$records;javaRange=@(8..27);warning='Não confundir JasperReports Library, Jaspersoft Studio e JasperReports Server. Nenhuma JVM é certificada apenas pelo bytecode.'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'catalog.json'),($catalog|ConvertTo-Json -Depth 12)+"`n",[Text.UTF8Encoding]::new($false))
