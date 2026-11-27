package com.salutem.theme;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;

/** SWT 3.106 does not implement ToolBar item text colours in NM_CUSTOMDRAW.
 * Supply the documented NMTBCUSTOMDRAW colours; Windows still renders icons,
 * text, focus, disabled state, hit testing and accessibility exactly once.
 * https://learn.microsoft.com/windows/win32/api/commctrl/ns-commctrl-nmtbcustomdraw
 */
final class LegacyToolbarColors {
 private static final String KEY="salutem.legacyToolbarColors";
 private static final int PREPAINT=1,ITEMPREPAINT=0x10001,NOTIFYITEMDRAW=0x20,NEWFONT=2;
 private final Composite parent;
 private final Display display;
 private static final class Paint {
  final ToolBar bar;final Supplier<Color> foreground,selection;
  Paint(ToolBar bar,Supplier<Color> foreground,Supplier<Color> selection){this.bar=bar;this.foreground=foreground;this.selection=selection;}
 }
 private final Map<Long,Paint> colors=new HashMap<>();
 private final Callback callback;
 private final long previous;
 private int depth;
 private boolean released;
 static void install(ToolBar bar,Supplier<Color> color,Supplier<Color> selection){
  if(bar.isDisposed())return;
  if(Native.POINTER_SIZE!=8)return;
  boolean hasText=false;for(ToolItem item:bar.getItems())if(!item.getText().isEmpty()){hasText=true;break;}
  if(!hasText)return;
  Composite parent=bar.getParent();
  LegacyToolbarColors hook=(LegacyToolbarColors)parent.getData(KEY);
  if(hook==null){hook=new LegacyToolbarColors(parent);parent.setData(KEY,hook);}
  hook.colors.put(bar.handle,new Paint(bar,color,selection));
  if(bar.getData(KEY)==null){
   bar.setData(KEY,Boolean.TRUE);final LegacyToolbarColors selected=hook;final long handle=bar.handle;
   bar.addListener(SWT.Dispose,e->selected.colors.remove(handle));
  }
 }
 private LegacyToolbarColors(Composite parent){
  this.parent=parent;this.display=parent.getDisplay();callback=new Callback(this,"windowProc",4);
  if(callback.getAddress()==0){callback.dispose();throw new IllegalStateException("Legacy toolbar callback unavailable");}
  previous=OS.SetWindowLongPtr(parent.handle,OS.GWLP_WNDPROC,callback.getAddress());
  if(previous==0){callback.dispose();throw new IllegalStateException("Legacy toolbar parent unavailable");}
  parent.addListener(SWT.Dispose,e->close());
 }
 @SuppressWarnings("unused") long windowProc(long hwnd,long message,long wParam,long lParam){
  depth++;
  try{
   long result=OS.CallWindowProc(previous,hwnd,(int)message,wParam,lParam);
   if(message==0x0082){release();return result;} // WM_NCDESTROY: later hooks may still own the head.
   if(!released && message==OS.WM_NOTIFY && lParam!=0){
    NMHDR header=new NMHDR();OS.MoveMemory(header,lParam,NMHDR.sizeof);
    Paint paint=colors.get(header.hwndFrom);
    if(header.code==OS.NM_CUSTOMDRAW && paint!=null && !paint.bar.isDisposed()){
     Color color=paint.foreground.get();
     if(color!=null && !color.isDisposed()){
      NMCUSTOMDRAW draw=new NMCUSTOMDRAW();OS.MoveMemory(draw,lParam,NMCUSTOMDRAW.sizeof);
      if(draw.dwDrawStage==PREPAINT)return result|NOTIFYITEMDRAW;
      if(draw.dwDrawStage==ITEMPREPAINT){
       int index=(int)OS.SendMessage(paint.bar.handle,OS.TB_COMMANDTOINDEX,draw.dwItemSpec,0);
       if(index<0 || index>=paint.bar.getItemCount() || paint.bar.getItem(index).getText().isEmpty())return result;
       // NMCUSTOMDRAW, then three native brush/pen handles, then COLORREFs.
       long offset=NMCUSTOMDRAW.sizeof+3L*Native.POINTER_SIZE;
       Pointer nativeDraw=new Pointer(lParam);
       Color selection=paint.selection.get();
       Color background=(paint.bar.getItem(index).getSelection() || (draw.uItemState&(1|8|64))!=0) && selection!=null && !selection.isDisposed()?selection:paint.bar.getBackground();
       color=CustomColors.readable(color,background);
       nativeDraw.setInt(offset,color.handle);
       nativeDraw.setInt(offset+8,color.handle); // clrTextHighlight
       RECT area=new RECT();area.left=draw.left;area.top=draw.top;area.right=draw.right;area.bottom=draw.bottom;
       long brush=OS.CreateSolidBrush(background.handle);
       try{OS.FillRect(draw.hdc,area,brush);}finally{OS.DeleteObject(brush);}
       // Keep native icon/text rendering, suppress only its light button face.
       return result|NEWFONT|0x00800000|0x00400000|0x00010000; // USECDCOLORS, NOBACKGROUND, NOEDGES
      }
     }
    }
   }
   return result;
  }finally{depth--;}
 }
 private void close(){
  if(released)return;
  long current=OS.GetWindowLongPtr(parent.handle,OS.GWLP_WNDPROC);
  if(current!=0 && current!=callback.getAddress())return;
  if(current==callback.getAddress())OS.SetWindowLongPtr(parent.handle,OS.GWLP_WNDPROC,previous);
  release();
 }
 private void release(){
  if(released)return;released=true;colors.clear();
  if(depth==0)callback.dispose();else if(!display.isDisposed())display.asyncExec(callback::dispose);
 }
}
