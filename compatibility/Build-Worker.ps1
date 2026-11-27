#Requires -Version 7.2
[CmdletBinding()]
param([string]$StudioPath)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot;$cache=Join-Path $root '.compatibility'
$config=Get-Content (Join-Path $cache 'tools.json') -Raw | ConvertFrom-Json
$java=($config.java|Where-Object major -eq 17|Select-Object -First 1).executable
if(!$java){throw 'Java 17 é necessário para o compilador bootstrap'}
if(!$StudioPath){$StudioPath=Get-ChildItem (Join-Path $env:ProgramFiles 'Jaspersoft') -Directory -ErrorAction SilentlyContinue | Where-Object Name -like '*6.20.6*' | Select-Object -First 1 -ExpandProperty FullName}
$compiler=if($StudioPath){Get-ChildItem (Join-Path $StudioPath 'plugins') -Filter 'org.eclipse.jdt.core.compiler.batch_*.jar' | Select-Object -First 1}else{$null}
if($compiler){$compilerPath=$compiler.FullName}else{
    $compilerPath=Join-Path $cache 'bootstrap-ecj-3.33.0.jar'
    $url='https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar'
    if(!(Test-Path $compilerPath)){Invoke-WebRequest $url -OutFile $compilerPath}
    $expected=((Invoke-WebRequest "$url.sha1").Content).Trim()
    if((Get-FileHash $compilerPath -Algorithm SHA1).Hash -ne $expected){throw 'Checksum do compilador bootstrap divergente'}
}
& $java '-Dfile.encoding=UTF-8' -jar $compilerPath -8 -encoding UTF-8 -d (Join-Path $cache 'worker') (Join-Path $PSScriptRoot 'java/ReportWorker.java')
if($LASTEXITCODE -ne 0){throw 'Falha ao compilar worker Java 8'}
