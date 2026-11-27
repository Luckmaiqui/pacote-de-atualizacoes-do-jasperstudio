package com.salutem.theme;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.internal.win32.*;
import java.lang.reflect.Field;
import org.eclipse.swt.internal.Callback;

/** Native pixel regression, no Studio workspace or credentials. */
public final class LegacyToolbarTest {
 public static void main(String[] args)throws Exception{
  Display display=new Display();Color background=new Color(display,37,38,56),foreground=new Color(display,230,233,244),selection=new Color(display,36,90,134);
  Shell shell=new Shell(display);shell.setSize(400,180);
  try{
   ToolBar bar=new ToolBar(shell,SWT.FLAT);bar.setBounds(10,10,350,35);bar.setBackground(background);bar.setForeground(foreground);
   ToolItem item=new ToolItem(bar,SWT.CHECK);item.setText("Console Erros Estatísticas");item.setSelection(true);
   shell.open();pump(display);int before=readablePixels(bar);
   LegacyToolbarColors.install(bar,()->foreground,()->selection);bar.redraw();pump(display);
   int after=readablePixels(bar);
   if(after<20)throw new AssertionError("Legacy toolbar text has no readable foreground pixels: "+before+" -> "+after);
   long dc=OS.GetDC(bar.handle);
   try{Rectangle bounds=item.getBounds();if(OS.GetPixel(dc,bounds.x+bounds.width-4,bounds.y+3)!=selection.handle)throw new AssertionError("Selected item kept a light native background");}finally{OS.ReleaseDC(bar.handle,dc);}
   if(!item.getSelection() || !item.getText().equals("Console Erros Estatísticas"))throw new AssertionError("Toolbar item state changed");
   Object hook=shell.getData("salutem.legacyToolbarColors");
   LegacyToolbarColors.install(bar,()->foreground,()->selection);
   if(hook!=shell.getData("salutem.legacyToolbarColors"))throw new AssertionError("Duplicate parent hook");
   Field callback=LegacyToolbarColors.class.getDeclaredField("callback");callback.setAccessible(true);Callback bridge=(Callback)callback.get(hook);
   LaterHook later=new LaterHook(shell.handle);
   shell.dispose();pump(display);if(bridge.getAddress()!=0)throw new AssertionError("Toolbar callback not released when another extension owns the chain");later.callback.dispose();
   for(int i=0;i<100;i++){
    Shell temporary=new Shell(display);ToolBar child=new ToolBar(temporary,SWT.FLAT);new ToolItem(child,SWT.PUSH).setText("Teste");
    LegacyToolbarColors.install(child,()->foreground,()->selection);temporary.dispose();pump(display);
   }
   System.out.println("PASS: toolbar text pixels="+before+" -> "+after+"; selection/text unchanged; unique hook; callback released; 100 parent disposals.");
  }finally{if(!shell.isDisposed())shell.dispose();background.dispose();foreground.dispose();selection.dispose();display.dispose();}
 }
 private static final class LaterHook {
  final Callback callback;final long previous;
  LaterHook(long handle){callback=new Callback(this,"windowProc",4);previous=OS.SetWindowLongPtr(handle,OS.GWLP_WNDPROC,callback.getAddress());}
  long windowProc(long handle,long message,long wParam,long lParam){return OS.CallWindowProc(previous,handle,(int)message,wParam,lParam);}
 }
 private static int readablePixels(ToolBar bar){
  long dc=OS.GetDC(bar.handle);int count=0;
  try{Rectangle bounds=bar.getItem(0).getBounds();for(int y=bounds.y+4;y<bounds.y+bounds.height-4;y++)for(int x=bounds.x+4;x<bounds.x+bounds.width-4;x++){
   int color=OS.GetPixel(dc,x,y);int r=color&255,g=(color>>>8)&255,b=(color>>>16)&255;
   if(Math.abs(r-230)<=4 && Math.abs(g-233)<=4 && Math.abs(b-244)<=4)count++;
  }}finally{OS.ReleaseDC(bar.handle,dc);}return count;
 }
 private static void pump(Display display)throws Exception{long end=System.currentTimeMillis()+40;while(System.currentTimeMillis()<end){if(!display.readAndDispatch())Thread.sleep(2);}}
}
