import org.eclipse.swt.*;import org.eclipse.swt.widgets.*;import org.eclipse.swt.graphics.*;import org.eclipse.swt.layout.GridLayout;import org.eclipse.jface.wizard.ProgressMonitorPart;import java.lang.reflect.*;
public class CheckSaveProgress {
 public static void main(String[]args)throws Exception {
  com.salutem.l10n.Startup language=new com.salutem.l10n.Startup();
  for(String text:new String[]{"Salvar@Ctrl+S","&Save@Ctrl+S","Save\tCtrl+S"})if(!language.translateButton(text).equals("Salvar"))throw new AssertionError(text);
  if(!language.translate("Save\tCtrl+S").equals("Salvar\tCtrl+S"))throw new AssertionError("Atalho do menu alterado");
  if(!language.translate("Save 'Main Jrxml'?").equals("Salvar 'Main Jrxml'?"))throw new AssertionError("Pergunta de salvamento");
  Display display=new Display();Shell shell=new Shell(display);
  try {
   Object theme=Class.forName("com.salutem.theme.Startup").getConstructor().newInstance();Color bg=new Color(display,37,38,56),fg=new Color(display,230,233,244);
   for(String name:new String[]{"bg","fg","border","accent"}){Field field=theme.getClass().getDeclaredField(name);field.setAccessible(true);field.set(theme,name.equals("fg")?fg:bg);}
   Field muted=theme.getClass().getDeclaredField("muted");muted.setAccessible(true);muted.set(theme,new Color(display,183,190,209));
   Method configure=theme.getClass().getDeclaredMethod("configureDisabledLabels",Display.class);configure.setAccessible(true);configure.invoke(theme,display);
   Field nativeColor=Display.class.getDeclaredField("disabledLabelForegroundPixel");nativeColor.setAccessible(true);
   if(nativeColor.getInt(display)!=(183|(190<<8)|(209<<16)))throw new AssertionError("Cor nativa do rótulo desabilitado não aplicada");
   ProgressMonitorPart progress=new ProgressMonitorPart(shell,new GridLayout(1,false));progress.beginTask("Tentando RESTv2",100);progress.worked(10);
   Method extras=theme.getClass().getDeclaredMethod("extras",Control.class);extras.setAccessible(true);extras.invoke(theme,progress);check(progress,bg,fg);
   progress.setCanceled(true);if(!progress.isCanceled())throw new AssertionError("Cancelamento do progresso");progress.done();
   System.out.println("PASS: Salvar sem sufixo; menu preservado; pergunta traduzida; ProgressMonitorPart real escuro; cor nativa desabilitada aplicada; cancelamento funcional.");
  }finally{shell.dispose();display.dispose();}
 }
 private static void check(Control control,Color bg,Color fg){if(!control.getBackground().equals(bg)||!control.getForeground().equals(fg))throw new AssertionError(control.getClass().getName());if(control instanceof Composite)for(Control child:((Composite)control).getChildren())check(child,bg,fg);}
}
