package com.salutem.order;
import org.eclipse.swt.*;import org.eclipse.swt.widgets.*;import org.eclipse.swt.custom.*;import org.eclipse.swt.layout.*;import org.eclipse.core.runtime.preferences.InstanceScope;
/** Local review with in-memory synthetic accounts; never uses user credentials. */
public class Review270Fixture {
 public static void main(String[]args)throws Exception{
  Display display=new Display();Shell shell=new Shell(display);shell.setText("Revisão Local 2.7.0 — Dados Fictícios");shell.setData("salutem.accountSurface",Boolean.TRUE);shell.setLayout(new GridLayout());shell.setSize(740,420);
  InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").put("themeid","com.salutem.jasper.theme.deepblue");new com.salutem.theme.Startup().earlyStartup();
  AccountStore store=new AccountStore(new CheckAccounts.Memory().proxy());store.save(null,AccountStore.Kind.DATABASE,"Banco de Demonstração","usuario_banco","synthetic-only");store.save(null,AccountStore.Kind.SERVER,"Servidor de Demonstração","usuario_servidor","synthetic-only");
  Button accounts=new Button(shell,SWT.PUSH);accounts.setText("Contas de Banco de Dados e JasperServer…");accounts.addListener(SWT.Selection,e->new AccountsDialog(shell,store).open());
  Label hint=new Label(shell,SWT.WRAP);hint.setText("Prévia local: as contas abaixo são fictícias e ficam somente na memória deste teste.");hint.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  CTabFolder tabs=new CTabFolder(shell,SWT.FLAT|SWT.BORDER);tabs.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
  for(AccountStore.Kind kind:AccountStore.Kind.values()){
   Composite form=new Composite(tabs,SWT.NONE);form.setLayout(new GridLayout(2,false));CTabItem tab=new CTabItem(tabs,SWT.NONE);tab.setText(kind.label);tab.setControl(form);
   new Label(form,SWT.NONE).setText("Usuário");Text user=new Text(form,SWT.BORDER);user.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
   new Label(form,SWT.NONE).setText("Senha");Text password=new com.jaspersoft.studio.swt.widgets.WSecretText(form,SWT.BORDER|SWT.PASSWORD);password.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));ConnectionUi.pair(kind,user,password,()->store);
  }
  Menu menu=new Menu(shell,SWT.POP_UP);for(String text:new String[]{"Abrir Sub-Relatório","Salvar","Propriedades"}){MenuItem item=new MenuItem(menu,SWT.PUSH);item.setText(text);if(text.equals("Salvar"))item.setEnabled(false);}tabs.setMenu(menu);
  tabs.setSelection(0);shell.open();while(!shell.isDisposed())if(!display.readAndDispatch())display.sleep();display.dispose();
 }
}
