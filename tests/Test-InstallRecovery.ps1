#Requires -Version 7.2
# Fault-inject the real transaction block, only against synthetic build files.
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'installer/common/RegistryCache.ps1')
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'installer/common/Instalar.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
$transaction=$ast.FindAll({param($n) $n -is [Management.Automation.Language.TryStatementAst] -and $n.Body.Extent.Text.Contains('Copy-Item -LiteralPath $file.Source')},$true)|Sort-Object {$_.Extent.Text.Length}|Select-Object -First 1
if(!$transaction -or $transaction.CatchClauses.Count -ne 1){throw 'Transação de instalação não localizada.'}
function Copy-Item([string]$LiteralPath,[string]$Destination){
    if($script:partialFailure -and $Destination.EndsWith('com.salutem.b_2.jar')){[IO.File]::WriteAllText($Destination,'partial');throw 'injected-partial-copy'}
    Microsoft.PowerShell.Management\Copy-Item -LiteralPath $LiteralPath -Destination $Destination
}
function Move-Item([string]$LiteralPath,[string]$Destination,[switch]$Force){
    if($script:restoreFailure -and $LiteralPath.StartsWith($backup+[IO.Path]::DirectorySeparatorChar) -and $Destination.EndsWith('com.salutem.a_1.jar')){throw 'injected-restore-denied'}
    Microsoft.PowerShell.Management\Move-Item -LiteralPath $LiteralPath -Destination $Destination -Force:$Force
}
foreach($scenario in @('success','partial-copy','restore-failure')){
    $fixture=Join-Path $root ('build/install-recovery-'+[guid]::NewGuid())
    $target=Join-Path $fixture 'Studio/dropins/eclipse/plugins';$backup=Join-Path $fixture 'backup';$RegistryUserProfile=Join-Path $fixture 'profile'
    New-Item -ItemType Directory -Path $target,(Join-Path $fixture 'source') -Force|Out-Null
    $files=@(foreach($id in @('a','b')){
        [IO.File]::WriteAllText((Join-Path $target ('com.salutem.'+$id+'_1.jar')),'old-'+$id)
        $source=Join-Path $fixture ('source/com.salutem.'+$id+'_2.jar');[IO.File]::WriteAllText($source,'new-'+$id)
        [pscustomobject]@{Name=[IO.Path]::GetFileName($source);Source=$source;Hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash}
    })
    $plan=[pscustomobject]@{Root=(Join-Path $fixture 'Studio');Files=$files}
    $added=New-Object 'System.Collections.Generic.List[string]';$previous=New-Object 'System.Collections.Generic.List[object]'
    $script:partialFailure=$scenario -ne 'success';$script:restoreFailure=$scenario -eq 'restore-failure'
    $message='';try{Invoke-Expression $transaction.Extent.Text}catch{$message=$_.Exception.Message}
    if($scenario -eq 'success'){
        if($message -or $added.Count -ne 2 -or $previous.Count -ne 2){throw 'Instalação sintética não concluiu.'}
        foreach($file in $files){if((Get-FileHash -LiteralPath (Join-Path $target $file.Name) -Algorithm SHA256).Hash -ne $file.Hash){throw 'Integridade final inválida.'}}
    }else{
        if(!$message.Contains('injected-partial-copy')){throw 'Falha de cópia não foi informada.'}
        if(Get-ChildItem -LiteralPath $target -Filter '*_2.jar'){throw 'Arquivo novo/parcial permaneceu após falha.'}
        if([IO.File]::ReadAllText((Join-Path $target 'com.salutem.b_1.jar')) -ne 'old-b'){throw 'Segunda recuperação não foi tentada.'}
        if($scenario -eq 'partial-copy' -and [IO.File]::ReadAllText((Join-Path $target 'com.salutem.a_1.jar')) -ne 'old-a'){throw 'Original perdido.'}
        if($scenario -eq 'restore-failure' -and (!$message.Contains('Recuperação incompleta') -or !(Test-Path -LiteralPath (Join-Path $backup 'com.salutem.a_1.jar')))){throw 'Falha de recuperação ocultada ou backup perdido.'}
    }
    Write-Output ('PASS: transação real em fixture — '+$scenario)
}
