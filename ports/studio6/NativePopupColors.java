package com.salutem.theme;

import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;

/** Studio 6.21.5: bar drop-downs remain classic even with native dark mode.
 * Own the complete row through documented WM_DRAWITEM/WM_MEASUREITEM, rather
 * than painting a second label over Windows' disabled-text shadow.
 * Native IDs, strings, commands, accessibility and tracking are retained. */
final class NativePopupColors {
 private static final String KEY="salutem.popupColors";
 private static final int OWNER=0x100,TYPE=0x100,ID=2;
 private static final NativeLibrary COMCTL=NativeLibrary.getInstance("comctl32");
 private static final Function SUBCLASS=COMCTL.getFunction("SetWindowSubclass",Function.ALT_CONVENTION),REMOVE=COMCTL.getFunction("RemoveWindowSubclass",Function.ALT_CONVENTION),DELEGATE=COMCTL.getFunction("DefSubclassProc",Function.ALT_CONVENTION);
 private final Shell shell;private final Display display;private final Callback callback;
 private final Map<Long,State> menus=new HashMap<>();private final Map<Integer,Entry> entries=new HashMap<>();
 private int depth;private boolean released,reported;
 private static final class State {final Menu menu;Supplier<Color[]> colors;long originalBrush,brush;int brushColor;boolean captured;State(Menu menu,Supplier<Color[]> colors){this.menu=menu;this.colors=colors;}}
 private static final class Entry {final State state;final MenuItem item;final int id,type;Entry(State state,MenuItem item,int id,int type){this.state=state;this.item=item;this.id=id;this.type=type;}}
 static void install(Menu menu,Supplier<Color[]> colors){
  if(menu.isDisposed()||(menu.getStyle()&SWT.DROP_DOWN)==0)return;
  Shell shell=menu.getShell();NativePopupColors hook=(NativePopupColors)shell.getData(KEY);
  if(hook==null){hook=new NativePopupColors(shell);shell.setData(KEY,hook);}
  State state=hook.menus.get(menu.handle);
  if(state==null){state=new State(menu,colors);hook.menus.put(menu.handle,state);final NativePopupColors owner=hook;final State bound=state;final long handle=menu.handle;
   menu.addListener(SWT.Dispose,e->{owner.restore(bound);owner.menus.remove(handle);});
  }else state.colors=colors;
  hook.update(state);
 }
 private NativePopupColors(Shell shell){
  this.shell=shell;display=shell.getDisplay();callback=new Callback(this,"windowProc",6);
  if(callback.getAddress()==0){callback.dispose();throw new IllegalStateException("Menu callback unavailable");}
  if(SUBCLASS.invokeInt(new Object[]{new Pointer(shell.handle),new Pointer(callback.getAddress()),callback.getAddress(),0L})==0){callback.dispose();throw new IllegalStateException("Menu owner unavailable");}
  shell.addListener(SWT.Dispose,e->close());
 }
 private void update(State state){
  if(state.menu.isDisposed())return;
  if(state.colors.get()==null){restore(state);return;}
  surface(state,false);
  for(int index=0;index<state.menu.getItemCount();index++){
   MenuItem item=state.menu.getItem(index);if((item.getStyle()&SWT.SEPARATOR)!=0)continue;
   MENUITEMINFO info=new MENUITEMINFO();info.cbSize=MENUITEMINFO.sizeof;info.fMask=TYPE|ID;
   if(!OS.GetMenuItemInfo(state.menu.handle,index,true,info))throw new IllegalStateException("Menu item unavailable");
   Entry old=entries.get(info.wID);if(old!=null&&old.item==item)continue;
   if((info.fType&OWNER)!=0)continue; // Leave another extension's owner drawing intact.
   Entry entry=new Entry(state,item,info.wID,info.fType);info.fMask=TYPE;info.fType|=OWNER;
   entries.put(entry.id,entry); // SetMenuItemInfo can synchronously request measurement.
   if(!OS.SetMenuItemInfo(state.menu.handle,index,true,info)){entries.remove(entry.id);throw new IllegalStateException("Menu style unavailable");}
   item.addListener(SWT.Dispose,e->{if(entries.get(entry.id)==entry)entries.remove(entry.id);});
  }
 }
 private void restore(State state){
  for(Entry entry:new ArrayList<>(entries.values()))if(entry.state==state){
   if(!state.menu.isDisposed()&&!entry.item.isDisposed()){MENUITEMINFO info=new MENUITEMINFO();info.cbSize=MENUITEMINFO.sizeof;info.fMask=TYPE;info.fType=entry.type;OS.SetMenuItemInfo(state.menu.handle,entry.id,false,info);}
   entries.remove(entry.id);
  }
  surface(state,true);
  if(!state.menu.isDisposed())try{for(String name:new String[]{"setBackground","setForeground"}){Method setter=Menu.class.getDeclaredMethod(name,Color.class);setter.setAccessible(true);setter.invoke(state.menu,(Object)null);}}catch(NoSuchMethodException absent){/* Legacy SWT uses MENUINFO. */}catch(ReflectiveOperationException error){throw new IllegalStateException("Menu restoration failed",error);}
 }
 private static final boolean SWT_MENU_COLORS=hasMenuColors();
 private static boolean hasMenuColors(){try{Menu.class.getDeclaredMethod("setBackground",Color.class);return true;}catch(NoSuchMethodException absent){return false;}}
 private void surface(State state,boolean restore){
  if(SWT_MENU_COLORS)return;
  MENUINFO info=new MENUINFO();info.cbSize=MENUINFO.sizeof;info.fMask=2;
  if(restore){if(state.captured&&!state.menu.isDisposed()){info.hbrBack=state.originalBrush;OS.SetMenuInfo(state.menu.handle,info);}if(state.brush!=0)OS.DeleteObject(state.brush);state.brush=0;state.captured=false;return;}
  Color[] palette=state.colors.get();if(palette==null)return;
  if(!state.captured){if(!OS.GetMenuInfo(state.menu.handle,info))throw new IllegalStateException("Menu surface unavailable");state.originalBrush=info.hbrBack;state.captured=true;}
  if(state.brush!=0&&state.brushColor==palette[0].handle)return;
  long brush=OS.CreateSolidBrush(palette[0].handle);info.hbrBack=brush;
  if(brush==0||!OS.SetMenuInfo(state.menu.handle,info)){if(brush!=0)OS.DeleteObject(brush);throw new IllegalStateException("Menu surface update failed");}
  if(state.brush!=0)OS.DeleteObject(state.brush);state.brush=brush;state.brushColor=palette[0].handle;
 }
 private long delegate(long hwnd,long message,long wParam,long lParam){return DELEGATE.invokeLong(new Object[]{new Pointer(hwnd),(int)message,wParam,lParam});}
 @SuppressWarnings("unused") long windowProc(long hwnd,long message,long wParam,long lParam,long subclassId,long data){
  boolean delegated=false;long result=0;depth++;try{
   if(!released&&lParam!=0&&message==OS.WM_DRAWITEM){DRAWITEMSTRUCT draw=new DRAWITEMSTRUCT();OS.MoveMemory(draw,lParam,DRAWITEMSTRUCT.sizeof);Entry entry=entries.get(draw.itemID);if(draw.CtlType==1&&entry!=null&&draw.hwndItem==entry.state.menu.handle&&!entry.item.isDisposed()){paint(entry,draw);return 1;}}
   if(!released&&lParam!=0&&message==OS.WM_MEASUREITEM){MEASUREITEMSTRUCT measure=new MEASUREITEMSTRUCT();OS.MoveMemory(measure,lParam,MEASUREITEMSTRUCT.sizeof);Entry entry=entries.get(measure.itemID);if(measure.CtlType==1&&entry!=null&&!entry.item.isDisposed()){measure(entry,measure);OS.MoveMemory(lParam,measure,MEASUREITEMSTRUCT.sizeof);return 1;}}
   if(!released&&message==0x120){State state=menus.get(lParam);if(state!=null&&state.colors.get()!=null){long match=mnemonic(state,(char)(wParam&0xffff));if(match!=0)return match;}}
   result=delegate(hwnd,message,wParam,lParam);delegated=true;
   if(message==0x82){release();return result;}
   // JFace can populate a menu in its Show listeners. Update after SWT has
   // delivered all of them, before Windows measures the newly created rows.
   if(!released&&message==0x117){State state=menus.get(wParam);if(state!=null)update(state);}
   return result;
  }catch(RuntimeException error){if(!reported){reported=true;System.err.println("Salutem popup: "+error);}return delegated?result:delegate(hwnd,message,wParam,lParam);}
  finally{depth--;}
 }
 private long mnemonic(State state,char key){
  List<Integer> matches=new ArrayList<>();MenuItem[] items=state.menu.getItems();int highlighted=-1;
  for(int i=0;i<items.length;i++){MenuItem item=items[i];MENUITEMINFO info=new MENUITEMINFO();info.cbSize=MENUITEMINFO.sizeof;info.fMask=1;if(OS.GetMenuItemInfo(state.menu.handle,i,true,info)&&(info.fState&0x80)!=0)highlighted=i;
   String text=item.getText();if(!item.isEnabled())continue;for(int n=0;n+1<text.length();n++)if(text.charAt(n)=='&'){char next=text.charAt(++n);if(next!='&'&&Character.toLowerCase(next)==Character.toLowerCase(key)){matches.add(i);break;}}
  }
  if(matches.isEmpty())return 0;int index=matches.get(0);for(int match:matches)if(match>highlighted){index=match;break;}
  return (index&0xffffL)|((matches.size()==1?2L:3L)<<16);
 }
 private static String[] parts(MenuItem item){String text=item.getText();int split=text.indexOf('\t');return split<0?new String[]{text,""}:new String[]{text.substring(0,split),text.substring(split+1)};}
 private void measure(Entry entry,MEASUREITEMSTRUCT size){
  long dc=OS.GetDC(shell.handle),saved=OS.SaveDC(dc);try{
   OS.SelectObject(dc,display.getSystemFont().handle);String[] text=parts(entry.item);RECT label=new RECT(),shortcut=new RECT();
   OS.DrawText(dc,text[0].toCharArray(),text[0].length(),label,0x420);OS.DrawText(dc,text[1].toCharArray(),text[1].length(),shortcut,0xc20);
   Image image=entry.item.getImage();int imageHeight=image==null?0:DPIUtil.autoScaleUp(image.getBounds().height);
   size.itemWidth=Math.max(DPIUtil.autoScaleUp(90),label.right+shortcut.right+DPIUtil.autoScaleUp(text[1].isEmpty()?56:80));size.itemHeight=Math.max(label.bottom,imageHeight)+DPIUtil.autoScaleUp(8);
  }finally{OS.RestoreDC(dc,(int)saved);OS.ReleaseDC(shell.handle,dc);}
 }
 private static RECT rect(int left,int top,int right,int bottom){RECT r=new RECT();r.left=left;r.top=top;r.right=right;r.bottom=bottom;return r;}
 private void paint(Entry entry,DRAWITEMSTRUCT draw){
  Color[] palette=entry.state.colors.get();if(palette==null)palette=new Color[]{display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND),display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND),display.getSystemColor(SWT.COLOR_DARK_GRAY),display.getSystemColor(SWT.COLOR_LIST_SELECTION)};
  boolean enabled=entry.item.isEnabled()&&(draw.itemState&6)==0;Color background=enabled&&(draw.itemState&0x41)!=0?palette[3]:palette[0],foreground=CustomColors.readable(enabled?palette[1]:palette[2],background);
  int saved=OS.SaveDC(draw.hDC);long brush=OS.CreateSolidBrush(background.handle);
  try{
   RECT area=rect(draw.left,draw.top,draw.right,draw.bottom);OS.FillRect(draw.hDC,area,brush);
   int gutter=DPIUtil.autoScaleUp(28),pad=DPIUtil.autoScaleUp(10);Image image=entry.item.getImage();
   boolean checked=(entry.item.getStyle()&(SWT.CHECK|SWT.RADIO))!=0&&entry.item.getSelection();
   if(image!=null&&!checked){Image drawn=enabled?image:new Image(display,image,SWT.IMAGE_DISABLE);GCData data=new GCData();data.device=display;GC gc=GC.win32_new(draw.hDC,data);try{gc.drawImage(drawn,DPIUtil.autoScaleDown(draw.left+pad/2),DPIUtil.autoScaleDown(draw.top+(draw.bottom-draw.top-DPIUtil.autoScaleUp(image.getBounds().height))/2));}finally{gc.dispose();if(drawn!=image)drawn.dispose();}}
   OS.SelectObject(draw.hDC,display.getSystemFont().handle);OS.SetBkMode(draw.hDC,1);OS.SetTextColor(draw.hDC,foreground.handle); // TRANSPARENT, not OPAQUE
   int flags=0x24|((draw.itemState&0x100)!=0?0x100000:0);String[] text=parts(entry.item);
   if(checked){String mark=(entry.item.getStyle()&SWT.RADIO)!=0?"\u2022":"\u2713";OS.DrawText(draw.hDC,mark.toCharArray(),mark.length(),rect(draw.left+pad/2,draw.top,draw.left+gutter,draw.bottom),0x825);}
   OS.DrawText(draw.hDC,text[0].toCharArray(),text[0].length(),rect(draw.left+gutter,draw.top,draw.right-pad,draw.bottom),flags);
   if(!text[1].isEmpty())OS.DrawText(draw.hDC,text[1].toCharArray(),text[1].length(),rect(draw.left+gutter,draw.top,draw.right-pad,draw.bottom),0x826);
   if(entry.item.getMenu()!=null)OS.DrawText(draw.hDC,new char[]{'\u203a'},1,rect(draw.right-pad,draw.top,draw.right,draw.bottom),0x825);
  }finally{OS.DeleteObject(brush);OS.RestoreDC(draw.hDC,saved);}
 }
 private void close(){if(released)return;REMOVE.invokeInt(new Object[]{new Pointer(shell.handle),new Pointer(callback.getAddress()),callback.getAddress()});release();}
 private void release(){if(released)return;released=true;for(State state:new ArrayList<>(menus.values()))restore(state);entries.clear();menus.clear();if(depth==0)callback.dispose();else if(!display.isDisposed())display.asyncExec(callback::dispose);}
}
