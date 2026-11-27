package com.salutem.tests;
import org.eclipse.equinox.app.*;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.eclipse.swt.widgets.Display;

/** Runs one component test in real bundle class spaces, using test fragments
 * to access package-private code without changing production JARs. */
public final class NativeSuiteApplication implements IApplication {
 public Object start(IApplicationContext context){
  String name=System.getProperty("salutem.test.class","");
  boolean workbenchFixture=name.equals("CheckThemeSwitch")&&"6.21.5".equals(System.getProperty("salutem.test.studio"));
  Display display=new Display();
  try{
   if(name.equals("PortResourceBindings")){
    System.out.println("INSTALL-URL="+Platform.getInstallLocation().getURL());try{java.nio.file.Paths.get(Platform.getInstallLocation().getURL().toURI());System.out.println("INSTALL-URI=accepted");}catch(Exception diagnostic){System.out.println("INSTALL-URI="+diagnostic);}
    int count=0;
    for(String line:java.nio.file.Files.readAllLines(java.nio.file.Paths.get(System.getProperty("salutem.test.resources")),java.nio.charset.StandardCharsets.UTF_8)){
     String[] item=line.split("\\t");Bundle target=Platform.getBundle(item[0]);
     if(target==null)throw new AssertionError("Translation host absent: "+item[0]);
     java.net.URL resource=target.getResource(item[1]);if(resource==null)throw new AssertionError("Fragment not attached: "+line);
     java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
     try(java.io.InputStream stream=resource.openStream()){byte[] buffer=new byte[8192];for(int read;(read=stream.read(buffer))!=-1;)digest.update(buffer,0,read);}
     StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
     if(!actual.toString().equalsIgnoreCase(item[2]))throw new AssertionError("Wrong translation resource: "+line);
     count++;
    }
    if(count==0)throw new AssertionError("Empty resource inventory");
    if("6.6.0".equals(System.getProperty("salutem.test.studio"))||"6.16.0".equals(System.getProperty("salutem.test.studio"))){
     if(!"pt_BR".equals(Platform.getNL()))throw new AssertionError("Portuguese locale fixture missing");
     Bundle language=Platform.getBundle("com.salutem.jasper.language.pt_BR");
     Object handler=language.loadClass("com.salutem.l10n.SelectedLocaleHandler").getConstructor().newInstance();
     Class<?> event=language.loadClass("org.eclipse.core.commands.ExecutionEvent");
     // No workbench and no writable INI: the already-active selection must be a no-op.
     handler.getClass().getMethod("execute",event).invoke(handler,event.getConstructor().newInstance());
     boolean command=false;for(org.eclipse.core.runtime.IConfigurationElement e:Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.commands"))if("com.salutem.jasper.language.pt_BR.select".equals(e.getAttribute("id"))){for(org.eclipse.core.runtime.IConfigurationElement p:e.getChildren("commandParameter"))if("com.jaspersoft.studio.switchlanguage.locale".equals(p.getAttribute("id")))command=true;}
     if(!command)throw new AssertionError("Portuguese menu command not registered");
     Bundle order=Platform.getBundle("com.salutem.jasper.connectionorder");Class<?> api=order.loadClass("com.salutem.order.StudioApi");java.lang.reflect.Method hasEdge=api.getDeclaredMethod("hasEdge");hasEdge.setAccessible(true);boolean expectedEdge;try{org.eclipse.swt.SWT.class.getField("EDGE");expectedEdge=true;}catch(NoSuchFieldException absent){expectedEdge=false;}if(!Boolean.valueOf(expectedEdge).equals(hasEdge.invoke(null)))throw new AssertionError("Legacy Edge detection is incorrect");
     if(!expectedEdge){
      Class<?> readerClass=order.loadClass("com.salutem.order.ExternalGestaoReader");java.lang.reflect.Method verify=readerClass.getDeclaredMethod("verify",org.eclipse.swt.widgets.Shell.class);verify.setAccessible(true);org.eclipse.swt.widgets.Shell readerShell=new org.eclipse.swt.widgets.Shell(display);try{String probe=(String)verify.invoke(null,readerShell);if(!probe.contains("\"passed\":true"))throw new AssertionError("Reader bridge failed: "+probe);System.out.println("PASS: actual OSGi-to-reader process bridge, install URL with spaces/plugins, WebView2, icon and theme.");}finally{readerShell.dispose();}
      for(String file:new String[]{"GestaoReader.exe","Microsoft.Web.WebView2.Core.dll","Microsoft.Web.WebView2.WinForms.dll","WebView2Loader.dll","LICENSE-WebView2.txt"})if(order.getResource("gestao-reader/"+file)==null)throw new AssertionError("Reader resource absent: "+file);
      Class<?> locale=language.loadClass("com.salutem.l10n.LocaleSelection");if(!Boolean.TRUE.equals(locale.getMethod("portuguese",String.class,java.util.Locale.class).invoke(null,"pt_BR",java.util.Locale.ENGLISH)))throw new AssertionError("App locale ignored");
     }
     Class<?> dialogType=order.loadClass("com.salutem.order.LegacyGestaoDialog");java.lang.reflect.Constructor<?> constructor=dialogType.getDeclaredConstructor(org.eclipse.swt.widgets.Shell.class,Runnable.class,Runnable.class);constructor.setAccessible(true);
     org.eclipse.swt.widgets.Shell parent=new org.eclipse.swt.widgets.Shell(display);org.eclipse.jface.window.Window dialog=(org.eclipse.jface.window.Window)constructor.newInstance(parent,(Runnable)()->{},(Runnable)()->{});
     try{dialog.create();if(dialog.getShell()==null||dialog.getShell().isDisposed())throw new AssertionError("Legacy import dialog did not initialize");String message=((org.eclipse.jface.dialogs.TitleAreaDialog)dialog).getMessage();if(!message.contains(System.getProperty("salutem.test.studio")))throw new AssertionError("Import dialog reports a different Studio version: "+message);}finally{dialog.close();parent.dispose();}
     System.out.println("PASS: legacy Portuguese re-selection without INI write; registered command; browser detection and external import dialog initialization.");
    }
    System.out.println("PASS: "+count+" translation resources retrieved through their real OSGi hosts, with matching SHA-256.");
    return IApplication.EXIT_OK;
   }
   String host=name.equals("com.salutem.theme.CheckCustomColors")?"com.salutem.jasper.theme.deepblue":"com.salutem.jasper.connectionorder";
   Bundle bundle=Platform.getBundle(host);if(bundle==null)throw new IllegalStateException("Test host absent: "+host);
   if(workbenchFixture){
    // JSSTableCombo obtains its colour registry from the actual workbench.
    // A bundle container alone cannot construct this vendor control.
    final Throwable[] failure=new Throwable[1];
    // Test only this vendor widget and its real theme registry. Product early
    // startup jobs belong to the separate private-preview validation.
    java.util.Set<String> startupPlugins=new java.util.LinkedHashSet<>();
    for(org.eclipse.core.runtime.IConfigurationElement element:Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.startup"))startupPlugins.add(element.getContributor().getName());
    org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE.getNode("org.eclipse.ui.workbench").put("PLUGINS_NOT_ACTIVATED_ON_STARTUP",String.join(";",startupPlugins));
    org.eclipse.ui.PlatformUI.createAndRunWorkbench(display,new org.eclipse.ui.application.WorkbenchAdvisor(){
     public String getInitialWindowPerspectiveId(){return "org.eclipse.ui.resourcePerspective";}
     public void postStartup(){display.asyncExec(()->{
      try{bundle.loadClass(name).getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
      catch(Throwable error){failure[0]=error;}
      finally{org.eclipse.ui.PlatformUI.getWorkbench().close();}
     });}
    });
    if(failure[0]!=null)throw failure[0];
    return IApplication.EXIT_OK;
   }
   bundle.loadClass(name).getMethod("main",String[].class).invoke(null,(Object)new String[0]);
   return IApplication.EXIT_OK;
  }catch(Throwable error){if(error instanceof java.lang.reflect.InvocationTargetException && error.getCause()!=null)error=error.getCause();error.printStackTrace();return Integer.valueOf(1);}
  // The dedicated launcher exits this JVM immediately on return. Keep its
  // workbench display alive until then: OSGi startup notifications can still
  // have queued UI callbacks after the synthetic workbench closes.
  finally{if(!workbenchFixture&&!display.isDisposed())display.dispose();}
 }
 public void stop(){}
}
