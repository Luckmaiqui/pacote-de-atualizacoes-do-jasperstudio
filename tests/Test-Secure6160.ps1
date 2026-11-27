#Requires -Version 5.1
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$file=Join-Path $root 'installer/windows/Update-Secure6160.ps1'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Sintaxe inválida do atualizador'}
# Load only the pure helper, never the update/installation body.
$function=$ast.Find({param($node)$node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Runtime-Ini'},$true)
if(!$function){throw 'Helper de INI ausente'}
. ([scriptblock]::Create($function.Extent.Text))
$replacement=$ast.Find({param($node)$node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Replace-IniFile'},$true)
if(!$replacement){throw 'Helper de substituição ausente'}
. ([scriptblock]::Create($replacement.Extent.Text))
foreach($newline in @("`r`n","`n")){
 $before=@('-startup','plugins/launcher.jar','-vm','features/original/bin/java.exe','-vmargs','-Xmx1024m','-Dcustom=value') -join $newline
 $java='C:\Program Files\TIBCO\Studio $& test\runtimes\java11\bin\javaw.exe'
 $expected=$before.Replace('features/original/bin/java.exe',$java)
 if((Runtime-Ini $before $java) -cne $expected){throw 'Alteração além do caminho Java ou perda de conteúdo/literal $'}
 if((Runtime-Ini $expected $java) -cne $expected){throw 'Atualização não idempotente'}
}
foreach($invalid in @('-vmargs',"-vm`nold`n-vm`nother","-vm`n")){
 $failed=$false;try{Runtime-Ini $invalid 'new'|Out-Null}catch{$failed=$true};if(!$failed){throw 'INI ambíguo aceito'}
}
Write-Output 'PASS: INI altera somente -vm, preserva CRLF/LF e caminhos literais, idempotência e recusa ambiguidades; nenhuma instalação executada.'
$locale=$ast.Find({param($node)$node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Locale-Ini'},$true)
. ([scriptblock]::Create($locale.Extent.Text))
foreach($newline in @("`r`n","`n")){
 $before=@('-nl','en_EN','-data','C:/workspace $&','-nl','en_US','-vmargs','-Xmx1g','') -join $newline
 $expected=@('-data','C:/workspace $&','-nl','pt_BR','-vmargs','-Xmx1g','') -join $newline
 if((Locale-Ini $before) -cne $expected -or (Locale-Ini $expected) -cne $expected){throw 'Locale do INI alterou dados ou duplicou argumentos.'}
}
Write-Output 'PASS: idioma pt_BR único antes de -vmargs, CRLF/LF, workspace e argumentos preservados.'
$stage=Join-Path $root ('build/ini-replace-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $stage -Force|Out-Null
$target=Join-Path $stage 'Jaspersoft Studio.ini';$temporary=Join-Path $stage 'replacement.tmp';$backup=Join-Path $stage 'original.ini'
$oldBytes=[Text.Encoding]::UTF8.GetBytes("-vm`r`nold-java`r`n-vmargs`r`n-Xmx1024m")
$newBytes=[Text.Encoding]::UTF8.GetBytes("-vm`r`nnew-java`r`n-vmargs`r`n-Xmx1024m")
[IO.File]::WriteAllBytes($target,$oldBytes);[IO.File]::WriteAllBytes($temporary,$newBytes)
$before=(Get-FileHash -LiteralPath $target).Hash
Replace-IniFile $temporary $target $backup
if((Get-FileHash -LiteralPath $backup).Hash -ne $before -or [IO.File]::ReadAllText($target) -cne [Text.Encoding]::UTF8.GetString($newBytes) -or (Test-Path -LiteralPath $temporary)){throw 'Substituição ou backup incorreto'}
# Simulate recovery after a later installation failure, using preserved bytes.
[IO.File]::WriteAllBytes($target,[IO.File]::ReadAllBytes($backup))
if((Get-FileHash -LiteralPath $target).Hash -ne $before){throw 'Recuperação perdeu bytes do INI'}
[IO.File]::WriteAllBytes($temporary,$newBytes)
$failed=$false;try{Replace-IniFile $temporary $target $backup}catch{$failed=$true}
if(!$failed -or (Get-FileHash -LiteralPath $target).Hash -ne $before -or !(Test-Path -LiteralPath $temporary)){throw 'Backup existente foi sobrescrito ou houve alteração na recusa'}
$failed=$false;try{Replace-IniFile $temporary (Join-Path $stage 'missing.ini') (Join-Path $stage 'missing-backup.ini')}catch{$failed=$true}
if(!$failed -or !(Test-Path -LiteralPath $temporary)){throw 'Falha de substituição consumiu o temporário'}
Write-Output 'PASS: File.Replace real com backup, recuperação byte a byte, recusa de backup existente e falha sem consumir a origem; arquivos somente em build.'
