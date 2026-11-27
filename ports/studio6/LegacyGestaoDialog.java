package com.salutem.order;
import org.eclipse.jface.dialogs.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.program.Program;

/** The original SWT 3.106 has no Edge backend. Reuse the existing JSON flow. */
final class LegacyGestaoDialog extends TitleAreaDialog {
 private final Runnable collector,importJson;
 LegacyGestaoDialog(Shell shell,Runnable collector,Runnable importJson){super(shell);this.collector=collector;this.importJson=importJson;}
 protected Control createDialogArea(Composite parent){
  setTitle("Ler Conexões Pelo Navegador");setMessage(browserMessage());
  Composite body=(Composite)super.createDialogArea(parent);Composite area=new Composite(body,SWT.NONE);area.setLayout(new GridLayout());area.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
  Label steps=new Label(area,SWT.WRAP);steps.setText("1. Abra o Gestão no navegador e realize o login.\n2. Copie o coletor e siga as instruções para exportar as conexões.\n3. Importe o JSON para revisar e testar antes de adicionar.\n\nNenhuma conexão é adicionada automaticamente.");GridData data=new GridData(SWT.FILL,SWT.CENTER,true,false);data.widthHint=520;steps.setLayoutData(data);
  button(area,"Abrir Gestão no Navegador",()->{if(!Program.launch("https://gestao.sistemasalutem.com.br/management"))MessageDialog.openError(getShell(),"Abrir Gestão","Não foi possível abrir o navegador. Acesse https://gestao.sistemasalutem.com.br/management diretamente.");});
  button(area,"Copiar Coletor",collector);
  button(area,"Importar JSON…",()->{close();importJson.run();});return body;
 }
 static String browserMessage(){
  org.osgi.framework.Bundle studio=org.eclipse.core.runtime.Platform.getBundle("com.jaspersoft.studio");
  if(studio==null)return "O navegador Edge/WebView2 integrado não está disponível nesta versão do Studio.";
  org.osgi.framework.Version version=studio.getVersion();
  return "O Studio "+version.getMajor()+"."+version.getMinor()+"."+version.getMicro()+" não disponibiliza Edge/WebView2 integrado. Use o navegador externo e importe o JSON.";
 }
 private void button(Composite area,String text,Runnable action){Button button=new Button(area,SWT.PUSH);button.setText(text);button.addListener(SWT.Selection,e->action.run());}
 protected void createButtonsForButtonBar(Composite parent){createButton(parent,IDialogConstants.CANCEL_ID,"Fechar",false);}
}
