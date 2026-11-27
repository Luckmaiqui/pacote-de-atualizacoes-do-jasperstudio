package com.salutem.order;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/** Publication lifecycle independent of Studio widgets and transport. */
public final class PublicationHistory {
 public static final String MESSAGE="Alteração no select e/ou design.";
 private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm",Locale.forLanguageTag("pt-BR"));
 private static final ReentrantLock[] LOCKS=new ReentrantLock[64];
 static {for(int i=0;i<LOCKS.length;i++)LOCKS[i]=new ReentrantLock();}
 public static final class Snapshot {
  public final String description;public final int version;public final Instant serverTime;
  public Snapshot(String description,int version,Instant serverTime){this.description=description==null?"":description;this.version=version;this.serverTime=serverTime;}
 }
 public interface Store {Snapshot read()throws Exception;void patchDescription(int version,String description)throws Exception;}
 public interface Publish {void run()throws Exception;}
 public static final class Conflict extends Exception {private static final long serialVersionUID=1L;public Conflict(){super("A Unidade de Relatório foi atualizada por outra operação.");}}
 public static final class HistoryPending extends Exception {private static final long serialVersionUID=1L;public HistoryPending(Throwable cause){super("O relatório foi publicado, mas o histórico da descrição não pôde ser confirmado. O assistente permanecerá aberto. Nenhum truncamento foi solicitado. Motivo: "+cause.getMessage(),cause);}}
 public static String line(Instant instant,ZoneId zone,String user){
  if(user==null||user.trim().isEmpty()||user.indexOf('\n')>=0||user.indexOf('\r')>=0)throw new IllegalArgumentException("Não foi possível identificar o usuário do servidor.");
  return FORMAT.withZone(zone).format(instant)+" - ("+user+") - "+MESSAGE;
 }
 public static boolean containsLine(String text,String line){if(text==null)return false;for(String existing:text.split("\\r\\n|\\r|\\n",-1))if(existing.equals(line))return true;return false;}
 public static String append(String previous,String addition){
  if(previous==null||previous.isEmpty())return addition;
  if(containsLine(previous,addition))return previous;
  return previous+(previous.endsWith("\n")||previous.endsWith("\r")?"":"\n")+addition;
 }
 static String preserve(String latest,String before){
  if(before==null||before.isEmpty()||latest.contains(before))return latest;
  if(latest.isEmpty()||before.contains(latest))return before;
  return before+(before.endsWith("\n")||before.endsWith("\r")?"":"\n")+latest;
 }
 public static final class Session {
  private final Clock clock;private String operation,line;
  public Session(Clock clock){this.clock=clock;}
  /** One ticket survives retries of this wizard, including a lost HTTP response. */
  public String execute(String key,String user,ZoneId zone,Store store,Publish publish,BooleanSupplier cancelled)throws Exception {
   // Validate identity and zone before the native report write, without recording an entry.
   PublicationHistory.line(clock.instant(),zone,user);
   ReentrantLock lock=LOCKS[(key.hashCode()&0x7fffffff)%LOCKS.length];
   while(!lock.tryLock(100,TimeUnit.MILLISECONDS))check(cancelled);
   try{
    check(cancelled);Snapshot before=store.read();check(cancelled);
    publish.run(); // No history is written for a failed or cancelled native operation.
    check(cancelled);
    try{
     for(int attempt=0;attempt<5;attempt++){
      check(cancelled);Snapshot latest=store.read();if(latest==null)throw new IllegalStateException("A Unidade de Relatório publicada não foi encontrada.");
      String ticketKey=key+'\u0000'+user+'\u0000'+zone.getId();
      if(!ticketKey.equals(operation)||line==null){operation=ticketKey;line=PublicationHistory.line(latest.serverTime==null?clock.instant():latest.serverTime,zone,user);}
      String merged=preserve(latest.description,before==null?"":before.description),expected=append(merged,line);
      if(expected.equals(latest.description))return latest.description;
      check(cancelled);
      try{store.patchDescription(latest.version,expected);}
      catch(Conflict conflict){continue;}
      catch(Exception ambiguous){
       if(ambiguous instanceof InterruptedException)throw ambiguous;
       // A write can have committed even when its response was lost.
       Snapshot confirm=store.read();if(confirm!=null&&confirm.description.contains(expected))return confirm.description;throw ambiguous;
      }
      Snapshot confirmed=store.read();if(confirmed!=null&&confirmed.description.contains(expected))return confirmed.description;
      throw new IllegalStateException("O servidor devolveu uma descrição diferente da enviada. Verifique o limite do campo e as regras do servidor.");
     }
     throw new Conflict();
    }catch(InterruptedException cancelledOperation){throw cancelledOperation;}
    catch(Exception failure){throw new HistoryPending(failure);}
   }finally{lock.unlock();}
  }
  private static void check(BooleanSupplier cancelled)throws InterruptedException{if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new InterruptedException("Publicação cancelada.");}
 }
}
