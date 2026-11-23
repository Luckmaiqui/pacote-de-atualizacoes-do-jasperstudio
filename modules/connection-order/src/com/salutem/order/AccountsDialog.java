package com.salutem.order;

import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;

public final class AccountsDialog extends Dialog {
 private final AccountStore store;
 public AccountsDialog(Shell shell){this(shell,new AccountStore());}
 AccountsDialog(Shell shell,AccountStore store){super(shell);this.store=store;setShellStyle(getShellStyle()|SWT.RESIZE);}
 protected void configureShell(Shell shell){super.configureShell(shell);shell.setText("Contas de Banco de Dados e JasperServer");shell.setData("salutem.accountSurface",Boolean.TRUE);}
 protected Point getInitialSize(){return new Point(650,420);}
 protected Control createDialogArea(Composite parent){
  Composite area=(Composite)super.createDialogArea(parent);area.setLayout(new GridLayout());
  Label hint=new Label(area,SWT.WRAP);hint.setText("Usuários e senhas deste cadastro são protegidos pelo Armazenamento Seguro do Eclipse. Ao salvar uma conexão, o Jasper usa seu próprio mecanismo de persistência.");hint.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  CTabFolder tabs=new CTabFolder(area,SWT.FLAT|SWT.BORDER);tabs.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
  for(AccountStore.Kind kind:AccountStore.Kind.values()){
   Composite body=new Composite(tabs,SWT.NONE);body.setLayout(new GridLayout(2,false));CTabItem tab=new CTabItem(tabs,SWT.NONE);tab.setText(kind.label);tab.setControl(body);
   Table table=new Table(body,SWT.BORDER|SWT.FULL_SELECTION);table.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));table.setHeaderVisible(true);TableColumn name=new TableColumn(table,SWT.NONE);name.setText("Nome da Conta");name.setWidth(410);
   Composite buttons=new Composite(body,SWT.NONE);buttons.setLayout(new GridLayout());buttons.setLayoutData(new GridData(SWT.FILL,SWT.TOP,false,false));
   Runnable load=()->{table.removeAll();try{for(var account:store.list(kind)){TableItem item=new TableItem(table,SWT.NONE);item.setText(account.name());item.setData(account);}}catch(Exception error){failure(body.getShell());}};
   add(buttons,"Adicionar…",()->{if(new Editor(getShell(),store,kind,null).open()==OK)load.run();});
   Runnable edit=()->{if(table.getSelectionCount()==1 && new Editor(getShell(),store,kind,(AccountStore.Account)table.getSelection()[0].getData()).open()==OK)load.run();};
   add(buttons,"Editar…",edit);table.addListener(SWT.DefaultSelection,e->edit.run());
   add(buttons,"Excluir…",()->{if(table.getSelectionCount()!=1)return;var account=(AccountStore.Account)table.getSelection()[0].getData();if(MessageDialog.openConfirm(getShell(),"Excluir Conta","Excluir a conta “"+account.name()+"” deste cadastro? As conexões existentes não serão alteradas."))try{store.delete(account);load.run();}catch(Exception error){failure(getShell());}});load.run();
  }
  tabs.setSelection(0);return area;
 }
 private static void add(Composite parent,String label,Runnable action){Button b=new Button(parent,SWT.PUSH);b.setText(label);b.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));b.addListener(SWT.Selection,e->action.run());}
 protected void createButtonsForButtonBar(Composite parent){createButton(parent,IDialogConstants.CLOSE_ID,"Fechar",true);}
 protected void buttonPressed(int id){close();}
 static void failure(Shell shell){MessageDialog.openError(shell,"Contas","Não foi possível acessar ou gravar o Armazenamento Seguro do Eclipse. Nenhuma senha será salva em texto simples pelo cadastro.");}
 private static final class Editor extends TitleAreaDialog {
  private final AccountStore store;private final AccountStore.Kind kind;private final AccountStore.Account previous;private Text name,user,password;private boolean readable=true;
  Editor(Shell shell,AccountStore store,AccountStore.Kind kind,AccountStore.Account previous){super(shell);this.store=store;this.kind=kind;this.previous=previous;}
  protected void configureShell(Shell shell){super.configureShell(shell);shell.setData("salutem.accountSurface",Boolean.TRUE);shell.setText("Conta — "+kind.label);}
  protected Control createDialogArea(Composite parent){
   Composite area=(Composite)super.createDialogArea(parent);setTitle((previous==null?"Adicionar Conta — ":"Editar Conta — ")+kind.label);setMessage("O preenchimento de uma conexão só ocorre quando você selecionar esta conta.");
   Composite form=new Composite(area,SWT.NONE);form.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));form.setLayout(new GridLayout(2,false));
   name=field(form,"Nome da Conta",false);user=field(form,"Usuário",false);password=field(form,"Senha",true);
   if(previous!=null)try{var login=store.read(previous);name.setText(previous.name());user.setText(login.username());password.setText(login.password());}catch(Exception error){readable=false;setErrorMessage("Não foi possível abrir esta conta no armazenamento seguro.");}
   return area;
  }
  private Text field(Composite parent,String label,boolean secret){new Label(parent,SWT.NONE).setText(label);Text text=new Text(parent,SWT.BORDER|(secret?SWT.PASSWORD:0));GridData data=new GridData(SWT.FILL,SWT.CENTER,true,false);data.widthHint=350;text.setLayoutData(data);return text;}
  protected void okPressed(){if(!readable)return;try{store.save(previous,kind,name.getText(),user.getText(),password.getText());super.okPressed();}catch(IllegalArgumentException error){setErrorMessage(error.getMessage());}catch(Exception error){setErrorMessage("Falha no armazenamento seguro. A conta não foi salva.");}}
 }
}
