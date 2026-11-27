package com.salutem.order;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GlobalSearchEngineTest {
 public static void main(String[] args)throws Exception{
  List<GlobalSearchEngine.Target> targets=new ArrayList<>();for(int i=0;i<8;i++)targets.add(new GlobalSearchEngine.Target(i,"Servidor "+i));
  AtomicInteger active=new AtomicInteger(),maximum=new AtomicInteger(),resultCount=new AtomicInteger(),errors=new AtomicInteger();CountDownLatch done=new CountDownLatch(8);
  GlobalSearchEngine.search(targets,"ficha padrão",(target,query,canceled)->{
   int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);
   try{Thread.sleep(35);if(target.connection().equals(3))throw new IllegalStateException("Indisponível");return List.of(new GlobalSearchEngine.Hit(target,query,"/relatorios/ficha","reportUnit",new Object()));}
   finally{active.decrementAndGet();}
  },update->{if(update.error()!=null)errors.incrementAndGet();else{if(!update.hits().get(0).target().equals(update.target()))throw new AssertionError("Origem perdida");resultCount.addAndGet(update.hits().size());}done.countDown();});
  if(!done.await(5,TimeUnit.SECONDS) || maximum.get()!=6 || resultCount.get()!=7 || errors.get()!=1)throw new AssertionError("Agregação, limite ou isolamento de erro");
  CountDownLatch started=new CountDownLatch(6),stopped=new CountDownLatch(6);AtomicInteger callbacks=new AtomicInteger(),calls=new AtomicInteger();
  var session=GlobalSearchEngine.search(targets,"cancelar",(target,query,canceled)->{calls.incrementAndGet();started.countDown();try{while(!canceled.getAsBoolean())Thread.sleep(5);return List.of();}finally{stopped.countDown();}},update->callbacks.incrementAndGet());
  if(!started.await(3,TimeUnit.SECONDS))throw new AssertionError("Não iniciou");session.cancel();if(!stopped.await(3,TimeUnit.SECONDS))throw new AssertionError("Não cancelou");Thread.sleep(100);
  if(callbacks.get()!=0 || calls.get()!=6)throw new AssertionError("Executou fila cancelada ou entregou resultado obsoleto");
  CountDownLatch release=new CountDownLatch(1),bothStarted=new CountDownLatch(6),bothDone=new CountDownLatch(8);active.set(0);maximum.set(0);
  GlobalSearchEngine.Backend blocked=(target,query,canceled)->{int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);bothStarted.countDown();try{release.await();return List.of();}finally{active.decrementAndGet();}};
  GlobalSearchEngine.search(targets.subList(0,4),"janela 1",blocked,update->bothDone.countDown());
  GlobalSearchEngine.search(targets.subList(4,8),"janela 2",blocked,update->bothDone.countDown());
  if(!bothStarted.await(3,TimeUnit.SECONDS)||maximum.get()!=6)throw new AssertionError("Limite não é global entre janelas");release.countDown();if(!bothDone.await(3,TimeUnit.SECONDS))throw new AssertionError("Fila global não avançou");
  List<GlobalSearchEngine.Target> batch=new ArrayList<>();for(int i=0;i<108;i++)batch.add(new GlobalSearchEngine.Target(i,"Servidor "+i));
  long start=System.nanoTime();CountDownLatch batchDone=new CountDownLatch(108);AtomicInteger batchErrors=new AtomicInteger();
  GlobalSearchEngine.search(batch,"desempenho",(target,query,canceled)->{Thread.sleep(15);return List.of();},update->{if(update.error()!=null)batchErrors.incrementAndGet();batchDone.countDown();});
  if(!batchDone.await(8,TimeUnit.SECONDS)||batchErrors.get()!=0)throw new AssertionError("Lote de 108 não concluiu");
  System.out.println("PASS: 108 servidores simulados em "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)+" ms (latência sintética de 15 ms); limite global de 6 entre janelas; falha isolada; cancelamento de ativos e fila; nenhum resultado após cancelar. Não mede a rede real.");
 }
}
