package com.salutem.theme;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.internal.win32.OS;

/** Selected per-item colours are independent of ToolBar.setForeground in new SWT. */
public final class ModernToolbarTest {
 public static void main(String[] args)throws Exception{
  Display display=new Display();Color background=new Color(display,37,38,56),foreground=new Color(display,230,233,244),selection=new Color(display,36,90,134);
  Image icon=new Image(display,16,16);GC painter=new GC(icon);painter.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));painter.fillRectangle(0,0,16,16);painter.dispose();
  Shell shell=new Shell(display);shell.setSize(400,160);
  try{
   ToolBar bar=new ToolBar(shell,SWT.FLAT);bar.setBounds(10,10,350,40);bar.setBackground(background);bar.setForeground(foreground);
   ToolItem item=new ToolItem(bar,SWT.CHECK);item.setText("Relatório Principal");item.setImage(icon);item.setSelection(true);
   item.setBackground(display.getSystemColor(SWT.COLOR_LIST_SELECTION));item.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
   shell.open();pump(display);Color originalBackground=item.getBackground(),originalForeground=item.getForeground();
   Rectangle originalBounds=item.getBounds();long initialDC=OS.GetDC(bar.handle);int originalPixel;
   try{originalPixel=OS.GetPixel(initialDC,originalBounds.x+originalBounds.width-4,originalBounds.y+3);}finally{OS.ReleaseDC(bar.handle,initialDC);}
   final boolean[] active={true};final Color[] requested={foreground};LegacyToolbarColors.install(bar,()->active[0]?requested[0]:null,()->selection);bar.redraw();pump(display);
   if(!item.getBackground().equals(originalBackground) || !item.getForeground().equals(originalForeground))throw new AssertionError("Stored per-item colours were changed");
   if(!item.getSelection() || !item.getText().equals("Relatório Principal") || !item.getEnabled())throw new AssertionError("Selected item state changed");
   long dc=OS.GetDC(bar.handle);
   try{
    Rectangle bounds=item.getBounds();if(OS.GetPixel(dc,bounds.x+bounds.width-4,bounds.y+3)!=selection.handle)throw new AssertionError("Selected item pixels still use the native light background");
    int icons=0,text=0;for(int y=bounds.y;y<bounds.y+bounds.height;y++)for(int x=bounds.x;x<bounds.x+bounds.width;x++){int pixel=OS.GetPixel(dc,x,y);if(pixel==0xFF00FF)icons++;if(pixel==foreground.handle)text++;}
    if(icons<20 || text<20)throw new AssertionError("Native icon/text was erased: "+icons+" / "+text);
   }finally{OS.ReleaseDC(bar.handle,dc);}
   active[0]=false;bar.redraw();pump(display);dc=OS.GetDC(bar.handle);
   try{Rectangle bounds=item.getBounds();if(OS.GetPixel(dc,bounds.x+bounds.width-4,bounds.y+3)!=originalPixel)throw new AssertionError("Original native painting was not restored");}finally{OS.ReleaseDC(bar.handle,dc);}
   active[0]=true;requested[0]=display.getSystemColor(SWT.COLOR_BLACK);bar.redraw();pump(display);dc=OS.GetDC(bar.handle);
   try{Rectangle bounds=item.getBounds();int white=0;for(int y=bounds.y;y<bounds.y+bounds.height;y++)for(int x=bounds.x;x<bounds.x+bounds.width;x++)if(OS.GetPixel(dc,x,y)==0xFFFFFF)white++;if(white<20)throw new AssertionError("Dark custom text was not corrected for selection contrast");}finally{OS.ReleaseDC(bar.handle,dc);}
   item.setEnabled(false);bar.redraw();pump(display);if(item.getEnabled())throw new AssertionError("Disabled command enabled");
   if(shell.getData("salutem.legacyToolbarColors")==null)throw new AssertionError("Selected background hook missing");
   System.out.println("PASS: selected item pixels, native icon/text and palette; item properties and command state unchanged; native painting restored when inactive.");
  }finally{shell.dispose();icon.dispose();background.dispose();foreground.dispose();selection.dispose();display.dispose();}
 }
 private static void pump(Display display)throws Exception{long end=System.currentTimeMillis()+80;while(System.currentTimeMillis()<end){if(!display.readAndDispatch())Thread.sleep(2);}}
}
