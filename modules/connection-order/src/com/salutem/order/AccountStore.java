package com.salutem.order;

import java.util.*;
import org.eclipse.equinox.security.storage.*;

/** Local credential catalogue. Never exports credentials or writes a plaintext fallback. */
public final class AccountStore {
 public enum Kind { DATABASE("Banco de Dados"), SERVER("JasperServer");public final String label;Kind(String label){this.label=label;} }
 public record Account(String id,Kind kind,String name) {}
 public record Login(String username,String password) { @Override public String toString(){return "Login[redigido]";} }
 private final ISecurePreferences root;
 public AccountStore(){this(SecurePreferencesFactory.getDefault().node("com.salutem.jasper.accounts"));}
 public AccountStore(ISecurePreferences root){this.root=Objects.requireNonNull(root);}
 public List<Account> list(Kind kind)throws Exception{
  List<Account> result=new ArrayList<>();ISecurePreferences group=root.node(kind.name());
  for(String id:group.childrenNames()){ISecurePreferences node=group.node(id);if(node.getBoolean("complete",false))result.add(new Account(id,kind,node.get("name","")));}
  result.sort(Comparator.comparing(Account::name,String.CASE_INSENSITIVE_ORDER));return List.copyOf(result);
 }
 public Login read(Account account)throws Exception{
  ISecurePreferences group=root.node(account.kind().name());if(!group.nodeExists(account.id()))throw new IllegalStateException("Conta removida");
  ISecurePreferences node=group.node(account.id());if(!node.getBoolean("complete",false) || !node.isEncrypted("username") || !node.isEncrypted("password"))throw new IllegalStateException("Conta sem proteção segura");
  return new Login(node.get("username",""),node.get("password",""));
 }
 public Account save(Account previous,Kind kind,String name,String username,String password)throws Exception{
  name=name.strip();if(name.isEmpty() || username.isBlank())throw new IllegalArgumentException("Preencha Nome da Conta e Usuário.");
  if(previous!=null && previous.kind()!=kind)throw new IllegalArgumentException("Tipo de conta incompatível");
  for(Account other:list(kind))if(other.name().equalsIgnoreCase(name) && (previous==null || !other.id().equals(previous.id())))throw new IllegalArgumentException("Já existe uma conta com esse nome neste tipo.");
  // Write a complete new record before removing the previous one. On encryption
  // failure the existing account is retained, and the incomplete record is removed.
  ISecurePreferences group=root.node(kind.name());String id=UUID.randomUUID().toString();ISecurePreferences node=group.node(id);
  try{node.put("name",name,false);node.put("username",username,true);node.put("password",password,true);node.putBoolean("complete",true,false);root.flush();}
  catch(Exception error){node.removeNode();try{root.flush();}catch(Exception ignored){}throw error;}
  if(previous!=null && group.nodeExists(previous.id())){group.node(previous.id()).removeNode();root.flush();}
  return new Account(id,kind,name);
 }
 public void delete(Account account)throws Exception{ISecurePreferences group=root.node(account.kind().name());if(group.nodeExists(account.id())){group.node(account.id()).removeNode();root.flush();}}
}
