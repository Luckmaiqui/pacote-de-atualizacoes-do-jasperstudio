package com.salutem.l10n;
import java.util.*;
import org.eclipse.core.commands.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.handlers.*;

/** Selecting the active locale must not rewrite the installation's INI. */
public final class SelectedLocaleHandler extends AbstractHandler implements IElementUpdater {
 public Object execute(ExecutionEvent event)throws ExecutionException {
  if("pt_BR".equalsIgnoreCase(Platform.getNL()))return null;
  String previous=System.getProperty("eclipse.commands"),exit=System.getProperty("eclipse.exitcode"),data=System.getProperty("eclipse.exitdata");boolean restarted=false;
  try{
   if(System.getProperty("eclipse.vm")==null)throw new IllegalStateException("Abra o Studio pelo executável ou pelo atalho de Português para permitir reiniciar.");
   System.setProperty("eclipse.commands",LocaleRestartArguments.portuguese(previous));
   // Workbench.restart(true) uses the native Equinox relaunch protocol and
   // preserves the current workspace, VM and save/cancel handling. No INI write.
   restarted=HandlerUtil.getActiveWorkbenchWindowChecked(event).getWorkbench().restart(true);return null;
  }catch(Exception error){throw new ExecutionException("Não foi possível selecionar Português (Brasil).",error);}
  finally{if(!restarted){restore("eclipse.commands",previous);restore("eclipse.exitcode",exit);restore("eclipse.exitdata",data);}}
 }
 private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
 public void updateElement(UIElement element,Map parameters){element.setChecked("pt_BR".equalsIgnoreCase(Platform.getNL()));}
}
