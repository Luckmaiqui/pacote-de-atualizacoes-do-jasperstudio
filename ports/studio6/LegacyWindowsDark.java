package com.salutem.theme;
import com.sun.jna.*;

/** Per-process bridge to the same Windows dark-mode entry points used by newer SWT.
 * Restricted to x64 Windows build 18362+; never changes registry/system settings. */
final class LegacyWindowsDark {
 private static final Function preferred,window;
 private static volatile boolean active;
 static {
  Function app=null,hwnd=null;
  try {
   if(Native.POINTER_SIZE==8 && System.getProperty("os.name","").startsWith("Windows")){
    Memory version=new Memory(284);version.clear();version.setInt(0,284);
    int status=NativeLibrary.getInstance("ntdll").getFunction("RtlGetVersion",Function.ALT_CONVENTION).invokeInt(new Object[]{version});
    if(status==0 && version.getInt(4)>=10 && version.getInt(12)>=18362){
     NativeLibrary kernel=NativeLibrary.getInstance("kernel32");
     Pointer module=(Pointer)kernel.getFunction("LoadLibraryW",Function.ALT_CONVENTION).invoke(Pointer.class,new Object[]{new WString("uxtheme.dll")});
     Function address=kernel.getFunction("GetProcAddress",Function.ALT_CONVENTION);
     Pointer a=(Pointer)address.invoke(Pointer.class,new Object[]{module,Pointer.createConstant(135)});
     Pointer b=(Pointer)address.invoke(Pointer.class,new Object[]{module,Pointer.createConstant(133)});
     if(a!=null && b!=null){app=Function.getFunction(a,Function.ALT_CONVENTION);hwnd=Function.getFunction(b,Function.ALT_CONVENTION);}
    }
   }
  }catch(RuntimeException|LinkageError error){System.err.println("Salutem legacy dark mode unavailable: "+error.getClass().getSimpleName());}
  preferred=app;window=hwnd;
 }
 static boolean available(){return preferred!=null && window!=null;}
 static void preferred(int mode){if(available()){int previous=preferred.invokeInt(new Object[]{mode});if(previous<0 || previous>4)throw new IllegalStateException("Windows dark-mode response invalid");active=mode==2;}}
 static void window(long handle,boolean dark){if(available())window.invokeInt(new Object[]{Pointer.createConstant(handle),dark?1:0});}
 static boolean active(){return available() && active;}
}
