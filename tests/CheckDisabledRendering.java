import org.eclipse.swt.*;import org.eclipse.swt.widgets.*;import org.eclipse.swt.graphics.*;import org.eclipse.swt.internal.win32.OS;import java.lang.reflect.*;
import com.salutem.theme.ThemePalette;
public class CheckDisabledRendering {
 public static void main(String[] args)throws Exception{
  Display display=new Display();Shell shell=new Shell(display);
  try{
   Object theme=Class.forName("com.salutem.theme.Startup").getConstructor().newInstance();
   Method apply=theme.getClass().getDeclaredMethod("applyPalette",Display.class,ThemePalette.class);apply.setAccessible(true);apply.invoke(theme,display,ThemePalette.find("com.salutem.jasper.theme.github"));
   Method visit=theme.getClass().getDeclaredMethod("visit",Control.class);visit.setAccessible(true);
   Composite parent=new Composite(shell,SWT.NONE);Label label=new Label(parent,SWT.NONE);label.setText("Etiqueta Desabilitada");parent.setEnabled(false);visit.invoke(theme,label);
   if(!label.getEnabled() || label.isEnabled())throw new AssertionError("Estado funcional do rótulo alterado");
   if((OS.GetWindowLong(label.handle,OS.GWL_STYLE)&0x1F)!=OS.SS_OWNERDRAW)throw new AssertionError("Rótulo herdado usa desenho com relevo");
   parent.setEnabled(true);visit.invoke(theme,label);if((OS.GetWindowLong(label.handle,OS.GWL_STYLE)&0x1F)==OS.SS_OWNERDRAW)throw new AssertionError("Estilo nativo não restaurado");
   Menu popup=new Menu(shell,SWT.POP_UP);MenuItem item=new MenuItem(popup,SWT.PUSH);item.setText("Salvar\tCtrl+S");item.setEnabled(false);final int[] calls={0};item.addListener(SWT.Selection,e->calls[0]++);
   Method menu=theme.getClass().getDeclaredMethod("menu",Menu.class);menu.setAccessible(true);menu.invoke(theme,popup);
   display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme",Boolean.FALSE);
   display.setData("org.eclipse.swt.internal.win32.Combo.useDarkTheme",Boolean.FALSE);menu.invoke(theme,popup);
   for(String name:new String[]{"useDarkModeExplorerTheme","comboUseDarkTheme"}){Field mode=Display.class.getDeclaredField(name);mode.setAccessible(true);if(!mode.getBoolean(display))throw new AssertionError("Modo escuro redefinido não foi recuperado: "+name);}
   for(String field:new String[]{"background","foreground"}){Field pixel=Menu.class.getDeclaredField(field);pixel.setAccessible(true);if(pixel.getInt(popup)!=-1)throw new AssertionError("Popup força cores clássicas");}
   if(item.getEnabled() || !item.getText().equals("Salvar\tCtrl+S"))throw new AssertionError("Comando/atalho alterado");item.notifyListeners(SWT.Selection,new Event());if(calls[0]!=1)throw new AssertionError("Listener perdido");
   com.salutem.l10n.Startup language=new com.salutem.l10n.Startup();
   try{System.out.println("DIAGNOSTIC: SWT native dark-mode availability="+OS.class.getMethod("IsDarkModeAvailable").invoke(null));}
   catch(NoSuchMethodException legacy){System.out.println("DIAGNOSTIC: SWT has no native dark-mode availability API; configured bridge state is tested separately.");}
   if(!language.translateField("Language").equals("Linguagem") || !language.translate("Language").equals("Idioma") || !language.translate("Java Bean").equals("Java Bean") || !language.translate("Data Adapters").equals("Banco de Dados") || !language.translate("Format Factory Class").equals("Classe da Fábrica de Formatação") || !language.translate("Overwrite").equals("Escrever") || !language.translate("Overwrite Only Expression").equals("Escrever Apenas Expressão"))throw new AssertionError("Contexto/capitalização da tradução");
   if(!language.translate("ID").equals("ID") || !language.translate("Id").equals("ID") || !language.translate("ID:").equals("ID:") || !language.translate("&ID").equals("&ID"))throw new AssertionError("Identificador ID traduzido indevidamente");
   Text editable=new Text(parent,SWT.NONE);editable.setText("java");Combo combo=new Combo(parent,SWT.NONE);combo.setItems("java","groovy","javascript");combo.select(0);
   Method nativeCombo=theme.getClass().getDeclaredMethod("nativeCombo",Control.class);nativeCombo.setAccessible(true);nativeCombo.invoke(theme,combo);
   if(!combo.getText().equals("java") || combo.getSelectionIndex()!=0 || combo.getItemCount()!=3)throw new AssertionError("Aplicação do tema alterou valores da lista");
   Method translate=language.getClass().getDeclaredMethod("visit",Control.class);translate.setAccessible(true);translate.invoke(language,parent);
   if("6.21.5".equals(System.getProperty("salutem.test.studio"))){
    org.eclipse.swt.custom.CLabel nullable=new org.eclipse.swt.custom.CLabel(parent,SWT.NONE){@Override public String getText(){return null;}};
    translate.invoke(language,nullable);
    if(nullable.getText()!=null||nullable.getData("salutem.ptBR.error")!=null)throw new AssertionError("Rótulo opcional nulo gerou falha de tradução");
   }
   if(!editable.getText().equals("java") || !combo.getItem(0).equals("java"))throw new AssertionError("Valor técnico editável traduzido");
   System.out.println("PASS: menus escuros sem pintura clássica; estado e listeners mantidos; rótulos desabilitados por herança em uma passagem; recuperação ao habilitar; linguagem contextual, termos técnicos e capitalização.");
  }finally{shell.dispose();display.dispose();}
 }
}
