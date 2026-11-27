#Requires -Version 5.1
# Experimental means rebinding metadata, not certifying or recompiling APIs.
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-ExperimentManifest([string]$Path){
 if(Test-Path $Path -PathType Container){$text=[IO.File]::ReadAllText((Join-Path $Path 'META-INF/MANIFEST.MF'))}else{$zip=[IO.Compression.ZipFile]::OpenRead($Path);try{$reader=[IO.StreamReader]::new($zip.GetEntry('META-INF/MANIFEST.MF').Open());try{$text=$reader.ReadToEnd()}finally{$reader.Dispose()}}finally{$zip.Dispose()}}
 $result=@{};foreach($line in (($text -replace '\r?\n ','') -split '\r?\n')){if($line -match '^([^:]+): (.*)$'){$result[$matches[1]]=$matches[2]}};return $result
}
function Get-ExperimentHosts([string]$Studio){
 $hosts=@{};foreach($bundle in Get-ChildItem (Join-Path $Studio 'plugins')){if($bundle.Name -match '^(com\.jaspersoft\.|org\.eclipse\.|net\.sf\.jasperreports)'){try{$mf=Read-ExperimentManifest $bundle.FullName;$id=($mf['Bundle-SymbolicName'] -split ';')[0];if($id){if($hosts.ContainsKey($id)){throw "Host duplicado: $id"};$hosts[$id]=$mf['Bundle-Version']}}catch{if($_.Exception.Message -like 'Host duplicado:*'){throw}}}};return $hosts
}
function New-ExperimentJar([string]$Source,[string]$Directory,[hashtable]$Hosts,[string]$Target){
 $zip=[IO.Compression.ZipFile]::OpenRead($Source)
 try{
  $mf=Read-ExperimentManifest $Source;$id=($mf['Bundle-SymbolicName'] -split ';')[0]
  if($id -notlike 'com.salutem.*'){return $Source} # Never rewrite third-party signed bundles.
  if(@($zip.Entries|Where-Object FullName -match '(?i)^META-INF/[^/]+\.(SF|RSA|DSA|EC)$').Count){throw 'Um complemento assinado não pode ser adaptado pelo modo experimental.'}
  $version=$mf['Bundle-Version'];$numbers=$version.Split('.');$newVersion=($numbers[0..2] -join '.')+'.experimental-'+($Target -replace '[^a-zA-Z0-9_-]','-')
  $mf['Bundle-Version']=$newVersion
  if($mf['Require-Bundle']){
   $mf['Require-Bundle']=[regex]::Replace($mf['Require-Bundle'],'(?<id>com\.jaspersoft\.studio(?:\.[\w.]+)?|net\.sf\.jasperreports(?:\.[\w.]+)?);bundle-version="[^"]+"',{
    param($match);$hostId=$match.Groups['id'].Value;if(!$Hosts.ContainsKey($hostId)){return $match.Value};return $hostId+';bundle-version="['+$Hosts[$hostId]+','+$Hosts[$hostId]+']"'
   })
  }
  if($mf['Fragment-Host']){$hostId=($mf['Fragment-Host'] -split ';')[0];if($Hosts.ContainsKey($hostId)){$mf['Fragment-Host']=$hostId+';bundle-version="['+$Hosts[$hostId]+','+$Hosts[$hostId]+']"'}}
  $manifest=New-Object Text.StringBuilder
  foreach($key in @('Manifest-Version')+@($mf.Keys|Where-Object{$_ -ne 'Manifest-Version'}|Sort-Object)){
   if(!$mf.ContainsKey($key)){continue};$line=$key+': '+$mf[$key]
   # Manifest continuation lines, keeping UTF-8 byte lengths below 72.
   while([Text.Encoding]::UTF8.GetByteCount($line) -gt 70){$n=1;while($n -lt $line.Length -and [Text.Encoding]::UTF8.GetByteCount($line.Substring(0,$n+1)) -le 70){$n++};[void]$manifest.Append($line.Substring(0,$n)+"`r`n");$line=' '+$line.Substring($n)}
   [void]$manifest.Append($line+"`r`n")
  };[void]$manifest.Append("`r`n")
  $destination=Join-Path $Directory ($id+'_'+$newVersion+'.jar')
  $output=[IO.Compression.ZipFile]::Open($destination,[IO.Compression.ZipArchiveMode]::Create)
  try{foreach($entry in $zip.Entries|Sort-Object FullName){
   $copy=$output.CreateEntry($entry.FullName,[IO.Compression.CompressionLevel]::Optimal);$copy.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero);$stream=$copy.Open()
   try{if($entry.FullName -eq 'META-INF/MANIFEST.MF'){$bytes=[Text.Encoding]::UTF8.GetBytes($manifest.ToString());$stream.Write($bytes,0,$bytes.Length)}else{$inputStream=$entry.Open();try{$inputStream.CopyTo($stream)}finally{$inputStream.Dispose()}}}finally{$stream.Dispose()}
  }}finally{$output.Dispose()};return $destination
 }finally{$zip.Dispose()}
}
