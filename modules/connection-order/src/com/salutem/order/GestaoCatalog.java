package com.salutem.order;
import java.net.URI;import java.text.Normalizer;import java.util.*;import com.google.gson.*;
/** Parses only a small, explicit export schema, never cookies, tokens or page HTML. */
public final class GestaoCatalog {
 public record Entry(String code,String client,String label,String kind,String endpoint,String problem){
  public String name(boolean hyphen){return (hyphen?code+" - ":"("+code+") ")+client+" - "+label;}
  public String key(){return GestaoCatalog.key(kind,endpoint);}
 }
 static String string(JsonObject o,String key){JsonElement e=o.get(key);return e==null || !e.isJsonPrimitive()?"":e.getAsString().strip();}
 static String fold(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
 static String code(String s){if(!s.matches("[0-9]{1,8}"))throw new IllegalArgumentException("Código de cliente inválido");return String.format(Locale.ROOT,"%03d",Long.parseLong(s));}
 public static String key(String kind,String endpoint){return kind+":"+(kind.equals("SERVER")?serverUrl(endpoint):jdbcKey(endpoint));}
 public static String serverUrl(String value){try{URI u=URI.create(value.strip());String scheme=Objects.toString(u.getScheme(),"").toLowerCase(Locale.ROOT);if(!Set.of("http","https").contains(scheme) || u.getHost()==null || u.getUserInfo()!=null || u.getRawQuery()!=null || u.getRawFragment()!=null)throw new Exception();String p=Objects.toString(u.getRawPath(),"").replaceAll("/+$","");int port=u.getPort();if(port==(scheme.equals("https")?443:80))port=-1;return scheme+"://"+u.getHost().toLowerCase(Locale.ROOT)+(port<0?"":":"+port)+p;}catch(Exception e){throw new IllegalArgumentException("URL JasperServer inválida");}}
 public static String jdbcKey(String value){try{if(!value.startsWith("jdbc:postgresql://"))return value;URI u=URI.create(value.substring(5));if(u.getHost()==null || u.getUserInfo()!=null)return value;return "jdbc:postgresql://"+u.getHost().toLowerCase(Locale.ROOT)+":"+(u.getPort()<0?5432:u.getPort())+u.getRawPath();}catch(Exception e){return value;}}
 public static String jdbc(String host,String database){try{if(!host.matches("[A-Za-z0-9.-]+:[0-9]{1,5}"))throw new Exception();URI u=URI.create("postgresql://"+host);if(u.getHost()==null || u.getPort()<1 || u.getPort()>65535)throw new Exception();return "jdbc:postgresql://"+u.getHost().toLowerCase(Locale.ROOT)+":"+u.getPort()+"/"+database;}catch(Exception e){throw new IllegalArgumentException("Host/porta inválidos; revise no Gestão");}}
 public static List<String> warnings(String json){
  if(json.length()>5_000_000)throw new IllegalArgumentException("Exportação maior que o limite de 5 MB");
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();JsonArray values=root.getAsJsonArray("warnings");if(values==null)return List.of();if(values.size()>10000)throw new IllegalArgumentException("Lista de avisos inválida");
  List<String> result=new ArrayList<>();for(JsonElement value:values)if(value.isJsonPrimitive()){String warning=value.getAsString();result.add(warning.length()>500?warning.substring(0,500):warning);}return List.copyOf(result);
 }
 public static List<Entry> parse(String json){
  if(json.length()>5_000_000)throw new IllegalArgumentException("Exportação maior que o limite de 5 MB");
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();if(!string(root,"format").equals("salutem-gestao-v1"))throw new IllegalArgumentException("Formato de exportação incompatível");
  if(!string(root,"source").equals("https://gestao.sistemasalutem.com.br/management"))throw new IllegalArgumentException("Origem da exportação inválida");
  List<Entry> result=new ArrayList<>();Set<String> seen=new HashSet<>();JsonArray rows=root.getAsJsonArray("connections");if(rows==null || rows.size()>10000)throw new IllegalArgumentException("Lista de conexões inválida");
  for(JsonElement el:rows){JsonObject r=el.getAsJsonObject();String c=code(string(r,"code")),client=string(r,"client").replaceFirst("^\\(?[0-9]+\\)?\\s*[-–]?\\s*","");String label=string(r,"label"),kind=string(r,"kind"),ep="",problem="";
   if(client.isBlank() || client.length()>200 || label.length()>200)throw new IllegalArgumentException("Nome de cliente ou sistema inválido");
   try{if(kind.equals("SERVER")){ep=serverUrl(string(r,"endpoint"));if(label.isBlank())label="JasperServer";}else if(kind.equals("DATABASE")){
    String f=fold(label);String db=f.contains("homolog")?null:f.equals("hospitalar")||f.equals("banco hospitalar")?"salutem_hospitalar":f.equals("ambulatorial")||f.equals("banco ambulatorial")?"salutem":f.equals("painel")||f.equals("banco painel")?"painel":null;
    if(db==null)throw new IllegalArgumentException("Sistema não identificado com segurança; revisão manual necessária");ep=jdbc(string(r,"endpoint"),db);
   }else throw new IllegalArgumentException("Tipo de conexão inválido");}catch(IllegalArgumentException e){problem=e.getMessage();ep=string(r,"endpoint");}
   Entry entry=new Entry(c,client,label,kind,ep,problem);if(!problem.isEmpty() || seen.add(c+":"+entry.key()))result.add(entry);
  }
  Map<String,Set<String>> owners=new HashMap<>();for(Entry entry:result)owners.computeIfAbsent(entry.key(),k->new HashSet<>()).add(entry.code());
  for(int n=0;n<result.size();n++){Entry e=result.get(n);if(e.problem().isEmpty() && owners.get(e.key()).size()>1)result.set(n,new Entry(e.code(),e.client(),e.label(),e.kind(),e.endpoint(),"Endereço compartilhado por clientes distintos; revisão manual necessária"));}
  result.sort(Comparator.comparing(Entry::code).thenComparing(Entry::client).thenComparing(Entry::label));return List.copyOf(result);
 }
}
