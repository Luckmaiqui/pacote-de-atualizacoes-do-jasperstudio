package com.salutem.order;
import java.lang.reflect.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Composite;

/** Only optional metadata uses an absent marker; functional APIs must fail clearly. */
final class StudioApi {
 static Object optionalMetadata(Object target,String name){
  try{return target.getClass().getMethod(name).invoke(target);}catch(NoSuchMethodException absent){return "not-in-platform:"+name;}catch(ReflectiveOperationException error){throw new IllegalStateException("Não foi possível consultar "+name,error);}
 }
 static boolean hasEdge(){try{SWT.class.getField("EDGE");return true;}catch(NoSuchFieldException absent){return false;}}
 static Browser browser(Composite parent){
  final int edge;try{edge=SWT.class.getField("EDGE").getInt(null);}catch(ReflectiveOperationException absent){throw new SWTError("WebView2 não é suportado por esta plataforma; utilize o coletor externo e Importar JSON.");}
  return new Browser(parent,edge);
 }
}
