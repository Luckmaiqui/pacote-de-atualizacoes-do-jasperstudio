#Requires -Version 7.2
function Copy-VerifiedStudioPort {
    param([string]$BuildFile,[string]$Payload,[string]$Repository,[object]$Profile)
    $build=Get-Content -LiteralPath $BuildFile -Raw|ConvertFrom-Json
    if($Profile.uiStatus -ne 'validated-port' -or $Profile.studio -ne $build.studio+'.final' -or $build.platformKind -ne 'windows-installation'){throw 'O porte não tem um perfil Windows aprovado para empacotamento.'}
    $proof=Get-Content -LiteralPath (Join-Path $Repository ('ports/studio6/results/studio-'+$build.studio+'.json')) -Raw|ConvertFrom-Json
    $visual=@((Get-Content -LiteralPath (Join-Path $Repository 'ports/studio6/results/ui-initialization.json') -Raw|ConvertFrom-Json).observations|Where-Object studio -eq $build.studio)
    $requiredTests=@('CheckAccounts','CheckNativeSelection','CheckReview270','CheckPublishing','CheckThemeSwitch','CheckRepositoryOrder','CheckExplorerSearch','CheckDisabledRendering','GlobalSearchEngineTest','NumericOrderTest','SubreportPathsTest','SubreportInputsTest','RemoteSubreportMatcherTest','GestaoCatalogTest','CheckCustomColors','CheckSaveProgress','PortResourceBindings')
    if($proof.studio -ne $build.studio -or $proof.platform -ne 'windows-x64' -or @($proof.tests|Select-Object -Unique).Count -ne $requiredTests.Count -or @($requiredTests|Where-Object{$_ -notin $proof.tests}).Count -or $visual.Count -ne 1 -or $visual[0].basicWorkbenchSmoke -ne 'passed' -or @($visual[0].blockingFindings).Count){throw 'Faltam as evidências de componentes e da interface básica, ou há falhas visuais pendentes.'}
    $required=@('themeApplied','reportOpened','paletteVisible','menusTranslated')
    foreach($check in $required){if($visual[0].observedChecks.$check -ne $true){throw ('Conferência visual ausente: '+$check)}}
    $actual=@($build.plugins|Sort-Object { [IO.Path]::GetFileName($_.path) }|ForEach-Object{[IO.Path]::GetFileName($_.path)+'|'+$_.sha256.ToLowerInvariant()}) -join "`n"
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$setHash=[Convert]::ToHexString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($actual))).ToLowerInvariant()}finally{$sha.Dispose()}
    if($visual[0].artifactSetSha256 -ne $setHash){throw 'O teste visual pertence a outro conjunto de complementos.'}
    $location=Join-Path $Payload ('profiles/studio-'+$build.studio.Replace('.',''))
    if(Test-Path -LiteralPath $location){throw 'O destino do porte já existe; nenhum pacote será sobrescrito.'}
    $inventories=@{'themes'=@();'translation'=@();'connection-order'=@()}
    $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    # Validate the complete input before creating a partial module tree.
    foreach($entry in $build.plugins){
        $name=[IO.Path]::GetFileName($entry.path)
        if(!$names.Add($name) -or !$name.EndsWith('.port'+$build.studio.Replace('.','')+'.jar') -or $entry.module -notin @('theme','language','translation-fragment','connection-order')){throw 'Inventário de porte duplicado ou incompatível com a plataforma.'}
        $observed=@($proof.artifacts|Where-Object name -eq $name)
        if($observed.Count -ne 1 -or $observed[0].sha256 -ne $entry.sha256 -or (Get-FileHash -LiteralPath $entry.path -Algorithm SHA256).Hash -ne $entry.sha256){throw 'O binário diverge das evidências aprovadas.'}
    }
    foreach($module in @('theme','language','connection-order')){if($module -notin $build.plugins.module){throw ('Módulo obrigatório ausente: '+$module)}}
    foreach($entry in $build.plugins){
        $name=[IO.Path]::GetFileName($entry.path)
        $observed=@($proof.artifacts|Where-Object name -eq $name)
        if($observed.Count -ne 1 -or $observed[0].sha256 -ne $entry.sha256 -or (Get-FileHash -LiteralPath $entry.path -Algorithm SHA256).Hash -ne $entry.sha256){throw 'O binário diverge das evidências aprovadas.'}
        $id=switch($entry.module){'theme'{'themes'}'language'{'translation'}'translation-fragment'{'translation'}'connection-order'{'connection-order'}default{throw 'Tipo de componente desconhecido no porte.'}}
        $destination=Join-Path $location ('modules/'+$id+'/pacote/plugins')
        New-Item -ItemType Directory -Force $destination|Out-Null
        Copy-Item -LiteralPath $entry.path -Destination $destination
        $inventories[$id]+=@{name=$name;sha256=$entry.sha256}
    }
    foreach($id in $inventories.Keys){
        if(!$inventories[$id].Count){throw ('Módulo vazio no porte: '+$id)}
        [IO.File]::WriteAllText((Join-Path $location ('modules/'+$id+'/pacote/arquivos.json')),(ConvertTo-Json -InputObject @($inventories[$id]) -Depth 3),[Text.UTF8Encoding]::new($true))
    }
    Copy-Item -LiteralPath (Join-Path $Repository 'installer/themes/padroes.ini') -Destination (Join-Path $location 'modules/themes')
    New-Item -ItemType Directory -Force (Join-Path $location 'evidence')|Out-Null
    foreach($name in @(('studio-'+$build.studio+'.json'),'ui-initialization.json')){Copy-Item -LiteralPath (Join-Path $Repository ('ports/studio6/results/'+$name)) -Destination (Join-Path $location 'evidence')}
    Copy-Item -LiteralPath (Join-Path $Repository 'ports/studio6/toolchain.json') -Destination (Join-Path $location 'evidence')
    . (Join-Path $Repository 'ports/studio6/Redistribution.ps1')
    Copy-PortSources -Build $build -Repository $Repository -Destination (Join-Path $location 'sources')
    return $location
}
