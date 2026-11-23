package com.salutem.order;

import java.util.*;

/** Shared classification and add-only guard. Contains no SWT, passwords or network calls. */
public final class GestaoReconciliation {
 public enum Configuration { COMPLETE, INCOMPLETE, INVALID }
 public enum State { NEW, READY, REGISTERED, VALID, FAILED, INCOMPLETE, REVIEW }
 public record Registered(Object identity,String name,String fingerprint,Configuration configuration,String detail) {
  @Override public String toString(){return "Registered[metadata]";}
 }
 public record Probe(String fingerprint,boolean success,String message){}
 public record Assessment(State state,boolean registered,boolean testable,String detail) {
  public boolean pending(){return state!=State.REGISTERED && state!=State.VALID;}
  public boolean addable(){return !registered && state==State.READY;}
 }
 public static Assessment assess(GestaoCatalog.Entry row,List<Registered> matches,Map<Object,Probe> existingTests,Probe newTest) {
  if(!row.problem().isEmpty())return new Assessment(State.REVIEW,!matches.isEmpty(),false,row.problem());
  if(matches.isEmpty()){
   if(newTest!=null && newTest.fingerprint().equals(row.key()))return new Assessment(newTest.success()?State.READY:State.FAILED,false,true,newTest.message());
   return new Assessment(State.NEW,false,true,"Nova Conexão — Teste Necessário");
  }
  if(matches.size()>1){
   boolean configured=matches.stream().anyMatch(m->m.configuration()==Configuration.COMPLETE);
   return new Assessment(configured?State.REGISTERED:State.INCOMPLETE,true,false,matches.size()+" Configurações Cadastradas — Gerencie no Explorador");
  }
  Registered found=matches.get(0);
  if(found.configuration()!=Configuration.COMPLETE)return new Assessment(found.configuration()==Configuration.INCOMPLETE?State.INCOMPLETE:State.REVIEW,true,false,found.detail());
  Probe probe=existingTests.get(found.identity());
  if(probe!=null && probe.fingerprint().equals(found.fingerprint()))return new Assessment(probe.success()?State.VALID:State.FAILED,true,true,probe.message());
  return new Assessment(State.REGISTERED,true,true,"Cadastrada — Conectividade Não Verificada");
 }

 public interface Registry {
  Map<String,List<Registered>> snapshot();
  Registered add(GestaoCatalog.Entry row,String name)throws Exception;
 }
 public enum AddState { ADDED, ALREADY_REGISTERED, CONFLICT, FAILED }
 public record Addition(GestaoCatalog.Entry row,AddState state,String detail){}
 private static final Object ADD_LOCK=new Object();
 /** Serializes imports in this JVM and rechecks presence inside the guard, never trusting a UI snapshot. */
 public static List<Addition> addAbsent(List<GestaoCatalog.Entry> rows,boolean hyphen,Registry registry) {
  synchronized(ADD_LOCK){
   Map<String,List<Registered>> index=new HashMap<>();registry.snapshot().forEach((key,value)->index.put(key,new ArrayList<>(value)));
   Set<String> names=new HashSet<>();index.forEach((key,value)->{String kind=key.substring(0,key.indexOf(':'));for(Registered r:value)names.add(kind+":"+r.name().strip().toLowerCase(Locale.ROOT));});
   List<Addition> result=new ArrayList<>();
   for(GestaoCatalog.Entry row:rows){
    if(!row.problem().isEmpty()){result.add(new Addition(row,AddState.FAILED,"Revise os Dados de Origem"));continue;}
    if(!index.getOrDefault(row.key(),List.of()).isEmpty()){result.add(new Addition(row,AddState.ALREADY_REGISTERED,"Já Cadastrada — Preservada"));continue;}
    String name=row.name(hyphen),nameKey=row.kind()+":"+name.strip().toLowerCase(Locale.ROOT);
    if(names.contains(nameKey)){result.add(new Addition(row,AddState.CONFLICT,"Nome Já Utilizado por Outro Endereço"));continue;}
    try{
     Registered added=Objects.requireNonNull(registry.add(row,name));index.computeIfAbsent(row.key(),key->new ArrayList<>()).add(added);names.add(nameKey);
     result.add(new Addition(row,AddState.ADDED,"Adicionada"));
    }catch(Exception error){result.add(new Addition(row,AddState.FAILED,"Falha ao Gravar — Revise o Cadastro"));}
   }
   return List.copyOf(result);
  }
 }
}
