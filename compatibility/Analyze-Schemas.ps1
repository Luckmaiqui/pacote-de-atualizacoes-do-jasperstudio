#Requires -Version 7.2
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$catalog=Get-Content (Join-Path $PSScriptRoot 'catalog.json') -Raw | ConvertFrom-Json
$cache=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.compatibility'))
$previousNames=@();$previousProperties=@();$rows=@()
function Read-Entry($Zip,[string]$Name){$entry=$Zip.GetEntry($Name);if(!$entry){return $null};$reader=[IO.StreamReader]::new($entry.Open());try{return $reader.ReadToEnd()}finally{$reader.Dispose()}}
foreach($version in $catalog.versions){
    $zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $cache ($version.version+'.jar')))
    try{
        $xsd=Read-Entry $zip 'net/sf/jasperreports/engine/dtds/jasperreport.xsd'
        $properties=Read-Entry $zip 'default.jasperreports.properties'
        $names=@()
        if($xsd){$settings=[Xml.XmlReaderSettings]::new();$settings.DtdProcessing=[Xml.DtdProcessing]::Prohibit;$settings.XmlResolver=$null;$reader=[Xml.XmlReader]::Create([IO.StringReader]::new($xsd),$settings);try{$doc=[Xml.XmlDocument]::new();$doc.Load($reader)}finally{$reader.Dispose()};$names=@($doc.SelectNodes("//*[local-name()='element' or local-name()='attribute'][@name]") | ForEach-Object {$_.LocalName+':'+$_.GetAttribute('name')} | Sort-Object -Unique)}
        $keys=@([regex]::Matches($properties,'(?m)^\s*([^#!\s=][^=\s]*)\s*=') | ForEach-Object {$_.Groups[1].Value} | Sort-Object -Unique)
        $rows+=@{version=$version.version;schema=if($xsd){'bundled-xsd'}else{'jackson-model-no-legacy-xsd'};schemaSha256=if($xsd){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($xsd))).ToLowerInvariant()}else{$null};schemaNames=$names;addedSchemaNames=@($names | Where-Object {$_ -notin $previousNames});removedSchemaNames=@($previousNames | Where-Object {$_ -notin $names});defaultProperties=$keys;addedDefaultProperties=@($keys | Where-Object {$_ -notin $previousProperties});removedDefaultProperties=@($previousProperties | Where-Object {$_ -notin $keys});warning='Diferença de nomes no XSD e padrões publicados, não análise semântica completa. A ausência de uma propriedade nos padrões não comprova remoção da API.'}
        $previousNames=$names;$previousProperties=$keys
    }finally{$zip.Dispose()}
}
@{schema=1;baseline='6.6.0';asOf=$catalog.asOf;versions=$rows} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $PSScriptRoot 'schema-deltas.json') -Encoding utf8
Write-Output ('PASS: estrutura e propriedades inspecionadas em '+$rows.Count+' motores.')
