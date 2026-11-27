#Requires -Version 7.2
param([Parameter(Mandatory=$true)][string]$BuildFile,[Parameter(Mandatory=$true)][string]$PreviousPlugins)
$ErrorActionPreference='Stop';Add-Type -AssemblyName System.IO.Compression.FileSystem
$build=Get-Content $BuildFile -Raw|ConvertFrom-Json;$count=0
foreach($entry in $build.plugins){
 $old=Join-Path $PreviousPlugins ([IO.Path]::GetFileName($entry.path));if(!(Test-Path $old)){continue}
 $left=[IO.Compression.ZipFile]::OpenRead($entry.path);$right=[IO.Compression.ZipFile]::OpenRead($old)
 try{if($left.Entries.Count -ne $right.Entries.Count){throw 'JAR mudou sem aumentar sua versão.'};foreach($item in $left.Entries){$other=$right.GetEntry($item.FullName);if(!$other -or $item.Length -ne $other.Length){throw ('JAR mudou: '+$entry.path)};$hashes=@();foreach($z in @($item,$other)){$stream=$z.Open();$sha=[Security.Cryptography.SHA256]::Create();try{$hashes+=[Convert]::ToHexString($sha.ComputeHash($stream))}finally{$stream.Dispose();$sha.Dispose()}};if($hashes[0] -ne $hashes[1]){throw ('JAR mudou: '+$item.FullName)}}}finally{$left.Dispose();$right.Dispose()}
 Copy-Item -LiteralPath $old -Destination $entry.path -Force;$entry.sha256=(Get-FileHash $entry.path).Hash;$count++
}
foreach($file in @($BuildFile,(Join-Path $build.stage 'build.json'))){$build|ConvertTo-Json -Depth 8|Set-Content $file -Encoding utf8}
Write-Output ('PASS: '+$count+' JARs semanticamente idênticos preservam os bytes anteriores.')
