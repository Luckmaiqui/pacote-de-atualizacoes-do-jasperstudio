#Requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RequestFile)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$request=$null
try {
    $request=Get-Content -LiteralPath $RequestFile -Raw -Encoding UTF8|ConvertFrom-Json
    if($request.action -notin @('Inspect','Install','Launch','Verify')){throw 'Operação inválida.'}
    $allowed=@('themes','translation','connection-order')
    $selected=@($request.components|Select-Object -Unique)
    foreach($id in $selected){if($id -notin $allowed){throw 'Componente desconhecido.'}}
    $selected=$allowed # Unified distribution always installs the complete module set.
    $experimental=$request.experimental -eq $true
    $core=@(Get-ChildItem -LiteralPath (Join-Path $request.studio 'plugins')|Where-Object{$_.Name -match '^com\.jaspersoft\.studio_\d+\.\d+\.\d+\.[\w-]+(?:\.jar)?$'})
    if($core.Count -ne 1){throw 'Não foi possível identificar uma única versão do Studio nesta pasta.'}
    . (Join-Path $PSScriptRoot 'Experimental.ps1')
    $targetVersion=(Read-ExperimentManifest $core[0].FullName)['Bundle-Version']
    if($targetVersion -notmatch '^\d+\.\d+\.\d+\.[\w-]+$'){throw 'Versão do Studio não identificada no manifesto.'}
    $profiles=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'studio-profiles.json') -Raw -Encoding UTF8|ConvertFrom-Json).profiles
    $profile=@($profiles|Where-Object{$_.studio -eq $targetVersion})
    if(!$experimental -and $profile.Count -ne 1){throw "O Studio $targetVersion não possui perfil validado. Para testes manuais, marque Modo Experimental."}
    if(!$experimental){$profile=$profile[0];if($profile.uiStatus -notin @('validated-baseline','validated-port')){throw ("O Studio $targetVersion aguarda validação. Marque Modo Experimental para testar. "+$profile.reason)}}
    $moduleRoot=$PSScriptRoot
    if($experimental){
        $references=Get-Content (Join-Path $PSScriptRoot 'experimental-profiles.json') -Raw -Encoding UTF8|ConvertFrom-Json
        $number=[version](($targetVersion.Split('.')[0..2]) -join '.')
        $ordered=@($references|Sort-Object {[version](($_.studio.Split('.')[0..2]) -join '.')})
        if(!$ordered.Count){throw 'Pacotes de referência experimentais ausentes.'}
        $eligible=@($ordered|Where-Object{[version](($_.studio.Split('.')[0..2]) -join '.') -le $number})
        $exact=@($ordered|Where-Object studio -eq $targetVersion)
        if($exact.Count -ne 1){throw 'Este instalador não contém um porte exato desta versão. Não serão usados binários de outra versão.'}
        $reference=if($exact.Count -eq 1){$exact[0]}elseif($eligible.Count){$eligible[-1]}else{$ordered[0]}
        $moduleRoot=Join-Path $PSScriptRoot $reference.path
        $profile=[pscustomobject]@{uiStatus='experimental';observedJava=$null;observedSwt=$null;minimumWindowsBuild=$null}
        $experimentHosts=Get-ExperimentHosts $request.studio
    }elseif($targetVersion -ne '6.20.6.final'){
        $relative='profiles/studio-'+$targetVersion.Replace('.final','').Replace('.','')
        $moduleRoot=Join-Path $PSScriptRoot $relative
        if(!(Test-Path -LiteralPath $moduleRoot)){throw 'O perfil foi identificado, mas seus complementos correspondentes não estão incluídos no pacote.'}
    }
    $state=Split-Path -Parent $RequestFile
    $transaction=Join-Path $state 'transaction'
    New-Item -ItemType Directory -Path (Join-Path $transaction 'pacote/plugins') -Force|Out-Null
    $adapted=Join-Path $transaction 'adapted';if($experimental){New-Item -ItemType Directory -Path $adapted -Force|Out-Null}
    foreach($name in @('Instalar.ps1','RegistryCache.ps1','PrivateRuntime.ps1')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot ('common/'+$name)) -Destination $transaction}
    $inventory=@();$hashes=@{}
    foreach($id in $selected){
        $module=Join-Path $moduleRoot ('modules/'+$id)
        $entries=Get-Content -LiteralPath (Join-Path $module 'pacote/arquivos.json') -Raw -Encoding UTF8|ConvertFrom-Json
        foreach($entry in $entries){
            if([IO.Path]::GetFileName($entry.name) -ne $entry.name -or $entry.name -notmatch '\.jar$'){throw 'Nome de arquivo inválido.'}
            $source=Join-Path $module ('pacote/plugins/'+$entry.name)
            if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $entry.sha256){throw "Integridade inválida: $($entry.name)"}
            if($experimental -and $reference.studio -ne $targetVersion){
                $source=New-ExperimentJar $source $adapted $experimentHosts $targetVersion
                $entry=[pscustomobject]@{name=[IO.Path]::GetFileName($source);sha256=(Get-FileHash $source -Algorithm SHA256).Hash}
            }
            if($hashes.ContainsKey($entry.name)){if($hashes[$entry.name] -ne $entry.sha256){throw 'Módulos com arquivos conflitantes.'};continue}
            $hashes[$entry.name]=$entry.sha256;$inventory+=$entry
            Copy-Item -LiteralPath $source -Destination (Join-Path $transaction 'pacote/plugins')
        }
    }
    $utf8=New-Object Text.UTF8Encoding($true)
    [IO.File]::WriteAllText((Join-Path $transaction 'pacote/arquivos.json'),(ConvertTo-Json -InputObject @($inventory) -Depth 4),$utf8)
    $hosts=@('com.jaspersoft.studio')
    if($selected -contains 'connection-order'){$hosts+=@('com.jaspersoft.studio.data','com.jaspersoft.studio.server')}
    $javaMajor=if($targetVersion -eq '6.6.0.final'){8}elseif($targetVersion -eq '6.16.0.final'){11}else{17}
    if($experimental){$javaMajor=$reference.minimumJava}
    $config=@{component='unified';title='Pacote de Atualizações do JasperStudio';requiredStudioVersion=$targetVersion;requiredJavaMajor=$javaMajor;profileValidation=$profile.uiStatus;requiredJavaVersion=$profile.observedJava;requiredSwtVersion=$profile.observedSwt;requiredHosts=$hosts;launchThemes=($selected -contains 'themes');launchTranslation=($selected -contains 'translation')}
    if($targetVersion -eq '6.16.0.final'){$config.privateJavaSource=Join-Path $PSScriptRoot 'privateJava/runtime';$config.privateJavaInventory=Join-Path $PSScriptRoot 'privateJava/files.json'}
    if($profile.minimumWindowsBuild){$config.minimumWindowsBuild=$profile.minimumWindowsBuild}
    [IO.File]::WriteAllText((Join-Path $transaction 'componente.json'),($config|ConvertTo-Json),$utf8)
    Copy-Item -LiteralPath (Join-Path $moduleRoot 'modules/themes/padroes.ini') -Destination $transaction
    $powershell=Join-Path ([Environment]::GetFolderPath('System')) 'WindowsPowerShell/v1.0/powershell.exe'
    $installer=Join-Path $transaction 'Instalar.ps1'
    function Invoke-Installer([string]$Script,[string]$Flags){
        # Separate stderr from structured stdout. Windows PowerShell 5.1 can
        # turn native stderr into a terminating NativeCommandError at 2>&1.
        $mode=if($experimental){' -Experimental '}else{' '}
        $command="& '"+$Script.Replace("'","''")+"' -StudioPath '"+$request.studio.Replace("'","''")+"' -Quiet -ValidatedOnly "+$mode+$Flags
        $encoded=[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
        $log=Join-Path $state ([guid]::NewGuid().ToString('N'))
        $child=Start-Process -FilePath $powershell -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-STA','-EncodedCommand',$encoded) -WindowStyle Hidden -RedirectStandardOutput ($log+'.out.log') -RedirectStandardError ($log+'.err.log') -Wait -PassThru
        $stdout=[IO.File]::ReadAllText($log+'.out.log',[Text.Encoding]::UTF8)
        if($child.ExitCode -ne 0){throw ($stdout+"`n"+[IO.File]::ReadAllText($log+'.err.log'))}
        return $stdout
    }
    # The same validation is repeated in the elevated installer before mutation.
    $plan=(Invoke-Installer $installer '-ValidateOnly -PlanJson')|ConvertFrom-Json
    $requiredInventory=@($inventory|Where-Object {$_.name -in @($plan.FileNames)})
    $detail="Studio $($plan.Version); Java $($plan.Java); $($plan.Plugins) complementos conferidos; $($plan.OpcionaisAusentes) fragmentos opcionais não aplicáveis."
    if($experimental){$detail="EXPERIMENTAL — compatibilidade não certificada. Referência: Studio $($reference.studio). Apenas metadados dos nossos complementos foram adaptados; erros de APIs/cores/recursos ainda podem ocorrer. " + $detail}
    if($request.action -eq 'Install'){
        Invoke-Installer $installer ''|Out-Null
        $detail+=' Instalação concluída. Backups e log: '+$transaction
    }elseif($request.action -in @('Launch','Verify')){
        foreach($entry in $requiredInventory){
            $installed=Join-Path $request.studio ('dropins/eclipse/plugins/'+$entry.name)
            if(!(Test-Path -LiteralPath $installed) -or (Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Instale os módulos selecionados antes de abrir, ou desmarque os módulos não instalados.'}
        }
        if($request.action -eq 'Launch'){
        # A stable state folder avoids cleaning the registry on every launch.
        $identity=[Security.Cryptography.SHA256]::Create()
        try{$rootKey=([BitConverter]::ToString($identity.ComputeHash([Text.Encoding]::UTF8.GetBytes(([IO.Path]::GetFullPath($request.studio)).ToLowerInvariant())))).Replace('-','')}finally{$identity.Dispose()}
        $launchState=Join-Path (Split-Path -Parent $state) ('launch-'+$rootKey+'-'+(($selected|Sort-Object) -join '-'))
        New-Item -ItemType Directory -Path $launchState -Force|Out-Null
        foreach($name in @('Instalar.ps1','RegistryCache.ps1','componente.json','padroes.ini')){Copy-Item -LiteralPath (Join-Path $transaction $name) -Destination $launchState -Force}
        Invoke-Installer (Join-Path $launchState 'Instalar.ps1') '-Launch'|Out-Null
        $detail+=' Abertura solicitada ao Windows.'
        }else{$detail+=' Arquivos instalados conferidos por SHA-256.'}
    }
    $installed=$true;foreach($entry in $requiredInventory){$installedFile=Join-Path $request.studio ('dropins/eclipse/plugins/'+$entry.name);if(!(Test-Path -LiteralPath $installedFile) -or (Get-FileHash -LiteralPath $installedFile).Hash -ne $entry.sha256){$installed=$false;break}}
    $result=@{ok=$true;message=$detail;studio=$plan.Version;java=$plan.Java;plugins=$plan.Plugins;installed=$installed;optionalSkipped=$plan.OpcionaisAusentes;transaction=$transaction;experimental=$experimental;compatibility=if($experimental){'not-certified'}else{$profile.uiStatus}}
    [IO.File]::WriteAllText((Join-Path $state 'result.json'),($result|ConvertTo-Json -Depth 5),$utf8)
    exit 0
}catch{
    $result=@{ok=$false;message=$_.Exception.Message}
    [IO.File]::WriteAllText((Join-Path (Split-Path -Parent $RequestFile) 'result.json'),($result|ConvertTo-Json),[Text.UTF8Encoding]::new($true))
    exit 1
}
