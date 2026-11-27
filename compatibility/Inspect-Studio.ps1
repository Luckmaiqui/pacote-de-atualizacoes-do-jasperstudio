#Requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StudioPath)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=(Resolve-Path -LiteralPath $StudioPath).Path
function Manifest([string]$File){$zip=[IO.Compression.ZipFile]::OpenRead($File);try{$entry=$zip.GetEntry('META-INF/MANIFEST.MF');if(!$entry){return @{}};$reader=[IO.StreamReader]::new($entry.Open());try{$text=$reader.ReadToEnd() -replace "\r?\n ",''}finally{$reader.Dispose()};$result=@{};foreach($line in $text -split '\r?\n'){if($line -match '^([^:]+): (.*)$'){$result[$matches[1]]=$matches[2]}};return $result}finally{$zip.Dispose()}}
$bundles=@{}
foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'plugins') -File -Filter '*.jar'){
    if($file.Name -match '^(com\.jaspersoft\.studio_|com\.jaspersoft\.studio\.(data|server)_|net\.sf\.jasperreports_|org\.eclipse\.swt\.win32)'){$mf=Manifest $file.FullName;$id=($mf['Bundle-SymbolicName'] -split ';')[0];if($id){$bundles[$id]=$mf['Bundle-Version']}}
}
$version=$bundles['com.jaspersoft.studio'];if(!$version){throw 'Não foi possível identificar o Studio pelo manifesto'}
$lines=Get-Content -LiteralPath (Join-Path $root 'Jaspersoft Studio.ini');$javaVersion=$null
for($i=0;$i -lt $lines.Count-1;$i++){if($lines[$i].Trim() -eq '-vm'){$vm=$lines[$i+1].Trim().Trim('"');if(![IO.Path]::IsPathRooted($vm)){$vm=Join-Path $root $vm};$directory=if(Test-Path $vm -PathType Leaf){Split-Path $vm -Parent}else{$vm};for($j=0;$j -lt 5 -and $directory;$j++){$release=Join-Path $directory 'release';if(Test-Path $release){$content=[IO.File]::ReadAllText($release);if($content -match 'JAVA_VERSION="([^"]+)"'){$javaVersion=$Matches[1];break}};$directory=Split-Path $directory -Parent};break}}
$major=if($javaVersion -like '1.*'){[int]$javaVersion.Split('.')[1]}elseif($javaVersion){[int]$javaVersion.Split('.')[0]}else{$null}
$reasons=@();if($version -ne '6.20.6.final'){$reasons+='APIs internas e fragmentos deste pacote ainda não portados/validados para este Studio.'};if(!$major -or $major -lt 17){$reasons+='Os complementos atuais têm bytecode Java 17; este runtime não foi confirmado como compatível.'}
if($major -gt 17){$reasons+='Esta JVM foi avaliada pelo worker separadamente; a interface do Studio ainda não foi validada nela.'}
@{studio=$version;configuredJava=$javaVersion;javaMajor=$major;bundles=$bundles;uiExtensions=if(!$reasons.Count){'validated-baseline'}else{'not-supported-current-ui-build'};reasons=$reasons;reportEngineSupport='Independente: consulte catalog.json e a matriz do worker isolado. Este diagnóstico não modifica a instalação.'} | ConvertTo-Json -Depth 5
