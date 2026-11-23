package com.salutem.order;
import org.eclipse.core.commands.*;import org.eclipse.ui.handlers.HandlerUtil;
public final class GestaoImportHandler extends AbstractHandler {
 public Object execute(ExecutionEvent event)throws ExecutionException{new GestaoImportDialog(HandlerUtil.getActiveShell(event)).open();return null;}
}
