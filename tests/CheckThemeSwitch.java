import org.eclipse.swt.*;import org.eclipse.swt.widgets.*;import org.eclipse.swt.graphics.*;import org.eclipse.swt.layout.GridLayout;import org.eclipse.jface.wizard.ProgressMonitorPart;import org.eclipse.nebula.widgets.gallery.*;import com.salutem.theme.ThemePalette;import java.lang.reflect.*;
public class CheckThemeSwitch {
 private static double luminance(RGB rgb){double total=0;int[] channels={rgb.red,rgb.green,rgb.blue};double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double value=channels[i]/255.0;total+=weights[i]*(value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4));}return total;}
 private static void readableSamples(Table table){for(TableItem row:table.getItems()){double a=luminance(row.getForeground(1).getRGB()),b=luminance(row.getBackground(1).getRGB());if((Math.max(a,b)+.05)/(Math.min(a,b)+.05)<4.5)throw new AssertionError("Amostra da fonte sem contraste");if(!"Sample".equals(row.getText(1)))throw new AssertionError("Amostra alterada");}}
 public static void main(String[]args)throws Exception{
  boolean ownDisplay=Display.getCurrent()==null;Display d=ownDisplay?new Display():Display.getCurrent();Shell shell=new Shell(d);
  try{
   Object theme=Class.forName("com.salutem.theme.Startup").getConstructor().newInstance();
   Method apply=theme.getClass().getDeclaredMethod("applyPalette",Display.class,ThemePalette.class);apply.setAccessible(true);
   Method extras=theme.getClass().getDeclaredMethod("extras",Control.class);extras.setAccessible(true);
   Gallery gallery=new Gallery(shell,SWT.V_SCROLL);GalleryItem group=new GalleryItem(gallery,SWT.NONE);GalleryItem item=new GalleryItem(group,SWT.NONE);item.setText("Template");gallery.setSelection(new GalleryItem[]{item});
   ProgressMonitorPart progress=new ProgressMonitorPart(shell,new GridLayout());
   Control compositeCombo=null;Class<?> compositeType=null;String selectedText=null;int selectedIndex=-1;
   if("6.21.5".equals(System.getProperty("salutem.test.studio"))){
    compositeType=Class.forName("net.sf.jasperreports.eclipse.ui.JSSTableCombo");
    Class<?> viewerType=Class.forName("com.jaspersoft.studio.property.combomenu.WritableComboTableViewer");
    Object viewer=viewerType.getConstructor(Composite.class,int.class).newInstance(shell,SWT.NONE);
    compositeCombo=(Control)viewerType.getMethod("getControl").invoke(viewer);
    if(compositeCombo.getClass()==compositeType||!compositeType.isInstance(compositeCombo))throw new AssertionError("Fixture deve usar a subclasse real da barra de fontes");
    compositeType.getMethod("defineColumns",int.class).invoke(compositeCombo,2);
    Table table=(Table)compositeType.getMethod("getTable").invoke(compositeCombo);
    for(String value:new String[]{"SansSerif","Arial","Monospace"}){TableItem row=new TableItem(table,SWT.NONE);row.setText(new String[]{value,"Sample"});row.setForeground(1,d.getSystemColor(SWT.COLOR_BLACK));}
    compositeType.getMethod("select",int.class).invoke(compositeCombo,1);
    selectedText=(String)compositeType.getMethod("getText").invoke(compositeCombo);
    selectedIndex=(Integer)compositeType.getMethod("getSelectionIndex").invoke(compositeCombo);
    if(selectedIndex!=1||!"Arial".equals(selectedText))throw new AssertionError("Fixture do JSSTableCombo não inicializou a fonte selecionada");
   }
   int count=0;
   for(ThemePalette p:ThemePalette.all()){
    apply.invoke(theme,d,p);extras.invoke(theme,gallery);extras.invoke(theme,progress);
    RGB expected=new RGB((p.background>>16)&255,(p.background>>8)&255,p.background&255);
    if(!gallery.getBackground().getRGB().equals(expected)||!progress.getBackground().getRGB().equals(expected))throw new AssertionError(p.id);
    DefaultGalleryItemRenderer renderer=(DefaultGalleryItemRenderer)gallery.getItemRenderer();
    RGB selected=renderer.getSelectionBackgroundColor().getRGB();
    if(selected.red!=((p.accent>>16)&255)||selected.green!=((p.accent>>8)&255)||selected.blue!=(p.accent&255))throw new AssertionError("Seleção: "+p.id);
    Color disabled=(Color)d.getData("org.eclipse.swt.internal.win32.Label.disabledForegroundColor");
    if(disabled.getRed()!=((p.muted>>16)&255)||disabled.getGreen()!=((p.muted>>8)&255)||disabled.getBlue()!=(p.muted&255))throw new AssertionError("Rótulo desabilitado: "+p.id);
    if(gallery.getSelection()[0]!=item)throw new AssertionError("Perdeu a seleção");count++;
    if(compositeCombo!=null){
     for(boolean inherited:new boolean[]{false,true}){
      compositeType.getMethod("setInherithed",boolean.class).invoke(compositeCombo,inherited);
      extras.invoke(theme,compositeCombo);
      org.eclipse.swt.custom.StyledText input=(org.eclipse.swt.custom.StyledText)compositeType.getMethod("getTextControl").invoke(compositeCombo);
      Table popup=(Table)compositeType.getMethod("getTable").invoke(compositeCombo);
      readableSamples(popup);
      // Opening refreshes the vendor rows after the first styling pass.
      for(TableItem row:popup.getItems())row.setForeground(1,d.getSystemColor(SWT.COLOR_BLACK));
      popup.getShell().notifyListeners(SWT.Show,new Event());while(d.readAndDispatch()){}
      readableSamples(popup);
      if(!input.getBackground().getRGB().equals(expected)||!popup.getBackground().getRGB().equals(expected))throw new AssertionError("JSSTableCombo continua claro: "+p.id);
      int value=inherited?p.muted:p.foreground;RGB expectedText=new RGB((value>>16)&255,(value>>8)&255,value&255);
      if(!input.getForeground().getRGB().equals(expectedText))throw new AssertionError("Contraste do JSSTableCombo: "+p.id);
      Field state=compositeType.getDeclaredField("isInherited");state.setAccessible(true);
      if(state.getBoolean(compositeCombo)!=inherited||!selectedText.equals(compositeType.getMethod("getText").invoke(compositeCombo))||selectedIndex!=(Integer)compositeType.getMethod("getSelectionIndex").invoke(compositeCombo)||popup.getItemCount()!=3)throw new AssertionError("Cor alterou fonte/seleção/opções/herança");
     }
    }
   }
   if(count!=7)throw new AssertionError("Quantidade de temas");
   Field pool=theme.getClass().getDeclaredField("colorPool");pool.setAccessible(true);int colors=((java.util.Map<?,?>)pool.get(theme)).size();
   for(int repeat=0;repeat<10;repeat++)for(ThemePalette p:ThemePalette.all())apply.invoke(theme,d,p);
   if(((java.util.Map<?,?>)pool.get(theme)).size()!=colors)throw new AssertionError("Cores acumuladas ao alternar temas");
   System.out.println("PASS: sete paletas aplicadas a Gallery e ProgressMonitorPart; seleção e rótulos desabilitados atualizados.");
   if(compositeCombo!=null)System.out.println("PASS: JSSTableCombo real nas sete paletas, incluindo StyledText e tabela interna; texto, seleção e opções preservados.");
  }finally{shell.dispose();if(ownDisplay)d.dispose();}
 }
}
