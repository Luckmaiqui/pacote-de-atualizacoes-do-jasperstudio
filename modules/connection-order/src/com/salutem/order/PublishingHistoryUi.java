package com.salutem.order;

import java.lang.reflect.*;
import java.text.DateFormat;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.*;
import org.eclipse.swt.widgets.*;
import com.jaspersoft.studio.model.ANode;
import com.jaspersoft.studio.server.model.MReportUnit;
import com.jaspersoft.studio.server.protocol.IConnection;
import com.jaspersoft.studio.server.WSClientHelper;

/** Decorates the supported wizard-container API, leaving its pages and publishing code intact. */
final class PublishingHistoryUi implements IWizardContainer2 {
 private static final String WIZARD="com.jaspersoft.studio.server.publish.wizard.Publish2ServerWizard";
 private final IWizard wizard;private final IWizardContainer original;
 private final PublicationHistory.Session session=new PublicationHistory.Session(Clock.systemUTC());
 private PublishingHistoryUi(IWizard wizard,IWizardContainer original){this.wizard=wizard;this.original=original;}
 static boolean supported(IWizard wizard){for(Class<?> type=wizard.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals(WIZARD))return true;return false;}
 static void attach(WizardDialog dialog){try{IWizard wizard=(IWizard)ConnectionUi.field(dialog,"wizard");if(wizard!=null&&supported(wizard)&&!(wizard.getContainer() instanceof PublishingHistoryUi))wizard.setContainer(new PublishingHistoryUi(wizard,wizard.getContainer()));}catch(ReflectiveOperationException error){throw new IllegalStateException("Não foi possível integrar o histórico ao assistente de publicação.",error);}}
 static boolean finishing(StackTraceElement[] stack){for(StackTraceElement frame:stack)if(frame.getClassName().equals(WIZARD)&&frame.getMethodName().equals("performFinish"))return true;return false;}
 public void run(boolean fork,boolean cancelable,IRunnableWithProgress nativeOperation)throws InvocationTargetException,InterruptedException{
  if(!finishing(Thread.currentThread().getStackTrace())){original.run(fork,cancelable,nativeOperation);return;}
  final MReportUnit unit;
  try{ANode node=(ANode)wizard.getClass().getMethod("getNode").invoke(wizard);while(node!=null&&!(node instanceof MReportUnit))node=node.getParent();unit=(MReportUnit)node;}
  catch(Exception error){throw new InvocationTargetException(error,"Não foi possível identificar a Unidade de Relatório; nenhuma publicação foi iniciada.");}
  if(unit==null){original.run(fork,cancelable,nativeOperation);return;} // A standalone file is not a Report Unit.
  AtomicReference<String> description=new AtomicReference<>();
  original.run(fork,cancelable,monitor->{try{
   IConnection connection=unit.getWsClient();if(connection==null)throw new IllegalStateException("Conexão do JasperReports Server indisponível.");
   String user=connection.getUsername();try{Object authenticated=connection.getUser(monitor);if(authenticated!=null){Object name=authenticated.getClass().getMethod("getUsername").invoke(authenticated);if(name instanceof String&&!((String)name).isEmpty())user=(String)name;}}catch(Exception unavailable){if(monitor.isCanceled())throw new InterruptedException("Publicação cancelada.");}
   ZoneId zone=zone(connection);String uri=unit.getValue().getUriString();Object rest=rest(connection,monitor,unit);
   PublicationDescriptionStore store=new PublicationDescriptionStore(rest,uri,monitor);
   // Include organization without copying passwords/tokens into identifiers.
   java.net.URI endpoint=new java.net.URI(connection.getServerProfile().getUrl());String server=new java.net.URI(endpoint.getScheme(),null,endpoint.getHost(),endpoint.getPort(),endpoint.getPath(),null,null).toString();
   String key=server+'\u0000'+connection.getServerProfile().getOrganisation()+'\u0000'+uri;
   description.set(session.execute(key,user,zone,store,()->nativeOperation.run(monitor),monitor::isCanceled));
  }catch(InterruptedException cancelled){throw cancelled;}catch(InvocationTargetException nativeFailure){throw nativeFailure;}catch(Exception error){throw new InvocationTargetException(error);}});
  if(description.get()!=null){unit.getValue().setDescription(description.get());refreshDescription(description.get());}
 }
 static ZoneId zone(IConnection connection){String configured=connection.getServerProfile().getTimeZone();if(configured!=null&&!configured.trim().isEmpty())return ZoneId.of(configured.trim());if(connection.getTimestampFormat() instanceof DateFormat)return ((DateFormat)connection.getTimestampFormat()).getTimeZone().toZoneId();throw new IllegalStateException("Configure o fuso horário da conexão do servidor antes de publicar.");}
 private static Object rest(IConnection connection,IProgressMonitor monitor,MReportUnit unit)throws Exception{
  try{connection.getClass().getMethod("getTarget");return connection;}catch(NoSuchMethodException wrapped){Object rest=WSClientHelper.getREST(monitor,unit);if(rest==null)throw new IllegalStateException("O histórico requer uma conexão REST v2 autenticada. A publicação não foi iniciada.");return rest;}
 }
 private void refreshDescription(String description){for(IWizardPage page:wizard.getPages())if(page.getClass().getName().endsWith(".RUnitLocationPage"))try{Object text=ConnectionUi.field(page,"ruDescription");if(text instanceof Text&&!((Text)text).isDisposed())((Text)text).setText(description);}catch(ReflectiveOperationException error){System.err.println("Salutem publication history: description persisted; field could not refresh: "+error.getClass().getSimpleName());}}
 public IWizardPage getCurrentPage(){return original.getCurrentPage();}public Shell getShell(){return original.getShell();}public void showPage(IWizardPage page){original.showPage(page);}public void updateButtons(){original.updateButtons();}public void updateMessage(){original.updateMessage();}public void updateTitleBar(){original.updateTitleBar();}public void updateWindowTitle(){original.updateWindowTitle();}public void updateSize(){if(original instanceof IWizardContainer2)((IWizardContainer2)original).updateSize();}
}
