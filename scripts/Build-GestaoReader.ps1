#Requires -Version 7.2
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$version='1.0.4370.26'
$expected='6y+vmgBDP/1YeSZiQt9JRSj3w5ffdiL/uu/SwIQ3hSgyvqcnRGunhOvwGGG5WOrDp150G6MP8JjRG8AtW5FZ5w=='
$url="https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/$version/microsoft.web.webview2.$version.nupkg"
$cache=Join-Path $root 'build/locale-reader-tools';New-Item -ItemType Directory -Force $cache|Out-Null
$package=Join-Path $cache "webview2-$version.nupkg"
if(!(Test-Path $package)){Invoke-WebRequest $url -OutFile $package}
$sha=[Security.Cryptography.SHA512]::Create();try{$actual=[Convert]::ToBase64String($sha.ComputeHash([IO.File]::ReadAllBytes($package)))}finally{$sha.Dispose()}
if($actual -cne $expected){throw 'Checksum SHA512 do SDK WebView2 inválido.'}
$out=Join-Path $root 'artifacts/gestao-reader';New-Item -ItemType Directory -Force $out|Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($package)
try{foreach($entry in @('lib/net462/Microsoft.Web.WebView2.Core.dll','lib/net462/Microsoft.Web.WebView2.WinForms.dll','runtimes/win-x64/native/WebView2Loader.dll','LICENSE.txt')){
 $source=$zip.GetEntry($entry);if(!$source){throw "SDK incompleto: $entry"}
 $name=if($entry -eq 'LICENSE.txt'){'LICENSE-WebView2.txt'}else{[IO.Path]::GetFileName($entry)}
 [IO.Compression.ZipFileExtensions]::ExtractToFile($source,(Join-Path $out $name),$true)
}}finally{$zip.Dispose()}
$source=Join-Path $root 'installer/windows/GestaoReader.cs'
Copy-Item $source $out -Force
& "$env:WINDIR/Microsoft.NET/Framework64/v4.0.30319/csc.exe" /nologo /target:winexe /platform:x64 /codepage:65001 /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Web.Extensions.dll /reference:System.Core.dll "/reference:$out/Microsoft.Web.WebView2.Core.dll" "/reference:$out/Microsoft.Web.WebView2.WinForms.dll" "/out:$out/GestaoReader.exe" $source
if($LASTEXITCODE){throw 'Compilação do leitor falhou.'}
@{sdk=$version;url=$url;sha512Base64=$expected;consulted='2026-11-27';catalog='https://api.nuget.org/v3/registration5-semver1/microsoft.web.webview2/1.0.4370.26.json';sourceSha256=(Get-FileHash $source).Hash;files=@(Get-ChildItem $out -File|Where-Object Name -ne 'provenance.json'|ForEach-Object{@{name=$_.Name;sha256=(Get-FileHash $_.FullName).Hash}})}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $out 'provenance.json') -Encoding utf8
Write-Output 'PASS: leitor x64 compilado com SDK conferido por SHA512. O Runtime WebView2 continua sendo pré-requisito; nada foi instalado.'
