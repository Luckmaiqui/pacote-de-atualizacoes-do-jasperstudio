package com.salutem.order;
import java.util.*;import java.util.List;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicInteger;import java.util.function.BooleanSupplier;
import org.eclipse.swt.SWT;import org.eclipse.swt.widgets.*;import org.eclipse.swt.layout.FillLayout;
import org.eclipse.jface.viewers.*;import org.eclipse.core.resources.*;import org.eclipse.core.runtime.Path;
import com.jaspersoft.studio.data.*;import com.jaspersoft.studio.data.noda.NoDataAdapterDescriptor;
import com.jaspersoft.studio.server.model.server.*;import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;
import org.eclipse.jdt.core.IJavaProject;

public class CheckExplorerSearch {
 public static final class NativeAPI {
  static Object selected,result=new Object();static int reveals;
  public static Object doFindResource(MServerProfile server,String[] include,String[] exclude){selected=server;return result;}
  public static void doFindResource(Object provider,TreeViewer viewer){throw new AssertionError("Não usar a API dependente da seleção da árvore");}
  private static void selectResource(Object provider,MServerProfile server,Object resource,TreeViewer viewer){if(server!=selected || resource!=result)throw new AssertionError("Resultado errado");reveals++;}
 }
 public static final class ConnectingServer {
  Object client;int connects;
  public Object getWsClient(){return client;}
  public Object getWsClient(org.eclipse.core.runtime.IProgressMonitor monitor){connects++;client=new Object();return client;}
 }
 static MDataAdapter adapter(String name){return new MDataAdapter(null,new NoDataAdapterDescriptor(){public String getTitle(){return name;}});}
 static MServerProfile server(String name){ServerProfile p=new ServerProfile();p.setName(name);return new MServerProfile(null,p){public JasperReportsConfiguration getJasperConfiguration(){return null;}};}
 static TreeViewer viewer(Composite parent,Object input,Map<Object,List<Object>> nodes){
  TreeViewer v=new TreeViewer(parent,SWT.NONE);v.setContentProvider(new ITreeContentProvider(){
   public Object[] getElements(Object n){return getChildren(n);}public Object[] getChildren(Object n){return nodes.getOrDefault(n,List.of()).toArray();}
   public boolean hasChildren(Object n){return nodes.containsKey(n);}public Object getParent(Object n){for(var e:nodes.entrySet())if(e.getValue().contains(n))return e.getKey();return null;}
  });v.setLabelProvider(new LabelProvider(){public String getText(Object n){return n instanceof com.jaspersoft.studio.model.ANode?((com.jaspersoft.studio.model.ANode)n).getDisplayText():n instanceof IResource?((IResource)n).getName():String.valueOf(n);}});v.setInput(input);v.expandToLevel(2);return v;
 }
 static Text search(TreeViewer v){Composite wrapper=v.getTree().getParent();for(Control c:wrapper.getChildren())if(c instanceof Composite && c!=v.getTree())for(Control x:((Composite)c).getChildren())if(x instanceof Text)return (Text)x;throw new AssertionError("Campo ausente");}
 static Text searchProject(Composite parent){for(Control c:parent.getChildren()){if(c instanceof Text)return (Text)c;if(c instanceof Composite){Text t=searchProject((Composite)c);if(t!=null)return t;}}return null;}
 static Label status(TreeViewer v){if(v.getTree().getData("salutem.explorerSearch")!=null)try{Field f=ExplorerSearch.class.getDeclaredField("status");f.setAccessible(true);return (Label)f.get(v.getTree().getData("salutem.explorerSearch"));}catch(Exception e){throw new AssertionError(e);}for(Control c:v.getTree().getParent().getChildren())if(c instanceof Label)return (Label)c;throw new AssertionError("Status ausente");}
 static void pump(Display d,BooleanSupplier done){long timeout=System.currentTimeMillis()+5000;while(!done.getAsBoolean() && System.currentTimeMillis()<timeout)if(!d.readAndDispatch())try{Thread.sleep(5);}catch(InterruptedException e){throw new AssertionError(e);}if(!done.getAsBoolean())throw new AssertionError("Timeout da busca");}
 static void waitUI(Display d,int millis){long end=System.currentTimeMillis()+millis;while(System.currentTimeMillis()<end)if(!d.readAndDispatch())try{Thread.sleep(5);}catch(InterruptedException e){throw new AssertionError(e);}}
 public static void main(String[]args)throws Exception{
  ConnectingServer unconnected=new ConnectingServer();org.eclipse.core.runtime.NullProgressMonitor monitor=new org.eclipse.core.runtime.NullProgressMonitor();
  if(NativeServerSearch.client(unconnected)!=null || !NativeServerSearch.connect(unconnected,monitor) || unconnected.connects!=1)throw new AssertionError("Servidor não inicializado");
  NativeServerSearch.connect(unconnected,monitor);if(unconnected.connects!=1)throw new AssertionError("Reconectou servidor inicializado");
  ConnectingServer canceled=new ConnectingServer();monitor.setCanceled(true);if(NativeServerSearch.connect(canceled,monitor) || canceled.connects!=0)throw new AssertionError("Conectou após cancelar");
  Display d=new Display();Shell shell=new Shell(d);shell.setLayout(new FillLayout());shell.setSize(1200,700);
  try{
   Composite left=new Composite(shell,SWT.NONE);left.setLayout(new FillLayout());Composite right=new Composite(shell,SWT.NONE);right.setLayout(new FillLayout());
   Object root="root",banks="Bancos",servers="Servidores",bank14=adapter("(014) São José"),bank2=adapter("(002) Hospital"),server14=server("(014) São José Server"),server2=server("(002) Outro Server");
   Map<Object,List<Object>> nodes=new IdentityHashMap<>();nodes.put(root,List.of(banks,servers));nodes.put(banks,List.of(bank14,bank2));nodes.put(servers,List.of(server14,server2));
   TreeViewer repo=viewer(left,root,nodes);repo.setSelection(new StructuredSelection(bank2));AtomicInteger remote=new AtomicInteger();
   ExplorerSearch.attach(repo,true,remote::incrementAndGet);ExplorerSearch.install(d);shell.open();Text field=search(repo);
   if(repo.getTree().getParent()==left || left.getChildren().length!=1)throw new AssertionError("Layout FillLayout quebrado");
   field.setText("014");pump(d,()->status(repo).getText().isEmpty());TreeItem[] groups=repo.getTree().getItems();
   if(groups[0].getItemCount()!=1 || groups[1].getItemCount()!=1 || groups[0].getItem(0).getData()!=bank14 || groups[1].getItem(0).getData()!=server14)throw new AssertionError("Código não trouxe banco e servidor");
   field.setText("sao jose");pump(d,()->status(repo).getText().isEmpty());if(groups[0].getItemCount()!=1 || groups[1].getItemCount()!=1)throw new AssertionError("Acentos");
   field.setText("");if(repo.getStructuredSelection().getFirstElement()!=bank2 || !groups[0].getExpanded())throw new AssertionError("Limpar perdeu seleção/expansão");
   repo.setSelection(StructuredSelection.EMPTY);Event key=new Event();key.keyCode='f';key.stateMask=SWT.MOD1;repo.getTree().notifyListeners(SWT.KeyDown,key);pump(d,()->remote.get()==1);
   repo.setSelection(new StructuredSelection(server2));if(ExplorerSearch.selectedServer(repo)!=server2)throw new AssertionError("Servidor selecionado ignorado");
   MDataAdapter descendant=adapter("Arquivo");descendant.setParent((com.jaspersoft.studio.model.ANode)server2,-1);nodes.put(server2,List.of(descendant));repo.refresh();repo.setSelection(new StructuredSelection(descendant),true);
   if(ExplorerSearch.selectedServer(repo)!=server2)throw new AssertionError("Servidor ancestral ignorado");
   nodes.remove(server2);repo.refresh();repo.setSelection(StructuredSelection.EMPTY);
   var allTargets=List.of(new GlobalSearchEngine.Target(server2,"Mesmo Nome"),new GlobalSearchEngine.Target(server14,"Mesmo Nome"));
   if(GlobalSearchDialog.selectedScope(allTargets,List.of(allTargets.get(1)),1).get(0).connection()!=server14 || GlobalSearchDialog.selectedScope(allTargets,allTargets,1).size()!=2 || GlobalSearchDialog.selectedScope(allTargets,List.of(),0).size()!=2 || GlobalSearchDialog.selectedScope(allTargets,List.of(),1).size()!=0 || GlobalSearchDialog.selectedScope(allTargets,List.of(),2).get(0).connection()!=server2)throw new AssertionError("Escopo múltiplo ou nomes repetidos");
   GlobalSearchDialog.show(null,repo,List.of(server2,server14),server14);
   Field currentDialog=GlobalSearchDialog.class.getDeclaredField("current");currentDialog.setAccessible(true);GlobalSearchDialog dialog=(GlobalSearchDialog)currentDialog.get(null);
   Field scopeField=GlobalSearchDialog.class.getDeclaredField("scope");scopeField.setAccessible(true);Combo scope=(Combo)scopeField.get(dialog);
   if(scope.getSelectionIndex()!=3)throw new AssertionError("Contexto não selecionado na janela");
   GlobalSearchDialog.show(null,repo,List.of(server2,server14),null);if(scope.getSelectionIndex()!=0)throw new AssertionError("Sem contexto não voltou para todos");dialog.close();
   if(remote.get()!=1)throw new AssertionError("Ctrl+F sem seleção");
   for(Control c:field.getParent().getChildren())if(c instanceof Button)((Button)c).notifyListeners(SWT.Selection,new Event());
   if(remote.get()!=2)throw new AssertionError("Botão de busca do servidor");
   field.setText("014");pump(d,()->status(repo).getText().isEmpty());ExplorerSearch.selectServer(repo,server2);
   if(!field.getText().isEmpty() || repo.getStructuredSelection().getFirstElement()!=server2)throw new AssertionError("Filtro impediu escolher outro servidor");
   Object cmp=repo.getTree().getData("salutem.explorerSearch");ExplorerSearch.attach(repo,true,remote::incrementAndGet);if(cmp!=repo.getTree().getData("salutem.explorerSearch"))throw new AssertionError("Instalação duplicada");
   Map<String,IResource> resources=new LinkedHashMap<>();Map<Object,List<Object>> projectNodes=new IdentityHashMap<>();AtomicInteger visits=new AtomicInteger();
   for(String name:List.of("/Projeto","/Projeto/Relatorios","/Projeto/Relatorios/Ficha Padrão.jrxml","/Projeto/Relatorios/Outro.jrxml")){
    Class<?> type=name.equals("/Projeto")?IProject.class:name.endsWith(".jrxml")?IFile.class:IFolder.class;
    IResource resource=(IResource)Proxy.newProxyInstance(CheckExplorerSearch.class.getClassLoader(),new Class[]{type},(proxy,method,a)->{
     switch(method.getName()){
      case "getFullPath":return new Path(name);case "getName":return new Path(name).lastSegment();case "isOpen":case "exists":case "isAccessible":return true;
      case "equals":return proxy==a[0];case "hashCode":return System.identityHashCode(proxy);case "toString":return name;
      case "getAdapter":return null;
     }return method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;
    });resources.put(name,resource);
   }
   IWorkspaceRoot workspace=(IWorkspaceRoot)Proxy.newProxyInstance(CheckExplorerSearch.class.getClassLoader(),new Class[]{IWorkspaceRoot.class},(proxy,method,a)->{
    if(method.getName().equals("accept")){IResourceVisitor visitor=(IResourceVisitor)a[0];for(IResource r:resources.values()){visits.incrementAndGet();visitor.visit(r);}return null;}
    if(method.getName().equals("findMember"))return resources.get(a[0].toString());
    if(method.getName().equals("getFullPath"))return Path.ROOT;
    if(method.getName().equals("equals"))return proxy==a[0];if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);return null;
   });
   IResource project=resources.get("/Projeto"),folder=resources.get("/Projeto/Relatorios"),match=resources.get("/Projeto/Relatorios/Ficha Padrão.jrxml"),other=resources.get("/Projeto/Relatorios/Outro.jrxml");
   projectNodes.put(workspace,List.of(project));projectNodes.put(project,List.of(folder));projectNodes.put(folder,List.of(match,other));
   IJavaProject javaProject=(IJavaProject)Proxy.newProxyInstance(CheckExplorerSearch.class.getClassLoader(),new Class[]{IJavaProject.class},(proxy,method,a)->{
    switch(method.getName()){case "getResource":case "getProject":return project;case "getElementName":return "MyReports";case "getAdapter":return null;case "equals":return proxy==a[0];case "hashCode":return System.identityHashCode(proxy);case "toString":return "MyReports";}
    return method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;
   });
   if(ExplorerSearch.resource(javaProject)!=project)throw new AssertionError("Projeto Java não adaptado");
   projectNodes.put(workspace,List.of(javaProject));projectNodes.put(javaProject,List.of(folder));projectNodes.remove(project);
   TreeViewer projects=viewer(right,workspace,projectNodes);projects.setSelection(new StructuredSelection(other));org.eclipse.jface.action.ToolBarManager toolbar=new org.eclipse.jface.action.ToolBarManager();toolbar.createControl(right);
   Composite originalParent=projects.getTree().getParent();ExplorerSearch.attachProject(projects,toolbar,()->workspace);
   if(projects.getTree().getParent()!=originalParent)throw new AssertionError("CommonNavigator foi reparentado");
   Text projectField=searchProject(toolbar.getControl());projects.collapseAll();ISelection selectionBefore=projects.getSelection();
   field.setText("014");pump(d,()->status(repo).getText().isEmpty());
   projectField.setText("ficha padrao");pump(d,()->status(projects).getText().contains("1 "));
   TreeItem files=projects.getTree().getItem(0).getItem(0);if(files.getItemCount()!=1 || files.getItem(0).getData()!=match)throw new AssertionError("Arquivo e ancestrais");
   if(!field.getText().equals("014") || groups[0].getItemCount()!=1 || groups[1].getItemCount()!=1)throw new AssertionError("Filtro de projeto alterou repositório");
   repo.setSelection(StructuredSelection.EMPTY);Object found=NativeServerSearch.open(NativeAPI.class,server2);
   if(NativeAPI.selected!=server2 || !repo.getSelection().isEmpty() || !field.getText().equals("014") || !projectField.getText().equals("ficha padrao"))throw new AssertionError("Busca nativa dependeu da árvore ou alterou filtros");
   NativeServerSearch.reveal(NativeAPI.class,new Object(),server2,found,repo);if(NativeAPI.reveals!=1)throw new AssertionError("Não encaminhou resultado");
   Throwable real=new ClassCastException("causa real");if(NativeServerSearch.cause(new InvocationTargetException(new InvocationTargetException(real)))!=real)throw new AssertionError("Exceção externa não desembrulhada");
   projectField.setText("inexistente");projectField.setText("outro");pump(d,()->status(projects).getText().contains("1 ") && files.getItemCount()==1 && files.getItem(0).getData()==other);
   projectField.setText("");if(!projects.getSelection().equals(selectionBefore) || projects.getTree().getItemCount()!=1)throw new AssertionError("Restauração do projeto");
   if(!field.getText().equals("014") || groups[0].getItemCount()!=1 || groups[1].getItemCount()!=1)throw new AssertionError("Limpar projeto alterou repositório");
   if(visits.get()>8 || remote.get()!=2)throw new AssertionError("Buscas repetidas ou acesso remoto pelo filtro local");
   projectField.setText("Projeto/Relatorios");pump(d,()->status(projects).getText().contains("3 "));
   projectField.setText("");projects.getTree().dispose();waitUI(d,300);
   System.out.println("PASS: barras nos dois exploradores; banco+server por código; acentos; nomes/caminhos; ancestralidade; debounce; restauração; Ctrl+F sem seleção; nenhuma pesquisa remota automática.");
  }finally{shell.dispose();d.dispose();}
 }
}
