package com.salutem.theme;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.win32.OS;

/** Old SWT has no disabled-foreground setting. Paint labels without embossing,
 * preserving their value, font, enabled state and original native style. */
final class LegacyLabelPaint {
 static void update(Label label,boolean enabled){
  final String key="salutem.legacyDisabledPaint";
  Object existing=label.getData(key);
  if(enabled){
   if(existing instanceof Listener){label.removeListener(SWT.Paint,(Listener)existing);label.setData(key,null);Object style=label.getData(key+".style");if(style instanceof Integer)OS.SetWindowLong(label.handle,OS.GWL_STYLE,(Integer)style);label.setData(key+".style",null);label.redraw();}
   return;
  }
  if(existing!=null || (label.getStyle()&SWT.SEPARATOR)!=0)return;
  int original=OS.GetWindowLong(label.handle,OS.GWL_STYLE);
  label.setData(key+".style",original);
  OS.SetWindowLong(label.handle,OS.GWL_STYLE,(original&~0x1F)|OS.SS_OWNERDRAW);
  Listener paint=event->{
   if(label.isDisposed() || label.isEnabled())return;
   GC gc=event.gc;Point extent=label.getSize();Rectangle area=new Rectangle(0,0,extent.x,extent.y);gc.setBackground(label.getBackground());gc.fillRectangle(area);gc.setForeground(label.getForeground());gc.setFont(label.getFont());
   Image image=label.getImage();
   if(image!=null && !image.isDisposed()){Rectangle size=image.getBounds();int x=label.getAlignment()==SWT.RIGHT?Math.max(0,area.width-size.width):label.getAlignment()==SWT.CENTER?Math.max(0,(area.width-size.width)/2):0;gc.drawImage(image,x,Math.max(0,(area.height-size.height)/2));}
   else{TextLayout layout=new TextLayout(label.getDisplay());try{layout.setFont(label.getFont());layout.setText(label.getText().replace("&&","\u0000").replace("&","").replace('\u0000','&'));if((label.getStyle()&SWT.WRAP)!=0 && area.width>0)layout.setWidth(area.width);layout.setAlignment(label.getAlignment());layout.draw(gc,0,0);}finally{layout.dispose();}}
  };
  label.setData(key,paint);label.addListener(SWT.Paint,paint);label.redraw();
 }
}
