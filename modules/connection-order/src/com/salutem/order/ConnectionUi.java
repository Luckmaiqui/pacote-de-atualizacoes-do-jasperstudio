package com.salutem.order;

import java.lang.reflect.Field;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Integrates into the native pages without reparenting their trees or replacing bindings. */
final class ConnectionUi {
 private boolean queued;
 void install(Display display){
  org.eclipse.swt.widgets.Listener changed=e->{if(!queued){queued=true;display.timerExec(80,()->{queued=false;scan(display);});}};
  display.addFilter(SWT.Show,changed);display.addFilter(SWT.FocusIn,changed);scan(display);
 }
 void scan(Display display){for(Shell shell:display.getShells())if(shell.isVisible()){
  Object window=shell.getData();
  if(window instanceof WizardDialog){Object page=((WizardDialog)window).getCurrentPage();if(page!=null){
   String type=page.getClass().getName();
   if(type.equals("com.jaspersoft.studio.server.wizard.pages.ServerProfilePage"))try{pair(AccountStore.Kind.SERVER,(Text)field(page,"tuser"),(Text)field(page,"tpass"));}catch(ReflectiveOperationException ignored){}
   if(type.startsWith("com.jaspersoft.studio.server.publish.wizard.page."))attachPublishing(page);
  }}
  jdbc(shell);
 }}
 static Object field(Object instance,String name)throws ReflectiveOperationException{for(Class<?> type=instance.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(instance);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static void attachPublishing(Object page){
  for(Class<?> type=page.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())if(TreeViewer.class.isAssignableFrom(field.getType()))try{field.setAccessible(true);RepositoryOrder.attach((TreeViewer)field.get(page));}catch(ReflectiveOperationException ignored){}
 }
 private void jdbc(Control control){
  if(control.getClass().getName().equals("com.jaspersoft.studio.data.jdbc.JDBCDataAdapterComposite"))try{pair(AccountStore.Kind.DATABASE,(Text)field(control,"textUsername"),(Text)field(control,"textPassword"));}catch(ReflectiveOperationException ignored){}
  if(control instanceof Composite)for(Control child:((Composite)control).getChildren())if(child.isVisible())jdbc(child);
 }
 static void pair(AccountStore.Kind kind,Text user,Text password){
  pair(kind,user,password,AccountStore::new);
 }
 static void pair(AccountStore.Kind kind,Text user,Text password,java.util.function.Supplier<AccountStore> stores){
  if(user==null || password==null || user.isDisposed() || password.isDisposed() || user.getData("salutem.accountPicker")!=null)return;
  Composite parent=user.getParent();if(!(parent.getLayout() instanceof GridLayout))return;
  Label label=new Label(parent,SWT.NONE);label.setText("Conta");label.moveAbove(null);
  Composite row=new Composite(parent,SWT.NONE);row.setData("salutem.accountSurface",Boolean.TRUE);row.setLayout(new GridLayout(3,false));((GridLayout)row.getLayout()).marginWidth=0;((GridLayout)row.getLayout()).marginHeight=0;
  GridData rowData=new GridData(SWT.FILL,SWT.CENTER,true,false);rowData.horizontalSpan=Math.max(1,((GridLayout)parent.getLayout()).numColumns-1);row.setLayoutData(rowData);row.moveBelow(label);
  Combo picker=new Combo(row,SWT.READ_ONLY);picker.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));picker.setToolTipText("Preencher Usuário e Senha — "+kind.label);user.setData("salutem.accountPicker",picker);
  Button manage=new Button(row,SWT.PUSH);manage.setText("Gerenciar…");
  final java.util.List<AccountStore.Account> accounts=new java.util.ArrayList<>();
  Runnable reload=()->{String selected=picker.getSelectionIndex()>0 && picker.getSelectionIndex()<=accounts.size()?accounts.get(picker.getSelectionIndex()-1).id():null;accounts.clear();picker.removeAll();picker.add("— Preencher Manualmente —");try{accounts.addAll(stores.get().list(kind));for(var account:accounts)picker.add(account.name());picker.select(0);if(selected!=null)for(int i=0;i<accounts.size();i++)if(accounts.get(i).id().equals(selected))picker.select(i+1);}catch(Exception error){picker.select(0);AccountsDialog.failure(parent.getShell());}};
  reload.run();picker.addListener(SWT.Show,e->reload.run());
  picker.addListener(SWT.Selection,e->{int index=picker.getSelectionIndex()-1;if(index<0 || index>=accounts.size())return;try{var login=stores.get().read(accounts.get(index));user.setText(login.username());password.setText(login.password());}catch(Exception error){AccountsDialog.failure(parent.getShell());}});
  manage.addListener(SWT.Selection,e->{try{new AccountsDialog(parent.getShell(),stores.get()).open();reload.run();}catch(RuntimeException error){AccountsDialog.failure(parent.getShell());}});
  Button importGestao=new Button(row,SWT.PUSH);importGestao.setText("Importar do Gestão…");importGestao.addListener(SWT.Selection,e->new GestaoImportDialog(parent.getShell()).open());
  parent.layout(true,true);
 }
}
