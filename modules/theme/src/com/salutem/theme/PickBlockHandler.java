package com.salutem.theme;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.swt.widgets.Display;

/** Window > Pick a block colour: starts the on-screen block picker. */
public final class PickBlockHandler extends AbstractHandler {
 @Override public Object execute(ExecutionEvent event){
  Display display=Display.getCurrent();
  // Let the menu close before the picker takes the mouse.
  if(display!=null)display.asyncExec(()->BlockPicker.start(display));
  return null;
 }
}
