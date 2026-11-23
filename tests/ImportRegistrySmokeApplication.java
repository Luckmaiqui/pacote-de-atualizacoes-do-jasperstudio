package com.salutem.tests;

import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.commands.*;
import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.app.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.ISources;

/** Runs inside the real Equinox registry in an isolated configuration/workspace. */
public final class ImportRegistrySmokeApplication implements IApplication {
 public Object start(IApplicationContext context) throws Exception {
  Display display=new Display();Shell parent=new Shell(display);
  try {
   IConfigurationElement importer=null;IHandler accounts=null;
   for(IConfigurationElement e:Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.handlers")) {
    String id=e.getAttribute("commandId");
    if("com.salutem.jasper.gestaoImport".equals(id) || "com.salutem.jasper.accounts".equals(id)) {
     if(!"com.salutem.jasper.connectionorder".equals(e.getContributor().getName()))throw new AssertionError("Wrong contributor: "+e.getContributor().getName());
     Object handler=e.createExecutableExtension("class");
     if(!(handler instanceof IHandler))throw new AssertionError("Handler not loaded");
     System.out.println("OWNER "+id+" = "+e.getContributor().getName());
     if(id.endsWith("gestaoImport"))importer=e;else accounts=(IHandler)handler;
    }
   }
   if(importer==null || accounts==null)throw new AssertionError("Missing command registrations");
   IHandler handler=(IHandler)importer.createExecutableExtension("class");
   parent.setText("Teste de Integração da Importação");parent.open();
   AtomicReference<Throwable> error=new AtomicReference<>();boolean[] observed={false};
   display.timerExec(500,()->{
    Shell dialog=null;
    try {
     for(Shell shell:display.getShells())if(shell!=parent && shell.isVisible() && shell.getText().equals("Importar Conexões do Gestão"))dialog=shell;
     if(dialog==null)throw new AssertionError("Import dialog not visible");
     List<Control> controls=new ArrayList<>();collect(dialog,controls);
     long tables=controls.stream().filter(c->c instanceof Table && ((Table)c).getColumnCount()==5).count();
     long combos=controls.stream().filter(c->c instanceof Combo && ((Combo)c).getItemCount()>0 && ((Combo)c).getSelectionIndex()==0).count();
     Set<String> buttons=new HashSet<>();for(Control c:controls)if(c instanceof Button)buttons.add(((Button)c).getText());
     if(tables!=1 || combos!=4 || !buttons.containsAll(Set.of("Abrir Gestão e Ler…","Importar JSON…","Testar Marcados","Adicionar Testadas","Fechar")))throw new AssertionError("Import controls not initialized");
     observed[0]=true;System.out.println("PASS: registry-owned command opened a visible import dialog; five columns, four initialized selectors and expected actions.");
    }catch(Throwable failure){error.set(failure);}finally{if(dialog!=null)dialog.dispose();else if(!parent.isDisposed())parent.dispose();}
   });
   EvaluationContext variables=new EvaluationContext(null,new Object());variables.addVariable(ISources.ACTIVE_SHELL_NAME,parent);
   handler.execute(new ExecutionEvent(null,Map.of(),null,variables));
   System.out.println("Import handler returned after fixture cleanup.");
   if(error.get()!=null)throw new AssertionError("Import UI verification failed",error.get());
   if(!observed[0])throw new AssertionError("No visible import dialog observed");
   observed[0]=false;
   display.timerExec(500,()->{
    Shell dialog=null;
    try{
     for(Shell shell:display.getShells())if(shell!=parent && shell.isVisible() && shell.getText().equals("Contas de Banco de Dados e JasperServer"))dialog=shell;
     if(dialog==null)throw new AssertionError("Accounts dialog not visible");
     List<Control> controls=new ArrayList<>();collect(dialog,controls);
     if(controls.stream().noneMatch(c->c instanceof org.eclipse.swt.custom.CTabFolder && ((org.eclipse.swt.custom.CTabFolder)c).getItemCount()==2))throw new AssertionError("Account kinds not initialized");
     observed[0]=true;System.out.println("PASS: related accounts command opened its visible dialog with separate database and server tabs.");
    }catch(Throwable failure){error.set(failure);}finally{if(dialog!=null)dialog.dispose();else if(!parent.isDisposed())parent.dispose();}
   });
   accounts.execute(new ExecutionEvent(null,Map.of(),null,variables));
   if(error.get()!=null)throw new AssertionError("Accounts UI verification failed",error.get());
   if(!observed[0])throw new AssertionError("No visible accounts dialog observed");
   GestaoManagementNativeTest.run(parent);
   return IApplication.EXIT_OK;
  }finally{
   for(var job:org.eclipse.core.runtime.jobs.Job.getJobManager().find(null))if(job.getClass().getName().startsWith("com.jaspersoft.studio."))job.cancel();
   long end=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<end && Arrays.stream(org.eclipse.core.runtime.jobs.Job.getJobManager().find(null)).anyMatch(j->j.getClass().getName().startsWith("com.jaspersoft.studio.") && j.getState()==org.eclipse.core.runtime.jobs.Job.RUNNING)){if(!display.readAndDispatch())Thread.sleep(2);}
   if(!parent.isDisposed())parent.dispose();display.dispose();System.out.println("SWT fixture disposed.");
  }
 }
 private static void collect(Composite parent,List<Control> result){for(Control c:parent.getChildren()){result.add(c);if(c instanceof Composite)collect((Composite)c,result);}}
 public void stop(){}
}
