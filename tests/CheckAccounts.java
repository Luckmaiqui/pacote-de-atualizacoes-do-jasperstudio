package com.salutem.order;
import java.lang.reflect.*;import java.util.*;import org.eclipse.equinox.security.storage.*;import org.eclipse.swt.*;import org.eclipse.swt.widgets.*;import org.eclipse.swt.layout.GridLayout;
/** Contract tests with synthetic credentials only. The fake does not test Eclipse cryptography. */
public class CheckAccounts {
 static class Memory implements InvocationHandler {
  final Map<String,Memory> children=new HashMap<>();final Map<String,String> values=new HashMap<>();final Set<String> encrypted=new HashSet<>();final Memory parent,root;boolean fail;int secretReads;
  Memory(){parent=null;root=this;}Memory(Memory parent){this.parent=parent;root=parent.root;}
  ISecurePreferences proxy(){return (ISecurePreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ISecurePreferences.class},this);}
  public Object invoke(Object proxy,Method method,Object[] args)throws Exception{
   String n=method.getName();if(n.equals("node"))return children.computeIfAbsent((String)args[0],x->new Memory(this)).proxy();if(n.equals("childrenNames"))return children.keySet().toArray(String[]::new);if(n.equals("nodeExists"))return children.containsKey(args[0]);
   if(n.equals("put")||n.equals("putBoolean")){String key=(String)args[0];boolean secure=(Boolean)args[2];if(key.equals("username") || key.equals("password")){if(!secure)throw new AssertionError("Credencial sem criptografia");if(root.fail)throw new StorageException(0,"Synthetic encryption failure");}values.put(key,String.valueOf(args[1]));if(secure)encrypted.add(key);return null;}
   if(n.equals("get")){if(args[0].equals("password"))root.secretReads++;return values.getOrDefault(args[0],(String)args[1]);}if(n.equals("getBoolean"))return Boolean.parseBoolean(values.getOrDefault(args[0],String.valueOf(args[1])));
   if(n.equals("isEncrypted"))return encrypted.contains(args[0]);if(n.equals("flush"))return null;if(n.equals("removeNode")){parent.children.values().remove(this);return null;}throw new UnsupportedOperationException(n);
  }
 }
 public static void main(String[] args)throws Exception{
  Memory memory=new Memory();AccountStore store=new AccountStore(memory.proxy());
  var db=store.save(null,AccountStore.Kind.DATABASE,"Exemplo","db-user","synthetic-db");var server=store.save(null,AccountStore.Kind.SERVER,"Exemplo","server-user","synthetic-server");
  if(store.list(AccountStore.Kind.DATABASE).size()!=1 || store.list(AccountStore.Kind.SERVER).size()!=1 || memory.secretReads!=0)throw new AssertionError("Tipos misturados ou leitura desnecessária da senha");
  if(!store.read(db).username().equals("db-user") || !store.read(server).password().equals("synthetic-server"))throw new AssertionError("Credencial incorreta");
  if(store.read(db).toString().contains("synthetic"))throw new AssertionError("Segredo em toString");
  try{store.save(null,AccountStore.Kind.DATABASE,"EXEMPLO","other","other");throw new AssertionError("Duplicata aceita");}catch(IllegalArgumentException expected){}
  memory.fail=true;try{store.save(db,AccountStore.Kind.DATABASE,"Exemplo","changed","changed");throw new AssertionError("Fallback inseguro");}catch(StorageException expected){}memory.fail=false;
  if(store.list(AccountStore.Kind.DATABASE).size()!=1 || !store.read(db).password().equals("synthetic-db"))throw new AssertionError("Falha destruiu a conta anterior");
  Display d=new Display();Shell shell=new Shell(d);try{
   Composite form=new Composite(shell,SWT.NONE);form.setLayout(new GridLayout(2,false));new Label(form,SWT.NONE).setText("Usuário");Text user=new Text(form,SWT.BORDER);new Label(form,SWT.NONE).setText("Senha");Text password=new Text(form,SWT.PASSWORD|SWT.BORDER);user.setText("manual");password.setText("manual");int[] changes={0};user.addListener(SWT.Modify,e->changes[0]++);password.addListener(SWT.Modify,e->changes[0]++);
   ConnectionUi.pair(AccountStore.Kind.DATABASE,user,password,()->store);Combo picker=(Combo)user.getData("salutem.accountPicker");if(!user.getText().equals("manual") || picker.getItemCount()!=2)throw new AssertionError("Preencheu sem seleção ou exibiu contas do outro tipo");
   int count=form.getChildren().length;ConnectionUi.pair(AccountStore.Kind.DATABASE,user,password,()->store);if(form.getChildren().length!=count)throw new AssertionError("Duplicou campo Conta");
   picker.select(1);picker.notifyListeners(SWT.Selection,new Event());if(!user.getText().equals("db-user") || !password.getText().equals("synthetic-db") || changes[0]!=2)throw new AssertionError("Campos/bindings não preenchidos");
   picker.select(0);picker.notifyListeners(SWT.Selection,new Event());if(!user.getText().equals("db-user"))throw new AssertionError("Modo manual apagou campos");
   AccountsDialog dialog=new AccountsDialog(shell,store);dialog.setBlockOnOpen(false);dialog.open();dialog.close();
  }finally{shell.dispose();d.dispose();}
  var updated=store.save(db,AccountStore.Kind.DATABASE,"Atualizada","new-user","new-secret");if(store.list(AccountStore.Kind.DATABASE).size()!=1 || !store.read(updated).username().equals("new-user"))throw new AssertionError("Edição incorreta");store.delete(updated);if(!store.list(AccountStore.Kind.DATABASE).isEmpty() || store.list(AccountStore.Kind.SERVER).size()!=1)throw new AssertionError("Exclusão cruzada");
  System.out.println("PASS: separação de tipos; escrita exige criptografia; falha preserva registro; metadados sem ler senha; escolha explícita e bindings; campo único; diálogo; edição/exclusão. Credenciais sintéticas; criptografia do Eclipse não simulada como prova real.");
 }
}
