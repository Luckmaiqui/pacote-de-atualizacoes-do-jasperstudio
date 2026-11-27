package com.salutem.order;
import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.List;import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jface.dialogs.*;import org.eclipse.swt.SWT;import org.eclipse.swt.browser.*;import org.eclipse.swt.dnd.*;import org.eclipse.swt.graphics.Point;import org.eclipse.swt.layout.*;import org.eclipse.swt.widgets.*;
/** Read, reconcile, test and add through the native Studio stores. */
final class GestaoImportDialog extends TitleAreaDialog {
 private final AccountStore store=new AccountStore();
 private final Map<Object,GestaoReconciliation.Probe> existingTests=new IdentityHashMap<>();
 private final Map<String,GestaoReconciliation.Probe> newTests=new HashMap<>();
 private final Set<String> selectedKeys=new HashSet<>();
 private final Map<GestaoCatalog.Entry,GestaoReconciliation.Assessment> assessments=new HashMap<>();
 private List<GestaoCatalog.Entry> rows=List.of();private GestaoConnections.Snapshot snapshot=new GestaoConnections.Snapshot(Map.of());
 private Table table;private Combo pattern,dbAccount,serverAccount,displayMode;private List<AccountStore.Account> dbAccounts=List.of(),serverAccounts=List.of();private Label status;
 private Display uiDisplay;private Text filterText;private AutoCloseable observation;private final AtomicBoolean refreshQueued=new AtomicBoolean();private boolean registryReady;private int warningsCount;private String lastAction="";
 GestaoImportDialog(Shell parent){super(parent);setShellStyle(getShellStyle()|SWT.RESIZE|SWT.MAX);}
 protected void configureShell(Shell shell){super.configureShell(shell);shell.setText("Importar Conexões do Gestão");shell.setData("salutem.accountSurface",Boolean.TRUE);uiDisplay=shell.getDisplay();shell.addListener(SWT.Activate,e->scheduleRefresh());shell.addListener(SWT.Dispose,e->unwatch());}
 protected Point getInitialSize(){return new Point(1100,730);}
 protected Control createDialogArea(Composite parent){
  setTitle("Importar Conexões do Gestão");setMessage("Revise as pendências. Conexões já cadastradas ficam separadas e são preservadas.");
  Composite body=(Composite)super.createDialogArea(parent);Composite area=new Composite(body,SWT.NONE);area.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));area.setLayout(new GridLayout(6,false));
  button(area,"Abrir Gestão e Ler…",this::browser);button(area,"Coletor Para o Navegador…",this::collector);button(area,"Importar JSON…",this::file);button(area,"Contas…",()->{new AccountsDialog(getShell(),store).open();accounts();});
  new Label(area,SWT.NONE).setText("Padrão:");pattern=new Combo(area,SWT.READ_ONLY);pattern.setItems(new String[]{"(000) Nome","000 - Nome"});pattern.select(0);pattern.addListener(SWT.Selection,e->render());
  new Label(area,SWT.NONE).setText("Conta de Banco:");dbAccount=new Combo(area,SWT.READ_ONLY);dbAccount.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));dbAccount.addListener(SWT.Selection,e->invalidateNew("DATABASE"));
  new Label(area,SWT.NONE).setText("Conta JasperServer:");serverAccount=new Combo(area,SWT.READ_ONLY);serverAccount.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));serverAccount.addListener(SWT.Selection,e->invalidateNew("SERVER"));
  button(area,"Marcar Testáveis",()->{for(TableItem item:table.getItems()){GestaoCatalog.Entry row=(GestaoCatalog.Entry)item.getData();if(assessments.get(row).testable())selectedKeys.add(row.key());}render();});button(area,"Desmarcar",()->{selectedKeys.clear();render();});
  Composite filter=new Composite(area,SWT.NONE);GridData fd=new GridData(SWT.FILL,SWT.CENTER,true,false);fd.horizontalSpan=6;filter.setLayoutData(fd);filter.setLayout(new GridLayout(4,false));new Label(filter,SWT.NONE).setText("Exibir:");displayMode=new Combo(filter,SWT.READ_ONLY);displayMode.setItems(new String[]{"Pendentes","Cadastradas","Todas"});displayMode.select(0);displayMode.addListener(SWT.Selection,e->render());new Label(filter,SWT.NONE).setText("Pesquisar:");filterText=new Text(filter,SWT.SEARCH|SWT.ICON_SEARCH|SWT.ICON_CANCEL);filterText.setMessage("Código, nome ou endereço…");filterText.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));filterText.addListener(SWT.Modify,e->render());
  table=new Table(area,SWT.CHECK|SWT.FULL_SELECTION|SWT.BORDER|SWT.V_SCROLL|SWT.H_SCROLL);table.setHeaderVisible(true);table.setLinesVisible(false);GridData data=new GridData(SWT.FILL,SWT.FILL,true,true);data.horizontalSpan=6;table.setLayoutData(data);
  String[] headers={"Conexão / Nome Proposto","Tipo","Endereço","Situação","Teste"};int[] widths={300,110,320,250,170};for(int n=0;n<headers.length;n++){TableColumn col=new TableColumn(table,SWT.NONE);col.setText(headers[n]);col.setWidth(widths[n]);}
  table.addListener(SWT.Selection,e->{if(e.detail==SWT.CHECK && e.item instanceof TableItem){TableItem item=(TableItem)e.item;GestaoCatalog.Entry row=(GestaoCatalog.Entry)item.getData();if(item.getChecked() && assessments.get(row).testable())selectedKeys.add(row.key());else{item.setChecked(false);selectedKeys.remove(row.key());}}});
  status=new Label(area,SWT.WRAP);GridData sd=new GridData(SWT.FILL,SWT.CENTER,true,false);sd.horizontalSpan=6;status.setLayoutData(sd);accounts();render();return body;
 }
 protected void createButtonsForButtonBar(Composite parent){Button test=createButton(parent,100,"Testar Marcados",false);test.addListener(SWT.Selection,e->test());Button apply=createButton(parent,101,"Adicionar Testadas",false);apply.addListener(SWT.Selection,e->apply());createButton(parent,IDialogConstants.CANCEL_ID,"Fechar",false);}
 private Button button(Composite parent,String text,Runnable action){Button button=new Button(parent,SWT.PUSH);button.setText(text);button.addListener(SWT.Selection,e->action.run());return button;}
 private String accountId(Combo combo,List<AccountStore.Account> accounts){int index=combo.getSelectionIndex()-1;return index>=0 && index<accounts.size()?accounts.get(index).id():"";}
 private void accounts(){try{String db=accountId(dbAccount,dbAccounts),server=accountId(serverAccount,serverAccounts);dbAccounts=store.list(AccountStore.Kind.DATABASE);serverAccounts=store.list(AccountStore.Kind.SERVER);fill(dbAccount,dbAccounts,db);fill(serverAccount,serverAccounts,server);if(!db.equals(accountId(dbAccount,dbAccounts)))invalidateNew("DATABASE");if(!server.equals(accountId(serverAccount,serverAccounts)))invalidateNew("SERVER");render();}catch(Exception error){AccountsDialog.failure(getShell());}}
 private void fill(Combo combo,List<AccountStore.Account> accounts,String selected){combo.removeAll();combo.add("— Selecione Uma Conta —");for(var account:accounts)combo.add(account.name());combo.select(0);for(int i=0;i<accounts.size();i++)if(accounts.get(i).id().equals(selected))combo.select(i+1);}
 private AccountStore.Login login(String kind)throws Exception{Combo combo=kind.equals("DATABASE")?dbAccount:serverAccount;List<AccountStore.Account> list=kind.equals("DATABASE")?dbAccounts:serverAccounts;int index=combo.getSelectionIndex()-1;if(index<0 || index>=list.size())throw new IllegalArgumentException("Selecione a Conta Para a Nova Conexão de "+(kind.equals("DATABASE")?"Banco de Dados":"JasperServer"));return store.read(list.get(index));}
 private void invalidateNew(String kind){newTests.keySet().removeIf(key->key.startsWith(kind+":"));render();}
 private void unwatch(){if(observation!=null)try{observation.close();}catch(Exception ignored){}finally{observation=null;}}
 private void scheduleRefresh(){if(uiDisplay==null || uiDisplay.isDisposed() || !refreshQueued.compareAndSet(false,true))return;uiDisplay.asyncExec(()->{refreshQueued.set(false);if(table!=null && !table.isDisposed() && !rows.isEmpty())refresh();});}
 private void refresh(){
  if(table==null || table.isDisposed())return;
  try{
   unwatch();observation=GestaoConnections.watch(this::scheduleRefresh);snapshot=GestaoConnections.snapshot();registryReady=true;setErrorMessage(null);
   Map<Object,String> fingerprints=new IdentityHashMap<>();for(var group:snapshot.entries().values())for(var entry:group)fingerprints.put(entry.object(),entry.metadata().fingerprint());
   existingTests.entrySet().removeIf(e->!Objects.equals(fingerprints.get(e.getKey()),e.getValue().fingerprint()));newTests.keySet().removeIf(snapshot.entries()::containsKey);
  }catch(RuntimeException error){registryReady=false;setErrorMessage("Não Foi Possível Atualizar os Cadastros. A Adição Está Bloqueada Para Evitar Duplicidades.");}
  render();
 }
 private void load(String json){try{
  rows=GestaoCatalog.parse(json);var warnings=GestaoCatalog.warnings(json);warningsCount=warnings.size();existingTests.clear();newTests.clear();selectedKeys.clear();lastAction="";refresh();
  for(var row:rows)if(assessments.get(row).state()==GestaoReconciliation.State.NEW)selectedKeys.add(row.key());render();
  if(!warnings.isEmpty())MessageDialog.openInformation(getShell(),"Cadastros Pendentes de Revisão",String.join("\n",warnings.subList(0,Math.min(20,warnings.size())))+(warnings.size()>20?"\nMais "+(warnings.size()-20)+" avisos no arquivo exportado.":""));
 }catch(Exception error){MessageDialog.openError(getShell(),"Importar Gestão","Não Foi Possível Ler a Exportação. Confira o Formato e a Origem do Arquivo.");}}
 private void render(){
  if(table==null || table.isDisposed())return;table.setRedraw(false);int pending=0,registered=0;assessments.clear();
  try{table.removeAll();Map<String,Set<String>> names=new HashMap<>();snapshot.entries().forEach((key,entries)->{String kind=key.substring(0,key.indexOf(':'));for(var entry:entries)names.computeIfAbsent(kind+":"+entry.metadata().name().strip().toLowerCase(Locale.ROOT),k->new HashSet<>()).add(key);});
   for(var row:rows){
    var matches=snapshot.entries().getOrDefault(row.key(),List.of());var assessment=GestaoReconciliation.assess(row,matches.stream().map(GestaoConnections.Existing::metadata).toList(),existingTests,newTests.get(row.key()));
    if(matches.isEmpty() && names.containsKey(row.kind()+":"+row.name(pattern.getSelectionIndex()==1).strip().toLowerCase(Locale.ROOT)))assessment=new GestaoReconciliation.Assessment(GestaoReconciliation.State.REVIEW,false,false,"Nome Já Utilizado por Outro Endereço — Revise no Explorador");
    assessments.put(row,assessment);if(assessment.pending())pending++;if(assessment.registered())registered++;
    if(displayMode.getSelectionIndex()==0 && !assessment.pending() || displayMode.getSelectionIndex()==1 && !assessment.registered())continue;
    String name=matches.size()==1?matches.get(0).metadata().name():row.name(pattern.getSelectionIndex()==1);
    if(filterText!=null&&!filterText.isDisposed()&&!ConnectionSearch.matches(row.code()+" "+name+" "+row.client()+" "+row.label()+" "+row.endpoint(),filterText.getText()))continue;
    TableItem item=new TableItem(table,SWT.NONE);item.setData(row);
    String test=assessment.state()==GestaoReconciliation.State.VALID || assessment.state()==GestaoReconciliation.State.READY?"OK":assessment.state()==GestaoReconciliation.State.FAILED?"Falhou":"Não Verificado";
    item.setText(new String[]{name,row.kind().equals("SERVER")?"JasperServer":"PostgreSQL",row.endpoint(),assessment.detail(),test});item.setChecked(assessment.testable() && selectedKeys.contains(row.key()));
   }
  }finally{table.setRedraw(true);}
  status.setText(rows.isEmpty()?"Leia o Gestão ou Importe o JSON. Senhas e Sessões do Site Não São Coletadas.":pending+" Pendentes; "+registered+" Cadastradas; "+warningsCount+" Avisos. Cadastros Completos Não São Novas Adições. "+lastAction);
 }

 private void file(){FileDialog dialog=new FileDialog(getShell(),SWT.OPEN);dialog.setFilterExtensions(new String[]{"*.json"});String path=dialog.open();if(path==null)return;try{Path p=Path.of(path);if(Files.size(p)>5_000_000)throw new IllegalArgumentException("Arquivo maior que 5 MB");load(Files.readString(p,StandardCharsets.UTF_8));}catch(Exception e){MessageDialog.openError(getShell(),"Importar Gestão","Não foi possível ler o JSON. Verifique o tamanho e o formato.");}}
 private String script()throws Exception{try(var in=getClass().getResourceAsStream("/gestao-export.js")){if(in==null)throw new IllegalStateException("Coletor indisponível");return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 private void collector(){try{String bookmarklet="javascript:"+java.net.URLEncoder.encode(script(),StandardCharsets.UTF_8).replace("+","%20");Clipboard clip=new Clipboard(getShell().getDisplay());try{clip.setContents(new Object[]{bookmarklet},new Transfer[]{TextTransfer.getInstance()});}finally{clip.dispose();}MessageDialog.openInformation(getShell(),"Coletor do Gestão","O endereço do coletor foi copiado.\n\n1. Crie um favorito no seu navegador, com o nome Exportar Gestão.\n2. Cole o conteúdo copiado no campo URL desse favorito.\n3. Acesse o Gestão e realize o login na lista de clientes.\n4. Clique no favorito: ele lerá as páginas e baixará conexoes-gestao.json.\n5. Use Importar JSON nesta janela.\n\nO coletor não lê senhas, cookies, tokens nem dados de SSH. Ele só funciona no domínio do Gestão.");}catch(Exception e){MessageDialog.openError(getShell(),"Coletor","Não foi possível abrir o coletor.");}}
 private void browser(){new GestaoBrowser(getShell()).open();}
 private final class GestaoBrowser extends TitleAreaDialog {
  Browser web;GestaoBrowser(Shell parent){super(parent);setShellStyle(getShellStyle()|SWT.RESIZE|SWT.MAX);}
  protected void configureShell(Shell shell){super.configureShell(shell);shell.setText("Gestão — Leitura de Conexões");shell.setData("salutem.accountSurface",Boolean.TRUE);}
  protected Point getInitialSize(){return new Point(1120,800);}
  protected Control createDialogArea(Composite p){setTitle("Acesse o Gestão e Realize o Login");setMessage("Esta sessão pode ser diferente da do seu navegador. Abra a lista de clientes e clique em Ler Conexões.");Composite body=(Composite)super.createDialogArea(p);try{web=new Browser(body,SWT.EDGE);web.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));new BrowserFunction(web,"salutemGestaoImport"){public Object function(Object[] args){if(!GestaoPagePolicy.allowed(web.getUrl()) || args.length!=1 || !(args[0] instanceof String))return false;String json=(String)args[0];getShell().getDisplay().asyncExec(()->{if(!GestaoImportDialog.this.getShell().isDisposed()){close();load(json);}});return true;}};web.setUrl("https://gestao.sistemasalutem.com.br/management");}catch(org.eclipse.swt.SWTError | org.eclipse.swt.SWTException error){Label text=new Label(body,SWT.WRAP);text.setText("O navegador Edge/WebView2 não está disponível no Jasper. Use Coletor Para o Navegador e depois Importar JSON. Nenhuma sessão do seu navegador será extraída.");text.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));}return body;}
  protected void createButtonsForButtonBar(Composite p){Button read=createButton(p,100,"Ler Conexões",true);read.addListener(SWT.Selection,e->{if(web==null || web.isDisposed())return;if(!GestaoPagePolicy.allowed(web.getUrl())){MessageDialog.openInformation(getShell(),"Login Necessário","Acesse o Gestão e realize o login na lista de clientes.");return;}try{if(!web.execute(script()))throw new IllegalStateException();}catch(Exception error){MessageDialog.openError(getShell(),"Ler Gestão","O navegador não executou o coletor. Utilize a alternativa por JSON.");}});createButton(p,IDialogConstants.CANCEL_ID,"Fechar",false);}
 }
 private record Target(GestaoCatalog.Entry row,GestaoConnections.Existing existing){}
 private List<GestaoCatalog.Entry> marked(){List<GestaoCatalog.Entry> result=new ArrayList<>();for(TableItem item:table.getItems())if(item.getChecked()){GestaoCatalog.Entry row=(GestaoCatalog.Entry)item.getData();if(assessments.get(row).testable())result.add(row);}return result;}
 private void test(){
  List<GestaoCatalog.Entry> selected=marked();if(selected.isEmpty())return;refresh();if(!registryReady)return;
  List<Target> targets=new ArrayList<>();Map<String,AccountStore.Login> logins=new HashMap<>();
  try{for(var row:selected){var assessment=assessments.get(row);if(assessment==null || !assessment.testable())continue;var matches=snapshot.entries().getOrDefault(row.key(),List.of());GestaoConnections.Existing found=matches.isEmpty()?null:matches.get(0);if(found==null && !logins.containsKey(row.kind()))logins.put(row.kind(),login(row.kind()));targets.add(new Target(row,found));if(found==null)newTests.remove(row.key());else existingTests.remove(found.object());}}catch(Exception error){logins.clear();MessageDialog.openInformation(getShell(),"Contas",Objects.toString(error.getMessage(),"Não Foi Possível Abrir a Conta Selecionada"));return;}
  boolean[] canceled={false};Map<Target,GestaoReconciliation.Probe> outcomes=new LinkedHashMap<>();
  try{new ProgressMonitorDialog(getShell()).run(true,true,monitor->{monitor.beginTask("Verificando Conexões",targets.size());try{for(Target target:targets){if(monitor.isCanceled())break;monitor.subTask(target.row().code()+" — "+target.row().label());boolean success=false;try{if(target.existing()==null)GestaoConnections.test(target.row(),logins.get(target.row().kind()),monitor);else GestaoConnections.test(target.existing(),monitor);success=true;}catch(Exception error){/* Do not expose credentials from driver exception messages. */}if(!monitor.isCanceled())outcomes.put(target,new GestaoReconciliation.Probe(target.existing()==null?target.row().key():target.existing().metadata().fingerprint(),success,success?(target.existing()==null?"Testada — Pronta Para Adicionar":"Válida — Configuração Cadastrada Verificada"):"Teste Falhou — Verifique Rede, Permissões e Credenciais"));monitor.worked(1);}}finally{canceled[0]=monitor.isCanceled();monitor.done();}});
  }catch(Exception error){canceled[0]=true;}finally{logins.clear();}
  for(var outcome:outcomes.entrySet()){Target target=outcome.getKey();if(target.existing()==null)newTests.put(target.row().key(),outcome.getValue());else existingTests.put(target.existing().object(),outcome.getValue());}
  lastAction=(canceled[0]?"Verificação Cancelada; Resultados Concluídos: ":"Verificações Concluídas: ")+outcomes.size()+". Cadastros Válidos Ficam em Cadastradas.";refresh();
 }
 private void apply(){
  List<GestaoCatalog.Entry> selected=marked();if(selected.isEmpty())return;refresh();if(!registryReady)return;List<GestaoCatalog.Entry> additions=new ArrayList<>();
  for(var row:selected){var assessment=assessments.get(row);if(assessment!=null && assessment.addable())additions.add(row);}
  if(additions.isEmpty()){MessageDialog.openInformation(getShell(),"Adicionar Conexões","Somente Novas Conexões com Teste OK Podem Ser Adicionadas. Cadastros Existentes São Preservados.");return;}
  if(!MessageDialog.openConfirm(getShell(),"Adicionar Conexões", "Adicionar "+additions.size()+" Novas Conexões Testadas? Cadastros Existentes Não Serão Renomeados, Substituídos ou Excluídos."))return;
  Map<String,AccountStore.Login> logins=new HashMap<>();List<GestaoReconciliation.Addition> results;
  try{for(var row:additions)if(!logins.containsKey(row.kind()))logins.put(row.kind(),login(row.kind()));results=GestaoConnections.addBatch(additions,pattern.getSelectionIndex()==1,logins);}catch(Exception error){MessageDialog.openError(getShell(),"Adicionar Conexões","Não Foi Possível Conferir os Cadastros ou Abrir as Contas. A Operação Foi Interrompida.");refresh();return;}finally{logins.clear();}
  int added=0,preserved=0,failed=0;
  for(var result:results){String key=result.row().key();if(result.state()==GestaoReconciliation.AddState.ADDED){added++;newTests.remove(key);selectedKeys.remove(key);}else if(result.state()==GestaoReconciliation.AddState.ALREADY_REGISTERED){preserved++;newTests.remove(key);selectedKeys.remove(key);}else{failed++;}}
  refresh();for(var result:results)if(result.state()==GestaoReconciliation.AddState.ADDED){var matches=snapshot.entries().getOrDefault(result.row().key(),List.of());if(matches.size()==1){var found=matches.get(0);existingTests.put(found.object(),new GestaoReconciliation.Probe(found.metadata().fingerprint(),true,"Válida — Testada e Cadastrada"));}}
  lastAction=added+" Adicionadas; "+preserved+" Já Existentes Preservadas; "+failed+" Falhas. Lista Atualizada Automaticamente.";render();if(failed>0)MessageDialog.openInformation(getShell(),"Itens Não Adicionados",String.join("\n",results.stream().filter(r->r.state()==GestaoReconciliation.AddState.CONFLICT || r.state()==GestaoReconciliation.AddState.FAILED).limit(10).map(r->r.row().code()+" — "+r.detail()).toList()));
 }
}
