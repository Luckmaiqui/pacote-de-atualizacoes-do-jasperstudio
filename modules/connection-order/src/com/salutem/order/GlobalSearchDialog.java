package com.salutem.order;

import java.util.*;
import java.util.List;
import java.lang.reflect.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import com.jaspersoft.studio.model.ANode;
import com.jaspersoft.studio.repository.RepositoryView;

final class GlobalSearchDialog extends Dialog {
 private static GlobalSearchDialog current;
 private final RepositoryView view;private final TreeViewer viewer;private final List<GlobalSearchEngine.Target> targets;
 private final List<GlobalSearchEngine.Hit> hits=new ArrayList<>();
 private Object initialServer;private List<GlobalSearchEngine.Target> selectedTargets=new ArrayList<>();
 private Text query;private Combo scope;private Table table,connections;private Label status;private Button cancel;
 private GlobalSearchEngine.Session session;private int generation,completed,failed,total;private Job locating;
 static void show(RepositoryView view,TreeViewer viewer,List<Object> servers,Object selectedServer){
  if(current!=null && current.getShell()!=null && !current.getShell().isDisposed()){current.applyContext(selectedServer);current.getShell().forceActive();return;}
  current=new GlobalSearchDialog(view,viewer,servers);current.initialServer=selectedServer;current.open();
 }
 private GlobalSearchDialog(RepositoryView view,TreeViewer viewer,List<Object> servers){
  super(viewer.getTree().getShell());this.view=view;this.viewer=viewer;
  targets=servers.stream().map(s->new GlobalSearchEngine.Target(s,((ANode)s).getDisplayText())).toList();
  setShellStyle((getShellStyle() & ~(SWT.APPLICATION_MODAL|SWT.PRIMARY_MODAL|SWT.SYSTEM_MODAL))|SWT.RESIZE);setBlockOnOpen(false);
 }
 protected void configureShell(Shell shell){super.configureShell(shell);shell.setText("Pesquisar em todos os JasperServers");}
 protected Point getInitialSize(){return new Point(1000,650);}
 protected Control createDialogArea(Composite parent){
  Composite area=(Composite)super.createDialogArea(parent);area.setLayout(new GridLayout(1,false));
  Label hint=new Label(area,SWT.WRAP);hint.setText("Busca nomes e descrições dos recursos nos servidores. A pesquisa começa ao clicar em Pesquisar (mínimo: 3 caracteres).");hint.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  Composite bar=new Composite(area,SWT.NONE);bar.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));bar.setLayout(new GridLayout(5,false));
  query=new Text(bar,SWT.SEARCH|SWT.ICON_SEARCH|SWT.ICON_CANCEL|SWT.BORDER);query.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));query.setMessage("Ex.: ficha padrão");
  scope=new Combo(bar,SWT.READ_ONLY);scope.add("Todos os Servidores ("+targets.size()+")");scope.add("Servidores Selecionados…");for(var t:targets)scope.add(t.name());scope.select(0);applyContext(initialServer);
  GridData scopeSize=new GridData(SWT.FILL,SWT.CENTER,false,false);scopeSize.widthHint=250;scope.setLayoutData(scopeSize);
  scope.addListener(SWT.Selection,e->{if(scope.getSelectionIndex()==1)chooseServers();});
  Button choose=new Button(bar,SWT.PUSH);choose.setText("Escolher Servidores…");choose.addListener(SWT.Selection,e->chooseServers());
  Button search=new Button(bar,SWT.PUSH);search.setText("Pesquisar");search.addListener(SWT.Selection,e->search());
  cancel=new Button(bar,SWT.PUSH);cancel.setText("Cancelar pesquisa");cancel.setEnabled(false);cancel.addListener(SWT.Selection,e->{stop();status.setText("Pesquisa cancelada; resultados já recebidos foram mantidos.");});
  query.addListener(SWT.DefaultSelection,e->search());
  TabFolder tabs=new TabFolder(area,SWT.NONE);tabs.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
  table=new Table(tabs,SWT.FULL_SELECTION|SWT.BORDER|SWT.VIRTUAL);table.setHeaderVisible(true);
  columns(table,new String[]{"Servidor","Arquivo","Tipo","Caminho"},new int[]{230,220,120,380});
  TabItem results=new TabItem(tabs,SWT.NONE);results.setText("Resultados");results.setControl(table);
  table.addListener(SWT.SetData,e->{TableItem item=(TableItem)e.item;int i=table.indexOf(item);if(i>=0 && i<hits.size()){var h=hits.get(i);item.setText(new String[]{h.target().name(),h.label(),h.type(),h.uri()});}});
  table.addListener(SWT.DefaultSelection,e->reveal());
  connections=new Table(tabs,SWT.FULL_SELECTION|SWT.BORDER);connections.setHeaderVisible(true);columns(connections,new String[]{"Servidor","Estado / erro"},new int[]{300,600});
  TabItem state=new TabItem(tabs,SWT.NONE);state.setText("Conexões");state.setControl(connections);
  status=new Label(area,SWT.WRAP);status.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));status.setText("Nenhum servidor será consultado até iniciar a pesquisa. No máximo 2 consultas simultâneas.");
  return area;
 }
 private static void columns(Table table,String[] names,int[] widths){for(int i=0;i<names.length;i++){TableColumn column=new TableColumn(table,SWT.NONE);column.setText(names[i]);column.setWidth(widths[i]);}}
 protected void createButtonsForButtonBar(Composite parent){createButton(parent,100,"Localizar na árvore",false);createButton(parent,IDialogConstants.CLOSE_ID,"Fechar",false);}
 protected void buttonPressed(int id){if(id==100)reveal();else close();}
 private void stop(){generation++;if(session!=null)session.cancel();if(cancel!=null && !cancel.isDisposed())cancel.setEnabled(false);}
 public boolean close(){stop();if(locating!=null)locating.cancel();return super.close();}
 private void search(){
  String requested=query.getText().strip();if(requested.length()<3){status.setText("Digite pelo menos 3 caracteres para pesquisar.");return;}
  List<GlobalSearchEngine.Target> selected=selectedScope(targets,selectedTargets,scope.getSelectionIndex());
  if(selected.isEmpty()){status.setText("Selecione pelo menos um servidor em Escolher Servidores…");return;}
  stop();int token=generation;hits.clear();table.setItemCount(0);connections.removeAll();completed=failed=0;
  total=selected.size();
  Map<GlobalSearchEngine.Target,TableItem> rows=new HashMap<>();for(var target:selected){TableItem item=new TableItem(connections,SWT.NONE);item.setText(new String[]{target.name(),"Aguardando resposta…"});rows.put(target,item);}
  cancel.setEnabled(true);status.setText("Consultando "+total+" servidores…");Display display=getShell().getDisplay();
  session=GlobalSearchEngine.search(selected,requested,new GlobalServerBackend(),update->display.asyncExec(()->{
   if(getShell()==null || getShell().isDisposed() || token!=generation)return;
   completed++;String message;
   if(update.error()!=null){failed++;message=update.error().getClass().getSimpleName()+": "+Objects.toString(update.error().getMessage(),"Erro de conexão");}
   else{int selectedIndex=table.getSelectionIndex();GlobalSearchEngine.Hit selectedHit=selectedIndex>=0 && selectedIndex<hits.size()?hits.get(selectedIndex):null;message=update.hits().size()+" resultados";hits.addAll(update.hits());hits.sort((a,b)->{int c=new NumericOrder().compare(a.target().name(),b.target().name());return c!=0?c:a.label().compareToIgnoreCase(b.label());});table.deselectAll();table.setItemCount(hits.size());table.clearAll();if(selectedHit!=null)table.setSelection(hits.indexOf(selectedHit));}
   rows.get(update.target()).setText(1,message);status.setText(completed+"/"+total+" servidores concluídos · "+hits.size()+" resultados · "+failed+" falhas (detalhes em Conexões)");if(completed==total)cancel.setEnabled(false);
  }));
 }
 static List<GlobalSearchEngine.Target> selectedScope(List<GlobalSearchEngine.Target> all,List<GlobalSearchEngine.Target> selected,int index){
  if(index==0)return all;if(index==1)return List.copyOf(selected);return index>=2 && index-2<all.size()?List.of(all.get(index-2)):List.of();
 }
 private void applyContext(Object server){
  if(scope==null || scope.isDisposed())return;
  for(int i=0;i<targets.size();i++)if(targets.get(i).connection()==server){scope.select(i+2);return;}
  scope.select(0);
 }
 private void chooseServers(){
  ListSelectionDialog dialog=new ListSelectionDialog(getShell(),targets,ArrayContentProvider.getInstance(),new LabelProvider(){public String getText(Object value){return ((GlobalSearchEngine.Target)value).name();}},"Marque os servidores que deseja consultar juntos.");
  dialog.setTitle("Selecionar Servidores");dialog.setInitialSelections(selectedTargets.toArray());
  if(dialog.open()==org.eclipse.jface.window.Window.OK){selectedTargets=new ArrayList<>();for(Object value:dialog.getResult())selectedTargets.add((GlobalSearchEngine.Target)value);scope.setItem(1,"Servidores Selecionados ("+selectedTargets.size()+")");scope.select(1);}
 }
 private void reveal(){
  if(locating!=null && locating.getState()!=Job.NONE)return;
  int index=table.getSelectionIndex();if(index<0 || index>=hits.size())return;var hit=hits.get(index);Display display=getShell().getDisplay();
  status.setText("Localizando o recurso e abrindo sua pasta…");
  locating=new Job("Localizar recurso do JasperServer"){
   protected IStatus run(IProgressMonitor monitor){try{
    if(!NativeServerSearch.connect(hit.target().connection(),monitor))return Status.CANCEL_STATUS;
    ClassLoader loader=hit.target().connection().getClass().getClassLoader();Class<?> helper=Class.forName("com.jaspersoft.studio.server.WSClientHelper",true,loader);Object descriptor=null;
    for(Method method:helper.getMethods())if(method.getName().equals("toResourceDescriptor") && method.getParameterCount()==2 && method.getParameterTypes()[0].isInstance(hit.target().connection()) && method.getParameterTypes()[1].isInstance(hit.nativeValue())){descriptor=method.invoke(null,hit.target().connection(),hit.nativeValue());break;}
    if(descriptor==null)throw new IllegalStateException("Não foi possível localizar o recurso");
    Object found=null;for(Method method:helper.getMethods())if(method.getName().equals("findSelected") && method.getParameterCount()==3){found=method.invoke(null,monitor,descriptor,hit.target().connection());break;}
    if(monitor.isCanceled())return Status.CANCEL_STATUS;if(found==null)throw new IllegalStateException("O recurso não foi encontrado na árvore do servidor.");
    Object resource=found;
    Field field=RepositoryView.class.getDeclaredField("rprovs");field.setAccessible(true);Object provider=null;for(Object p:(Iterable<?>)field.get(view))if(p.getClass().getName().equals("com.jaspersoft.studio.server.ServerProvider")){provider=p;break;}
    if(provider==null)throw new IllegalStateException("Provedor JasperServer indisponível");Object nativeProvider=provider;
    display.asyncExec(()->{if(viewer.getTree().isDisposed() || getShell()==null || getShell().isDisposed())return;try{NativeServerSearch.refreshForReveal(viewer,nativeProvider,()->ExplorerSearch.selectServer(viewer,hit.target().connection()));NativeServerSearch.revealAndClose(viewer,resource,()->close());}catch(Exception error){showError(error);}});
   }catch(Exception error){display.asyncExec(()->showError(error));}return Status.OK_STATUS;}
  };locating.schedule();
 }
 private void showError(Throwable error){if(getShell()!=null && !getShell().isDisposed())MessageDialog.openError(getShell(),"JasperStudio",Objects.toString(NativeServerSearch.cause(error).getMessage(),"Erro ao localizar recurso"));}
}
