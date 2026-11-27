package com.salutem.order;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Explicit metadata searches, bounded across all windows and search sessions. */
final class GlobalSearchEngine {
 record Target(Object connection,String name) {}
 record Hit(Target target,String label,String uri,String type,Object nativeValue) {}
 record Update(Target target,List<Hit> hits,Throwable error) {}
 interface Backend { List<Hit> search(Target target,String query,BooleanSupplier canceled) throws Exception; }
 static final int PARALLELISM=6;
 private static final ThreadPoolExecutor POOL=new ThreadPoolExecutor(PARALLELISM,PARALLELISM,30,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->{Thread t=new Thread(r,"JasperStudio-global-search");t.setDaemon(true);return t;});
 static final class Session {
  final AtomicBoolean canceled=new AtomicBoolean();final List<Future<?>> tasks=new ArrayList<>();
  synchronized void cancel(){canceled.set(true);for(Future<?> f:tasks)f.cancel(true);POOL.purge();}
 }
 private static Throwable cause(Throwable error){while(error instanceof java.lang.reflect.InvocationTargetException && error.getCause()!=null)error=error.getCause();return error;}
 static Session search(List<Target> targets,String query,Backend backend,Consumer<Update> listener){
  Session session=new Session();
  synchronized(session){for(Target target:targets)session.tasks.add(POOL.submit(()->{
   if(session.canceled.get())return;
   Update update;
   try {List<Hit> hits=backend.search(target,query,()->session.canceled.get() || Thread.currentThread().isInterrupted());update=new Update(target,List.copyOf(hits),null);}
   catch(Exception error){update=new Update(target,List.of(),cause(error));}
   if(!session.canceled.get())listener.accept(update);
  }));}
  return session;
 }
}
