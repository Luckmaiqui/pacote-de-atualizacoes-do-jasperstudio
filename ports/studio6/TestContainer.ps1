function New-StudioTestContainer {
    param([string]$Stage,[string]$Plugins,[string]$TestJar,[object]$Port)
    $fragments=@();$expectedResources=@()
    foreach($fragment in $Port.plugins|Where-Object module -eq 'translation-fragment'){
        $zip=[IO.Compression.ZipFile]::OpenRead($fragment.path)
        try{
            $reader=[IO.StreamReader]::new($zip.GetEntry('META-INF/MANIFEST.MF').Open());$manifest=$reader.ReadToEnd() -replace '\r?\n ','';$reader.Dispose()
            $hostId=[regex]::Match($manifest,'(?m)^Fragment-Host: ([^;\r\n]+)').Groups[1].Value
            foreach($entry in $zip.Entries|Where-Object FullName -like '*_pt_BR.properties'){
                $stream=$entry.Open();$digest=[Security.Cryptography.SHA256]::Create()
                try{$hash=[Convert]::ToHexString($digest.ComputeHash($stream)).ToLowerInvariant()}finally{$stream.Dispose();$digest.Dispose()}
                $expectedResources+=($hostId+"`t"+$entry.FullName+"`t"+$hash)
            }
        }finally{$zip.Dispose()}
    }
    [IO.File]::WriteAllLines((Join-Path $Stage 'expected-resources.tsv'),$expectedResources,[Text.UTF8Encoding]::new($false))
    # Fixtures instantiate internal Studio controls. Test-only fragments expose
    # those packages; production bundles and their access rules are unchanged.
    foreach($fixture in @(@{Host='com.jaspersoft.studio';Package='com.jaspersoft.studio.data.noda'},@{Host='com.jaspersoft.studio.server';Package='com.jaspersoft.studio.server.publish.wizard.page,com.jaspersoft.studio.server.publish.wizard'})){
        $directory=Join-Path $Stage ('test-export-'+$fixture.Host)
        New-Item -ItemType Directory -Force (Join-Path $directory 'META-INF')|Out-Null
        $version=$Port.studio+'.final'
        $manifest=@('Manifest-Version: 1.0','Bundle-ManifestVersion: 2',('Bundle-SymbolicName: com.salutem.tests.exports.'+$fixture.Host),'Bundle-Version: 1.0.0',('Fragment-Host: '+$fixture.Host+';bundle-version="['+$version+','+$version+']"'),('Export-Package: '+$fixture.Package))
        [IO.File]::WriteAllText((Join-Path $directory 'META-INF/MANIFEST.MF'),($manifest -join "`r`n")+"`r`n`r`n",[Text.Encoding]::ASCII)
        $out=$directory+'.jar';[IO.Compression.ZipFile]::CreateFromDirectory($directory,$out);$fragments+=$out
    }
    foreach($id in @('connection-order','theme','language')){
        $module=$Port.plugins|Where-Object module -eq $id|Select-Object -First 1
        $zip=[IO.Compression.ZipFile]::OpenRead($module.path)
        try{$reader=[IO.StreamReader]::new($zip.GetEntry('META-INF/MANIFEST.MF').Open());$text=$reader.ReadToEnd() -replace '\r?\n ','';$reader.Dispose()}finally{$zip.Dispose()}
        $hostId=[regex]::Match($text,'(?m)^Bundle-SymbolicName: ([^;\r\n]+)').Groups[1].Value
        $version=[regex]::Match($text,'(?m)^Bundle-Version: ([^\r\n]+)').Groups[1].Value
        $source=Join-Path $Stage ('test-fragment-'+$id);[IO.Compression.ZipFile]::ExtractToDirectory($TestJar,$source)
        New-Item -ItemType Directory -Force (Join-Path $source 'META-INF')|Out-Null
        $package=if($id -eq 'theme'){'com.salutem.theme'}elseif($id -eq 'language'){'com.salutem.l10n'}else{'com.salutem.order'}
        $manifest=@('Manifest-Version: 1.0','Bundle-ManifestVersion: 2',('Bundle-SymbolicName: com.salutem.tests.'+$id), 'Bundle-Version: 1.0.0',('Fragment-Host: '+$hostId+';bundle-version="['+$version+','+$version+']"'),('Export-Package: '+$package))
        if($id -eq 'connection-order'){$manifest+=@('Require-Bundle: com.salutem.jasper.theme.deepblue,',' com.salutem.jasper.language.pt_BR')}
        if($id -eq 'theme'){$manifest+='Require-Bundle: com.salutem.jasper.language.pt_BR'}
        [IO.File]::WriteAllText((Join-Path $source 'META-INF/MANIFEST.MF'),($manifest -join "`r`n")+"`r`n`r`n",[Text.Encoding]::ASCII)
        $out=Join-Path $Stage ('com.salutem.tests.'+$id+'_1.0.0.jar');[IO.Compression.ZipFile]::CreateFromDirectory($source,$out);$fragments+=$out
    }
    $app=Join-Path $Stage 'test-application';New-Item -ItemType Directory -Force (Join-Path $app 'META-INF'),(Join-Path $app 'com/salutem/tests'),(Join-Path $Stage 'bootstrap')|Out-Null
    $zip=[IO.Compression.ZipFile]::OpenRead($TestJar)
    try{
        foreach($entry in $zip.Entries|Where-Object FullName -match '^com/salutem/tests/NativeSuiteApplication(?:\$[^/]+)?\.class$'){
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $app $entry.FullName))
        }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('ImportRegistryTestLauncher.class'),(Join-Path $Stage 'bootstrap/ImportRegistryTestLauncher.class'))
    }finally{$zip.Dispose()}
    $manifest=@('Manifest-Version: 1.0','Bundle-ManifestVersion: 2','Bundle-SymbolicName: com.salutem.tests.nativeapp;singleton:=true','Bundle-Version: 1.0.0','Bundle-RequiredExecutionEnvironment: JavaSE-1.8','Require-Bundle: org.eclipse.equinox.app,org.eclipse.core.runtime,',' org.eclipse.swt,org.eclipse.ui','Bundle-ClassPath: .')
    [IO.File]::WriteAllText((Join-Path $app 'META-INF/MANIFEST.MF'),($manifest -join "`r`n")+"`r`n`r`n",[Text.Encoding]::ASCII)
    [IO.File]::WriteAllText((Join-Path $app 'plugin.xml'),'<plugin><extension id="nativeSuite" point="org.eclipse.core.runtime.applications"><application><run class="com.salutem.tests.NativeSuiteApplication"/></application></extension></plugin>')
    $appJar=Join-Path $Stage 'com.salutem.tests.nativeapp_1.0.0.jar';[IO.Compression.ZipFile]::CreateFromDirectory($app,$appJar)
    $files=@(Get-ChildItem -LiteralPath $Plugins|Where-Object{($_.PSIsContainer -and (Test-Path -LiteralPath (Join-Path $_.FullName 'META-INF/MANIFEST.MF'))) -or $_.Extension -eq '.jar'}|ForEach-Object FullName)+@($Port.plugins|ForEach-Object path)+$fragments+@($appJar)
    $lines=@('#version=1')
    foreach($file in $files){
        # The optional API assembly may originate in a macOS archive. These
        # platform-specific bundles cannot resolve on Windows; keep them in
        # the original extraction but outside the Windows test container.
        if([IO.Path]::GetFileName($file) -match '\.(macosx|cocoa)([._]|$)'){continue}
        $directory=Test-Path -LiteralPath $file -PathType Container
        if($directory){$text=[IO.File]::ReadAllText((Join-Path $file 'META-INF/MANIFEST.MF')) -replace '\r?\n ',''}else{
            $zip=[IO.Compression.ZipFile]::OpenRead($file)
            try{$entry=$zip.GetEntry('META-INF/MANIFEST.MF');if(!$entry){continue};$reader=[IO.StreamReader]::new($entry.Open());$text=$reader.ReadToEnd() -replace '\r?\n ','';$reader.Dispose()}finally{$zip.Dispose()}
        }
        $id=[regex]::Match($text,'(?m)^Bundle-SymbolicName: ([^;\r\n]+)').Groups[1].Value;$version=[regex]::Match($text,'(?m)^Bundle-Version: ([^\r\n]+)').Groups[1].Value
        if($id -and $version){$start=if($id -in @('org.apache.felix.scr','org.eclipse.equinox.event','org.eclipse.equinox.common','org.eclipse.core.runtime','org.eclipse.equinox.app','org.eclipse.equinox.registry','org.eclipse.equinox.preferences','org.eclipse.core.jobs','org.eclipse.core.contenttype')){'true'}else{'false'};$location=if($directory){([uri]($file+'/')).AbsoluteUri}else{([uri]$file).AbsoluteUri};$lines+=($id+','+$version+','+$location+',4,'+$start)}
    }
    [IO.File]::WriteAllLines((Join-Path $Stage 'test-bundles.info'),$lines,[Text.UTF8Encoding]::new($false))
    $framework=Get-ChildItem -LiteralPath $Plugins -Filter 'org.eclipse.osgi_*.jar'|Select-Object -First 1 -ExpandProperty FullName
    $simple=Get-ChildItem -LiteralPath $Plugins -Filter 'org.eclipse.equinox.simpleconfigurator_*.jar'|Select-Object -First 1 -ExpandProperty FullName
    return @{Framework=$framework;Simple=$simple;Info=(Join-Path $Stage 'test-bundles.info');Bootstrap=(Join-Path $Stage 'bootstrap')}
}
