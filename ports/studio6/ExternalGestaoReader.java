package com.salutem.order;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.widgets.Shell;

/** Isolated WebView2 host for Studio versions whose SWT has no Edge backend. */
final class ExternalGestaoReader {
 private static final String[] FILES={"GestaoReader.exe","Microsoft.Web.WebView2.Core.dll","Microsoft.Web.WebView2.WinForms.dll","WebView2Loader.dll"};
 static String read(Shell shell)throws Exception{return execute(shell,false);}
 static String verify(Shell shell)throws Exception{return execute(shell,true);}
 private static String execute(Shell shell,boolean selfTest)throws Exception{
  String executable=StudioLocation.executable(org.eclipse.core.runtime.Platform.getInstallLocation().getURL(),System.getProperty("eclipse.launcher")).toString();
  Path directory=Files.createTempDirectory("salutem-gestao-reader-");Path output=directory.resolve("export.json");List<Path> owned=new ArrayList<>();AtomicReference<String> result=new AtomicReference<>();
  org.eclipse.swt.graphics.RGB bg=shell.getBackground().getRGB(),fg=shell.getForeground().getRGB();
  String background=String.format("#%02x%02x%02x",bg.red,bg.green,bg.blue),foreground=String.format("#%02x%02x%02x",fg.red,fg.green,fg.blue);
  try{
   for(String file:FILES){Path target=directory.resolve(file);try(InputStream input=ExternalGestaoReader.class.getResourceAsStream("/gestao-reader/"+file)){if(input==null)throw new IOException("O leitor WebView2 não está incluído neste complemento.");Files.copy(input,target);}owned.add(target);}
   Path collector=directory.resolve("gestao-export.js");try(InputStream input=ExternalGestaoReader.class.getResourceAsStream("/gestao-export.js")){if(input==null)throw new IOException("Coletor indisponível.");Files.copy(input,collector);}owned.add(collector);owned.add(output);
   new ProgressMonitorDialog(shell).run(true,true,monitor->{Process process=null;try{
    monitor.beginTask("Aguardando leitura no Gestão. Faça login na janela do navegador e clique em Ler Conexões.",-1);
    List<String> command=new ArrayList<>(Arrays.asList(directory.resolve("GestaoReader.exe").toString(),"--output",output.toString(),"--collector",collector.toString(),"--studio-exe",executable,"--background",background,"--foreground",foreground));if(selfTest)command.add("--self-test");
    process=new ProcessBuilder(command).directory(directory.toFile()).start();
    while(!process.waitFor(200,TimeUnit.MILLISECONDS)){if(monitor.isCanceled()){process.destroy();if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly();throw new InterruptedException("Leitura cancelada.");}}
    if(process.exitValue()==2)return;
    if(process.exitValue()!=0)throw new IOException("O leitor terminou com código "+process.exitValue()+". Confira o Microsoft Edge WebView2 Runtime ou use Coletor Para o Navegador e Importar JSON.");
    if(!Files.isRegularFile(output)||Files.size(output)>5000000)throw new IOException("Exportação inválida ou maior que 5 MB.");
    String json=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);if(!selfTest)GestaoCatalog.parse(json);result.set(json);
   }catch(InterruptedException cancelled){throw cancelled;}catch(Exception error){throw new java.lang.reflect.InvocationTargetException(error);}finally{if(process!=null&&process.isAlive())process.destroy();monitor.done();}});
   return result.get();
  }finally{for(Path file:owned)try{Files.deleteIfExists(file);}catch(IOException busy){file.toFile().deleteOnExit();}try{Files.deleteIfExists(directory);}catch(IOException busy){directory.toFile().deleteOnExit();}}
 }
}
