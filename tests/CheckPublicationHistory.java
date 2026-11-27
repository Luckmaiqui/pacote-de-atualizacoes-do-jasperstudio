package com.salutem.order;

import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.TimeZone;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.*;
import org.eclipse.swt.widgets.*;
import com.jaspersoft.studio.server.model.server.ServerProfile;
import com.jaspersoft.studio.server.protocol.IConnection;
import com.jaspersoft.studio.server.publish.wizard.Publish2ServerWizard;

/** Real vendor wizard, synthetic container; no report, credential or remote operation. */
public final class CheckPublicationHistory {
 static final class Container extends WizardDialog {
  int calls;Container(Shell shell,IWizard wizard){super(shell,wizard);}
  public void run(boolean fork,boolean cancelable,IRunnableWithProgress operation)throws InvocationTargetException,InterruptedException{calls++;operation.run(new NullProgressMonitor());}
 }
 public static void main(String[] args)throws Exception{
  Display display=Display.getDefault();Shell shell=new Shell(display);
  Publish2ServerWizard wizard=new Publish2ServerWizard(null,null,0);Container original=new Container(shell,wizard);
  try{
   if(!PublishingHistoryUi.supported(wizard))throw new AssertionError("Native wizard not recognized");
   PublishingHistoryUi.attach(original);IWizardContainer wrapped=wizard.getContainer();
   if(!(wrapped instanceof PublishingHistoryUi))throw new AssertionError("Container not decorated");
   PublishingHistoryUi.attach(original);if(wizard.getContainer()!=wrapped)throw new AssertionError("Duplicate attachment");
   int[] calls={0};wrapped.run(true,true,monitor->calls[0]++);
   if(calls[0]!=1||original.calls!=1||wizard.getPageCount()!=0||wrapped.getShell()!=original.getShell())throw new AssertionError("Navigation/container changed");
   try{wrapped.run(true,true,monitor->{throw new InterruptedException("synthetic cancellation");});throw new AssertionError("Cancellation swallowed");}catch(InterruptedException expected){}
   String type=Publish2ServerWizard.class.getName();
   if(!PublishingHistoryUi.finishing(new StackTraceElement[]{new StackTraceElement(type,"performFinish","fixture",1)}))throw new AssertionError("Finish not detected");
   for(String method:new String[]{"getNextPage","doFindDependentResources","performCancel"})if(PublishingHistoryUi.finishing(new StackTraceElement[]{new StackTraceElement(type,method,"fixture",1)}))throw new AssertionError("Recorded without Finish");
   Wizard other=new Wizard(){public boolean performFinish(){return true;}};if(PublishingHistoryUi.supported(other))throw new AssertionError("Unrelated wizard intercepted");other.dispose();
   ServerProfile profile=new ServerProfile();SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd");format.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
   IConnection connection=(IConnection)Proxy.newProxyInstance(IConnection.class.getClassLoader(),new Class<?>[]{IConnection.class},(proxy,method,values)->method.getName().equals("getServerProfile")?profile:method.getName().equals("getTimestampFormat")?format:null);
   profile.setTimeZone("UTC");if(!PublishingHistoryUi.zone(connection).equals(ZoneId.of("UTC")))throw new AssertionError("Configured timezone ignored");
   profile.setTimeZone(null);if(!PublishingHistoryUi.zone(connection).equals(ZoneId.of("America/Sao_Paulo")))throw new AssertionError("Native timezone ignored");
   profile.setTimeZone("invalid-zone");try{PublishingHistoryUi.zone(connection);throw new AssertionError("Invalid zone accepted");}catch(java.time.DateTimeException expected){}
   System.out.println("PASS: native Publish2ServerWizard; idempotent attachment; original navigation and cancellation; Finish-only routing; independent wizards; configured/native timezone.");
  }finally{wizard.dispose();shell.dispose();}
 }
}
