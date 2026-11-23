package com.salutem.order;
import java.nio.file.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.ui.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.gef.EditPart;
import com.jaspersoft.studio.model.ANode;

final class SubreportOpener {
 void install(Display display){display.addFilter(SWT.Selection,event->{
  if(!(event.widget instanceof MenuItem) || !PlatformUI.isWorkbenchRunning())return;
  Object data=event.widget.getData();if(!(data instanceof ActionContributionItem))return;
  var action=((ActionContributionItem)data).getAction();if(!action.getClass().getName().equals("com.jaspersoft.studio.editor.action.OpenEditorAction"))return;
  IWorkbenchWindow window=PlatformUI.getWorkbench().getActiveWorkbenchWindow();if(window==null || window.getActivePage()==null)return;
  Object node=null;try{var method=org.eclipse.gef.ui.actions.SelectionAction.class.getDeclaredMethod("getSelectedObjects");method.setAccessible(true);var selected=(java.util.List<?>)method.invoke(action);if(selected.size()==1)node=selected.get(0);}catch(ReflectiveOperationException ignored){}
  if(node==null){var selection=window.getSelectionService().getSelection();if(!(selection instanceof IStructuredSelection) || ((IStructuredSelection)selection).size()!=1)return;node=((IStructuredSelection)selection).getFirstElement();}
  if(node instanceof EditPart)node=((EditPart)node).getModel();ANode selectedModel=node instanceof ANode?(ANode)node:null;if(node instanceof ANode)node=((ANode)node).getValue();
  if(node==null || !node.getClass().getName().endsWith("JRDesignSubreport"))return;
  String expression;try{Object expr=node.getClass().getMethod("getExpression").invoke(node);if(expr==null)return;expression=(String)expr.getClass().getMethod("getText").invoke(expr);}catch(ReflectiveOperationException error){return;}
  if(SubreportPaths.sourceName(expression)==null)return;
  if(selectedModel!=null && RemoteSubreportOpener.open(window,selectedModel,expression)){event.type=SWT.None;return;}
  IEditorPart editor=window.getActivePage().getActiveEditor();if(editor==null)return;IEditorInput input=editor.getEditorInput();java.net.URI uri=SubreportInputs.localUri(input);
  if(uri==null){System.err.println("Salutem Sub-Relatório: entrada sem caminho local: "+input.getClass().getName());return;}
  Path source=SubreportPaths.resolve(Path.of(uri),expression);if(source==null)return;
  // Consume only the native subreport action we can resolve, before the original
  // async action tries to evaluate the same expression against a compiled file.
  event.type=SWT.None;
  display.asyncExec(()->{try{
   Path chosen=source;if(!Files.isRegularFile(chosen)){FileDialog browse=new FileDialog(window.getShell(),SWT.OPEN);browse.setText("Localizar Fonte do Sub-Relatório: "+source.getFileName());browse.setFilterExtensions(new String[]{"*.jrxml"});browse.setFilterPath(source.getParent().toString());browse.setFileName(source.getFileName().toString());String selected=browse.open();if(selected==null)return;chosen=Path.of(selected);}
   if(!chosen.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jrxml"))throw new IllegalArgumentException("Selecione um fonte JRXML");
   IDE.openEditorOnFileStore(window.getActivePage(),EFS.getStore(chosen.toUri()));
  }catch(Exception error){org.eclipse.jface.dialogs.MessageDialog.openError(window.getShell(),"Abrir Sub-Relatório","Não foi possível abrir o fonte JRXML do sub-relatório. Verifique se o arquivo está disponível na pasta do relatório principal.");}});
 });}
}
