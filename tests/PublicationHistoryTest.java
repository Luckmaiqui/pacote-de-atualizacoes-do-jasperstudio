import com.salutem.order.PublicationHistory;
import java.io.IOException;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Behavioral lifecycle tests; no network, credentials, JRXML or Studio installation. */
public final class PublicationHistoryTest {
 static final Instant NOW=Instant.parse("2026-11-25T20:02:00Z");
 static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static final class MutableClock extends Clock {Instant now=NOW;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}}
 static final class Store implements PublicationHistory.Store {
  String description;int version,writes,publishes,conflicts;boolean exists=true,fail,lostResponse,truncate;Instant serverTime;
  final String select="select unchanged",design="design unchanged",parameters="parameters unchanged";
  Store(String description){this.description=description;}
  public synchronized PublicationHistory.Snapshot read(){return exists?new PublicationHistory.Snapshot(description,version,serverTime):null;}
  public synchronized void patchDescription(int expected,String value)throws Exception{
   if(fail)throw new IOException("HTTP 400: limite configurado no servidor");
   if(conflicts-->0){description=description+"\nRegistro concorrente";version++;throw new PublicationHistory.Conflict();}
   if(expected!=version)throw new PublicationHistory.Conflict();writes++;description=truncate?value.substring(0,Math.min(20,value.length())):value;version++;
   if(lostResponse){lostResponse=false;throw new IOException("Resposta perdida após commit");}
  }
  void publish(){exists=true;publishes++;}
 }
 static PublicationHistory.Session session(){return new PublicationHistory.Session(Clock.fixed(NOW,ZoneOffset.UTC));}
 static String run(PublicationHistory.Session s,Store store,String user)throws Exception{return s.execute("server/org/report",user,ZONE,store,store::publish,()->false);}
 public static void main(String[] args)throws Exception{
  String line=PublicationHistory.line(NOW,ZONE,"Luckas");check(line.equals("25/11/2026 17:02 - (Luckas) - Alteração no select e/ou design."),"Formato/fuso incorreto");
  Store fresh=new Store("");fresh.exists=false;run(session(),fresh,"Luckas");check(fresh.description.equals(line)&&fresh.writes==1,"Relatório novo");
  String old="  Descrição original\r\n24/11/2026 14:30 - (Administrador) - Alteração no select e/ou design.  ";
  Store existing=new Store(old);run(session(),existing,"Luckas");check(existing.description.equals(old+"\n"+line),"Perdeu espaços, CRLF ou histórico");
  check(PublicationHistory.append("histórico\r\n",line).equals("histórico\r\n"+line),"Criou linha vazia extra");
  Store identical=new Store(old+"\r\n"+line);run(session(),identical,"Luckas");check(identical.writes==0,"Duplicou linha completa idêntica");
  check(!PublicationHistory.containsLine("prefixo "+line,line),"Comparação por substring");
  Store differentUsers=new Store("");PublicationHistory.Session shared=session();run(shared,differentUsers,"Luckas");run(shared,differentUsers,"OutroUsuário");check(differentUsers.writes==2&&differentUsers.description.contains("(OutroUsuário)"),"Não distinguiu usuários");
  for(String changes:new String[]{"nenhuma","SQL","design"}){Store s=new Store("");run(session(),s,"Luckas");check(s.description.equals(line)&&s.select.equals("select unchanged")&&s.design.equals("design unchanged")&&s.parameters.equals("parameters unchanged"),"Ramo indevido para alterações: "+changes);}
  Store failed=new Store(old);try{session().execute("failed","Luckas",ZONE,failed,()->{throw new IOException("falha de publicação");},()->false);throw new AssertionError("Suprimiu falha");}catch(IOException expected){}check(failed.writes==0&&failed.description.equals(old),"Registrou publicação com falha");
  Store cancelled=new Store(old);AtomicBoolean cancel=new AtomicBoolean();try{session().execute("cancel","Luckas",ZONE,cancelled,()->cancel.set(true),cancel::get);throw new AssertionError("Ignorou cancelamento");}catch(InterruptedException expected){}check(cancelled.writes==0,"Registrou cancelamento");
  AtomicInteger actions=new AtomicInteger();try{session().execute("cancel","Luckas",ZONE,cancelled,actions::incrementAndGet,()->true);throw new AssertionError("Ignorou cancelamento inicial");}catch(InterruptedException expected){}check(actions.get()==0,"Publicou após cancelamento inicial");
  MutableClock clock=new MutableClock();Store reopen=new Store("");run(new PublicationHistory.Session(clock),reopen,"Luckas");clock.now=NOW.plusSeconds(120);run(new PublicationHistory.Session(clock),reopen,"Luckas");check(reopen.description.equals(line+"\n25/11/2026 17:04 - (Luckas) - Alteração no select e/ou design."),"Reabertura não preservou servidor");
  Store conflict=new Store(old);conflict.conflicts=1;run(session(),conflict,"Luckas");check(conflict.description.equals(old+"\nRegistro concorrente\n"+line),"409 não releu e mesclou");
  Store ambiguous=new Store(old);ambiguous.lostResponse=true;run(session(),ambiguous,"Luckas");check(ambiguous.writes==1&&ambiguous.description.equals(old+"\n"+line),"Resposta perdida duplicou a gravação");
  Store pending=new Store(old);pending.fail=true;clock=new MutableClock();PublicationHistory.Session retry=new PublicationHistory.Session(clock);try{run(retry,pending,"Luckas");throw new AssertionError("Falso sucesso após falha de metadados");}catch(PublicationHistory.HistoryPending expected){check(expected.getMessage().contains("relatório foi publicado"),"Falha parcial não informada");}check(pending.description.equals(old),"Truncou histórico ao atingir limite");pending.fail=false;clock.now=NOW.plusSeconds(600);run(retry,pending,"Luckas");check(pending.description.equals(old+"\n"+line),"Retry gerou outro ticket/minuto");
  Store truncated=new Store(old);truncated.truncate=true;try{run(session(),truncated,"Luckas");throw new AssertionError("Aceitou truncamento silencioso");}catch(PublicationHistory.HistoryPending expected){}
  Store serverClock=new Store("");serverClock.serverTime=NOW.plusSeconds(120);run(session(),serverClock,"Luckas");check(serverClock.description.startsWith("25/11/2026 17:04"),"Ignorou horário real do servidor");
  Store concurrent=new Store(old);ExecutorService pool=Executors.newFixedThreadPool(2);try{CountDownLatch start=new CountDownLatch(1);Future<?> a=pool.submit(()->{try{start.await();run(session(),concurrent,"A");}catch(Exception e){throw new RuntimeException(e);}});Future<?> b=pool.submit(()->{try{start.await();run(session(),concurrent,"B");}catch(Exception e){throw new RuntimeException(e);}});start.countDown();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);}finally{pool.shutdownNow();}check(concurrent.description.startsWith(old)&&concurrent.description.contains("(A)")&&concurrent.description.contains("(B)")&&concurrent.writes==2,"Concorrência perdeu histórico");
  System.out.println("PASS: novo/existente; fuso e relógio do servidor; histórico literal; três tipos de alteração; deduplicação e usuários; falhas/cancelamento; reabertura; conflito, retry, resposta perdida e concorrência; rejeição de truncamento.");
 }
}
