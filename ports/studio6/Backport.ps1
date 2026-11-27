function Invoke-BackportTool {
    param([string[]]$Arguments,[string]$Log,[string]$JavaExecutable,[string]$ToolJar,[string]$WorkingDirectory)
    $lines=@('-jar',('"'+$ToolJar.Replace('\','/')+'"'))+@($Arguments|ForEach-Object{'"'+$_.Replace('\','/')+'"'})
    $argsFile=$Log+'.args';[IO.File]::WriteAllLines($argsFile,$lines,[Text.UTF8Encoding]::new($false))
    $start=[Diagnostics.ProcessStartInfo]::new($JavaExecutable);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WorkingDirectory=$WorkingDirectory
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.ArgumentList.Add('@'+$argsFile)
    $process=[Diagnostics.Process]::Start($start);$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
    if(!$process.WaitForExit(120000)){$process.Kill($true);throw 'Tempo limite do backport.'}
    $text=$stdout.Result+"`n"+$stderr.Result;[IO.File]::WriteAllText($Log,$text,[Text.Encoding]::UTF8)
    $code=$process.ExitCode;$process.Dispose()
    if($code -ne 0 -or $text -match '\bERROR\b'){throw ('Falha do backport. Consulte '+$Log)}
}

function Convert-ClassesToJava8 {
    param([string]$InputDirectory,[string]$OutputJar,[string]$Classpath,[string]$ApiJar,[string]$Prefix,[string]$JavaExecutable,[string]$ToolJar)
    if(Test-Path -LiteralPath $OutputJar){throw 'Saída existente: não substituir artefatos anteriores.'}
    $working=Split-Path $OutputJar -Parent
    $down=$OutputJar+'.downgraded';$shaded=$OutputJar+'.shaded';$downJar=$OutputJar+'.down.jar'
    $shared=@{JavaExecutable=$JavaExecutable;ToolJar=$ToolJar;WorkingDirectory=$working}
    Invoke-BackportTool -Arguments @('--classVersion','52','--noColor','downgrade','--target',$InputDirectory,$down,'--classpath',$Classpath) -Log ($OutputJar+'.backport.log') @shared
    [IO.Compression.ZipFile]::CreateFromDirectory($down,$downJar)
    Invoke-BackportTool -Arguments @('--classVersion','52','--noColor','shade','--downgradedApi',$ApiJar,'--prefix',$Prefix,'--target',$downJar,$shaded) -Log ($OutputJar+'.shade.log') @shared
    [IO.Compression.ZipFile]::CreateFromDirectory($shaded,$OutputJar)
}
