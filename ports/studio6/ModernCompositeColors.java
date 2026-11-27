package com.salutem.theme;
import java.lang.reflect.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.*;

/** Version-specific vendor composite; hidden children are reached by its public API.
 * Only read inheritance state: never change a font, selection or inherited value. */
final class ModernCompositeColors {
 private static final String KEY="salutem.modernCompositeAccess";
 private static Class<?> vendorType(Class<?> type){
  for(Class<?> current=type;current!=null;current=current.getSuperclass())if(current.getName().equals("net.sf.jasperreports.eclipse.ui.JSSTableCombo"))return current;
  return null;
 }
 static boolean supports(Control control){return vendorType(control.getClass())!=null;}
 private static void rows(Table table){
  for(TableItem item:table.getItems())for(int column=0;column<Math.max(1,table.getColumnCount());column++){
   Color current=item.getForeground(column),readable=CustomColors.readable(current,item.getBackground(column));
   if(!current.equals(readable))item.setForeground(column,readable);
  }
 }
 private static final class Access {
  final Method text,table;final Field inherited;
  Access(Class<?> type)throws ReflectiveOperationException{
   type=vendorType(type);if(type==null)throw new NoSuchFieldException("JSSTableCombo base type");
   text=type.getMethod("getTextControl");table=type.getMethod("getTable");
   inherited=type.getDeclaredField("isInherited");inherited.setAccessible(true);
  }
 }
 static void apply(Control control,Color background,Color foreground,Color muted){
  try{
   Object cached=control.getData(KEY);Access access=cached instanceof Access?(Access)cached:new Access(control.getClass());
   if(cached!=access)control.setData(KEY,access);
   control.setBackground(background);control.setForeground(foreground);
   StyledText input=(StyledText)access.text.invoke(control);Table popup=(Table)access.table.invoke(control);
   // setBackground/layout may intentionally show a selected colour swatch.
   // Keep that vendor background and adjust only the text for its contrast.
   input.setForeground(CustomColors.readable(access.inherited.getBoolean(control)?muted:foreground,input.getBackground()));
   popup.setBackground(background);popup.setForeground(foreground);
   rows(popup);
   if(popup.getData(KEY)==null){
    popup.setData(KEY,Boolean.TRUE);Shell shell=popup.getShell();
    // The vendor repopulates its sample cells when opening the popup.
    Listener shown=event->popup.getDisplay().asyncExec(()->{if(!popup.isDisposed())rows(popup);});
    shell.addListener(SWT.Show,shown);
    popup.addListener(SWT.Dispose,event->{if(!shell.isDisposed())shell.removeListener(SWT.Show,shown);});
   }
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Studio 6.21.5 composite API differs from the tested profile",error);}
 }
}
