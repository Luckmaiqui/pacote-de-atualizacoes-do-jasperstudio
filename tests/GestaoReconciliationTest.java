import com.salutem.order.*;
import com.salutem.order.GestaoReconciliation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class GestaoReconciliationTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static GestaoCatalog.Entry row(String host){return new GestaoCatalog.Entry("001","Cliente","Hospitalar","DATABASE","jdbc:postgresql://"+host+":8002/salutem_hospitalar","");}
 static Registered record(String name,Configuration state){return new Registered(new Object(),name,"version-1",state,"Revisão Necessária");}
 static Assessment assess(GestaoCatalog.Entry row,List<Registered> entries,Map<Object,Probe> tests,Probe pending){return GestaoReconciliation.assess(row,entries,tests,pending);}
 static final class Registry implements GestaoReconciliation.Registry {
  final Map<String,List<Registered>> data=new HashMap<>();final AtomicInteger reads=new AtomicInteger(),writes=new AtomicInteger();String fail="";
  public Map<String,List<Registered>> snapshot(){reads.incrementAndGet();return data;}
  public Registered add(GestaoCatalog.Entry row,String name)throws Exception{writes.incrementAndGet();if(row.endpoint().contains(fail) && !fail.isEmpty())throw new Exception("sensitive error details");Thread.sleep(2);Registered created=record(name,Configuration.COMPLETE);data.put(row.key(),List.of(created));return created;}
 }
 public static void main(String[]args)throws Exception{
  var source=row("example.invalid");var registered=record("Meu Nome Manual",Configuration.COMPLETE);Map<Object,Probe> probes=new IdentityHashMap<>();
  check(assess(source,List.of(),probes,null).state()==State.NEW,"Ausente não aparece como nova");
  check(assess(source,List.of(),probes,new Probe(source.key(),true,"OK")).addable(),"Nova testada não pode ser adicionada");
  var found=assess(source,List.of(registered),probes,new Probe(source.key(),true,"teste com outra conta"));check(found.state()==State.REGISTERED && !found.pending() && !found.addable(),"Cadastro completo foi confundido com pendência ou teste de conta temporária");
  probes.put(registered.identity(),new Probe("version-1",true,"OK"));check(assess(source,List.of(registered),probes,null).state()==State.VALID,"Teste nativo OK não reconhecido");
  probes.put(registered.identity(),new Probe("version-1",false,"Falhou"));check(assess(source,List.of(registered),probes,null).pending() && !assess(source,List.of(registered),probes,null).addable(),"Cadastro inválido pode ser duplicado");
  var edited=new Registered(registered.identity(),registered.name(),"version-2",Configuration.COMPLETE,"");check(assess(source,List.of(edited),probes,null).state()==State.REGISTERED,"Teste antigo reutilizado após editar configuração");
  check(assess(source,List.of(record("Incompleta",Configuration.INCOMPLETE)),probes,null).state()==State.INCOMPLETE,"Incompleta escondida");
  check(assess(source,List.of(record("Driver Incorreto",Configuration.INVALID)),probes,null).state()==State.REVIEW,"Driver incompatível aceito");
  var multiple=assess(source,List.of(registered,record("Outra Conta",Configuration.COMPLETE)),probes,null);check(!multiple.pending() && !multiple.testable() && !multiple.addable(),"Cadastros legítimos com opções distintas viraram nova adição");
  var invalidSource=new GestaoCatalog.Entry("001","Cliente","Desconhecido","DATABASE",source.endpoint(),"Revisão da Origem");check(assess(invalidSource,List.of(),probes,null).state()==State.REVIEW,"Origem inválida ignorada");
  check(GestaoCatalog.key("SERVER","HTTPS://EXAMPLE.invalid:443/jasperserver/").equals(GestaoCatalog.key("SERVER","https://example.invalid/jasperserver")),"Porta padrão do servidor não normalizada");
  check(GestaoCatalog.key("DATABASE","jdbc:postgresql://EXAMPLE.invalid:8002/salutem_hospitalar?ssl=true").equals(source.key()),"Opções JDBC criaram falsa ausência");
  check(GestaoCatalog.serverUrl("https://example.invalid/meu%20jasper/").endsWith("/meu%20jasper"),"URL escapada foi codificada duas vezes");
  Registry existing=new Registry();existing.data.put(source.key(),List.of(registered));var keep=GestaoReconciliation.addAbsent(List.of(source),true,existing);check(keep.get(0).state()==AddState.ALREADY_REGISTERED && existing.writes.get()==0 && registered.name().equals("Meu Nome Manual"),"Importação alterou ou duplicou cadastro existente");
  Registry batch=new Registry();List<GestaoCatalog.Entry> many=new ArrayList<>();for(int i=0;i<100;i++)many.add(new GestaoCatalog.Entry(String.format("%03d",i),"Cliente "+i,"Hospitalar","DATABASE","jdbc:postgresql://host"+i+".invalid:8002/salutem_hospitalar",""));many.add(many.get(0));var added=GestaoReconciliation.addAbsent(many,false,batch);check(batch.reads.get()==1 && batch.writes.get()==100 && added.get(100).state()==AddState.ALREADY_REGISTERED,"Batch repete leituras ou adiciona duplicata interna");
  Registry conflict=new Registry();conflict.data.put(row("other.invalid").key(),List.of(record(source.name(false),Configuration.COMPLETE)));check(GestaoReconciliation.addAbsent(List.of(source),false,conflict).get(0).state()==AddState.CONFLICT && conflict.writes.get()==0,"Colisão de nome sobrescreveu outro endereço");
  Registry kinds=new Registry();var server=new GestaoCatalog.Entry(source.code(),source.client(),source.label(),"SERVER","https://example.invalid/jasperserver","");check(GestaoReconciliation.addAbsent(List.of(source,server),false,kinds).stream().allMatch(r->r.state()==AddState.ADDED),"Nome de banco interferiu no cadastro do servidor");
  Registry partial=new Registry();partial.fail="bad.invalid";var result=GestaoReconciliation.addAbsent(List.of(row("bad.invalid"),row("good.invalid")),false,partial);check(result.get(0).state()==AddState.FAILED && result.get(1).state()==AddState.ADDED && !result.get(0).detail().contains("sensitive"),"Falha parcial bloqueou lote ou expôs detalhes");
  Registry simultaneous=new Registry();ExecutorService threads=Executors.newFixedThreadPool(4);try{List<Future<List<Addition>>> futures=new ArrayList<>();for(int i=0;i<4;i++)futures.add(threads.submit(()->GestaoReconciliation.addAbsent(List.of(source),false,simultaneous)));for(var f:futures)f.get(5,TimeUnit.SECONDS);check(simultaneous.writes.get()==1,"Importações simultâneas duplicaram o endpoint");}finally{threads.shutdownNow();}
  check(assess(source,List.of(),probes,null).state()==State.NEW,"Remoção externa não volta à classificação de ausente");
  System.out.println("PASS: classificação, configuração versus conectividade, fingerprints, identidade canônica, preservação, lote O(N+M), conflitos, falhas parciais e quatro importações simultâneas.");
 }
}
