package com.salutem.order;
import org.eclipse.core.commands.*;
import org.eclipse.ui.handlers.HandlerUtil;
public final class AccountsHandler extends AbstractHandler {
 public Object execute(ExecutionEvent event)throws ExecutionException{try{new AccountsDialog(HandlerUtil.getActiveShell(event)).open();}catch(RuntimeException error){AccountsDialog.failure(HandlerUtil.getActiveShell(event));}return null;}
}
