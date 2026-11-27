package com.salutem.theme;
import java.lang.reflect.*;
import org.eclipse.swt.internal.win32.*;

/** API bridge compiled against each SWT; never replaces a Studio/SWT library. */
final class Win32Bridge {
 private static boolean usedLegacy;
 private static Object call(String name,Class<?>[] types,Object... values){
  try{return OS.class.getMethod(name,types).invoke(null,values);}catch(NoSuchMethodException absent){return null;}catch(ReflectiveOperationException error){throw new IllegalStateException("Win32: "+name,error);}
 }
 static void allowDarkModeForWindow(long handle,boolean value){if(usedLegacy){LegacyWindowsDark.window(handle,value);return;}try{OS.class.getMethod("AllowDarkModeForWindow",long.class,boolean.class);call("AllowDarkModeForWindow",new Class<?>[]{long.class,boolean.class},handle,value);}catch(NoSuchMethodException old){LegacyWindowsDark.window(handle,value);}}
 // SWT 3.115 exposes these methods but reports unavailable on this Windows.
 // Use the same bounded, per-process bridge as platforms without the API.
 static boolean isDarkModeAvailable(){Object nativeValue=call("IsDarkModeAvailable",new Class<?>[0]);if(Boolean.TRUE.equals(nativeValue))return true;usedLegacy=true;return LegacyWindowsDark.available();}
 static void setPreferredAppMode(int mode){if(usedLegacy){LegacyWindowsDark.preferred(mode);return;}try{OS.class.getMethod("SetPreferredAppMode",int.class);call("SetPreferredAppMode",new Class<?>[]{int.class},mode);}catch(NoSuchMethodException old){LegacyWindowsDark.preferred(mode);}}
 public static boolean legacyDarkActive(){return usedLegacy && LegacyWindowsDark.active();}
 public static void legacyPreferredMode(int mode){LegacyWindowsDark.preferred(mode);}
 static void drawText(long dc,char[] text,int length,RECT rectangle,int flags){
  try{OS.class.getMethod("DrawText",long.class,char[].class,int.class,RECT.class,int.class).invoke(null,dc,text,length,rectangle,flags);}
  catch(NoSuchMethodException oldSwt){try{Class<?> type=Class.forName("org.eclipse.swt.internal.win32.TCHAR");Object value=type.getConstructor(int.class,String.class,boolean.class).newInstance(0,new String(text),true);OS.class.getMethod("DrawText",long.class,type,int.class,RECT.class,int.class).invoke(null,dc,value,length,rectangle,flags);}catch(ReflectiveOperationException error){throw new IllegalStateException("SWT DrawText incompatível",error);}}
  catch(ReflectiveOperationException error){throw new IllegalStateException("SWT DrawText",error);}
 }
}
