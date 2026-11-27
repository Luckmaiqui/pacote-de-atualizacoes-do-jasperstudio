package com.salutem.tests;
import java.lang.reflect.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.graphics.Color;

/** Equivalent assertions for old SWT, which has no newer Display fields.
 * Native-rendering pixels are checked separately; these check configured state. */
public final class LegacyAssertions {
 public static boolean dark(Display display,String name)throws Exception{
  if(!nativeDarkAvailable()){Method method=Class.forName("com.salutem.theme.Win32Bridge").getDeclaredMethod("legacyDarkActive");method.setAccessible(true);return Boolean.TRUE.equals(method.invoke(null));}
  try{Field field=Display.class.getDeclaredField(name);field.setAccessible(true);return field.getBoolean(display);}
  catch(NoSuchFieldException old){Method method=Class.forName("com.salutem.theme.Win32Bridge").getDeclaredMethod("legacyDarkActive");method.setAccessible(true);return Boolean.TRUE.equals(method.invoke(null));}
 }
 public static int disabledForeground(Display display)throws Exception{
  try{Field field=Display.class.getDeclaredField("disabledLabelForegroundPixel");field.setAccessible(true);return field.getInt(display);}
  catch(NoSuchFieldException old){Object color=display.getData("org.eclipse.swt.internal.win32.Label.disabledForegroundColor");if(!(color instanceof Color))throw new AssertionError("Cor desabilitada não configurada");return ((Color)color).handle;}
 }
 public static void setDark(Display display,String name,boolean value)throws Exception{
  if(!nativeDarkAvailable()){Method method=Class.forName("com.salutem.theme.Win32Bridge").getDeclaredMethod("legacyPreferredMode",int.class);method.setAccessible(true);method.invoke(null,value?2:0);return;}
  try{Field field=Display.class.getDeclaredField(name);field.setAccessible(true);field.setBoolean(display,value);}
  catch(NoSuchFieldException old){Method method=Class.forName("com.salutem.theme.Win32Bridge").getDeclaredMethod("legacyPreferredMode",int.class);method.setAccessible(true);method.invoke(null,value?2:0);}
 }
 private static boolean nativeDarkAvailable()throws Exception{try{return Boolean.TRUE.equals(Class.forName("org.eclipse.swt.internal.win32.OS").getMethod("IsDarkModeAvailable").invoke(null));}catch(NoSuchMethodException absent){return false;}}
}
