package com.salutem.theme;
import com.sun.jna.*;
import java.lang.reflect.*;
import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.win32.*;

/** Focused native regression: sample only the popup owned by this test JVM. */
public final class NativePopupTest {
 static final NativeLibrary USER=NativeLibrary.getInstance("user32");
 static int id(Menu menu,int index){MENUITEMINFO info=new MENUITEMINFO();info.cbSize=MENUITEMINFO.sizeof;info.fMask=2;if(!OS.GetMenuItemInfo(menu.handle,index,true,info))throw new AssertionError("No item ID");return info.wID;}
 static int type(Menu menu,int index){MENUITEMINFO info=new MENUITEMINFO();info.cbSize=MENUITEMINFO.sizeof;info.fMask=0x100;OS.GetMenuItemInfo(menu.handle,index,true,info);return info.fType;}
 static void colors(Menu menu,Color bg,Color fg)throws Exception{for(String name:new String[]{"setBackground","setForeground"}){Method m;try{m=Menu.class.getDeclaredMethod(name,Color.class);}catch(NoSuchMethodException absent){continue;}m.setAccessible(true);m.invoke(menu,name.equals("setBackground")?bg:fg);}}
 static void checkPixels(Shell shell,Menu menu,Color[] palette)throws Exception{
  Pointer hwnd=(Pointer)USER.getFunction("FindWindowW",Function.ALT_CONVENTION).invoke(Pointer.class,new Object[]{new WString("#32768"),null});
  if(hwnd==null)throw new AssertionError("Native popup absent");long handle=Pointer.nativeValue(hwnd);int[] pid={0};OS.GetWindowThreadProcessId(handle,pid);
  if(pid[0]!=OS.GetCurrentProcessId())throw new AssertionError("Foreign popup: no pixels read");
  RECT row=new RECT(),window=new RECT();if(!OS.GetMenuItemRect(shell.handle,menu.handle,1,row)||!OS.GetWindowRect(handle,window))throw new AssertionError("No native geometry");
  long dc=OS.GetDC(handle);try{
   int background=OS.GetPixel(dc,row.right-window.left-12,(row.top+row.bottom)/2-window.top);
   if(background!=palette[0].handle)throw new AssertionError("Wrong disabled row background: "+Integer.toHexString(background));
   int white=0,ink=0,lightBoxes=0;int nativeFace=OS.GetSysColor(OS.COLOR_MENU);for(int y=row.top-window.top+2;y<row.bottom-window.top-2;y++)for(int x=row.left-window.left+30;x<row.right-window.left-20;x++){int pixel=OS.GetPixel(dc,x,y);if(pixel==0xffffff)white++;if(pixel==nativeFace&&pixel!=palette[0].handle)lightBoxes++;if(pixel!=palette[0].handle)ink++;}
   if(white!=0)throw new AssertionError("Disabled text still has a white embossed shadow: "+white);
   if(lightBoxes!=0)throw new AssertionError("Label still has an opaque light background: "+lightBoxes);
   if(ink<15)throw new AssertionError("Disabled label missing");
  }finally{OS.ReleaseDC(handle,dc);}
 }
 static void open(Display display,Shell shell,Menu menu,boolean bar,Color[] palette)throws Exception{
  final boolean[] finished={false};final Throwable[] error={null};
  display.timerExec(150,()->{try{checkPixels(shell,menu,palette);}catch(Throwable e){error[0]=e;}finally{USER.getFunction("EndMenu",Function.ALT_CONVENTION).invokeInt(new Object[0]);finished[0]=true;}});
  OS.PostMessage(shell.handle,0,0,0);if(bar)OS.SendMessage(shell.handle,OS.WM_SYSCOMMAND,0xF100,'f');else{menu.setLocation(100,100);menu.setVisible(true);}
  while(!finished[0]){if(!display.readAndDispatch())display.sleep();}
  if(error[0]!=null)throw new AssertionError(error[0]);
 }
 public static void main(String[] args)throws Exception{
  Display d=new Display();Shell shell=new Shell(d);shell.setText("Synthetic menu regression");shell.setBounds(40,40,520,220);
  Image icon=new Image(d,16,16);GC gc=new GC(icon);gc.setBackground(d.getSystemColor(SWT.COLOR_MAGENTA));gc.fillRectangle(icon.getBounds());gc.dispose();
  Color[][] active={null};final int[] commands={0};
  try{
   Menu bar=new Menu(shell,SWT.BAR);shell.setMenuBar(bar);MenuItem file=new MenuItem(bar,SWT.CASCADE);file.setText("&File");Menu dropdown=new Menu(shell,SWT.DROP_DOWN);file.setMenu(dropdown);Menu context=new Menu(shell,SWT.POP_UP);
   for(Menu menu:new Menu[]{dropdown,context}){MenuItem normal=new MenuItem(menu,SWT.PUSH);normal.setText("&Salvar\tCtrl+S");normal.setImage(icon);normal.addListener(SWT.Selection,e->commands[0]++);MenuItem disabled=new MenuItem(menu,SWT.PUSH);disabled.setText("&Suspenso");disabled.setEnabled(false);MenuItem check=new MenuItem(menu,SWT.CHECK);check.setText("&Marcar");check.setSelection(true);}
   int original=id(dropdown,0);shell.open();OS.SendMessage(shell.handle,OS.WM_COMMAND,original,0);OS.PostMessage(shell.handle,0,0,0);while(d.readAndDispatch()){}if(commands[0]!=1)throw new AssertionError("Native fixture command did not initialize");commands[0]=0;
   for(ThemePalette p:ThemePalette.all()){System.out.println("Palette: "+p.id);
    Color bg=new Color(d,(p.background>>16)&255,(p.background>>8)&255,p.background&255),fg=new Color(d,(p.foreground>>16)&255,(p.foreground>>8)&255,p.foreground&255),muted=new Color(d,(p.muted>>16)&255,(p.muted>>8)&255,p.muted&255),selection=new Color(d,36,90,134);
    try{
     active[0]=new Color[]{bg,fg,muted,selection};colors(bar,bg,fg);
     for(Menu menu:new Menu[]{dropdown,context}){
      if(menu==dropdown)colors(menu,bg,fg);NativePopupColors.install(menu,()->active[0]);NativePopupColors.install(menu,()->active[0]);
      if(menu==dropdown){if(OS.SendMessage(shell.handle,0x120,'s',menu.handle)!=0x20000)throw new AssertionError("Mnemonic lost");open(d,shell,menu,true,active[0]);}
      else if((type(menu,0)&0x100)!=0)throw new AssertionError("Native context style altered");
      if(!menu.getItem(0).getText().equals("&Salvar\tCtrl+S")||menu.getItem(1).getEnabled()||!menu.getItem(2).getSelection())throw new AssertionError("Menu state changed");
     }
    }finally{active[0]=null;for(Menu menu:new Menu[]{dropdown,context})NativePopupColors.install(menu,()->active[0]);bg.dispose();fg.dispose();muted.dispose();selection.dispose();}
   }
   if(id(dropdown,0)!=original||(type(dropdown,0)&0x100)!=0)throw new AssertionError("ID/type not restored");
   OS.SendMessage(shell.handle,OS.WM_COMMAND,original,0);OS.PostMessage(shell.handle,0,0,0);while(d.readAndDispatch()){}
   if(commands[0]!=1||dropdown.getItem(0).getImage()!=icon)throw new AssertionError("Native command/image lost: commands="+commands[0]+" iconSame="+(dropdown.getItem(0).getImage()==icon)+" id="+original);
   Object hook=shell.getData("salutem.popupColors");Field callback=hook.getClass().getDeclaredField("callback");callback.setAccessible(true);shell.dispose();while(d.readAndDispatch()){}if(((Callback)callback.get(hook)).getAddress()!=0)throw new AssertionError("Callback not released");
   System.out.println("PASS: native bar menus in seven palettes and context-item state; dark backgrounds; disabled labels without white shadow; commands, IDs, strings, images and check state preserved; styles restored and callback released.");
  }finally{if(!shell.isDisposed())shell.dispose();icon.dispose();d.dispose();}
 }
}
