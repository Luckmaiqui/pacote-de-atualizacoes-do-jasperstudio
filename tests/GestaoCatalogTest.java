import com.salutem.order.*;import java.util.*;
public class GestaoCatalogTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static String catalog(String rows){return "{\"format\":\"salutem-gestao-v1\",\"source\":\"https://gestao.sistemasalutem.com.br/management\",\"connections\":["+rows+"]}";}
 static String row(String code,String label,String kind,String ep){return "{\"code\":\""+code+"\",\"client\":\"("+code+") Cliente de Teste\",\"label\":\""+label+"\",\"kind\":\""+kind+"\",\"endpoint\":\""+ep+"\"}";}
 public static void main(String[] args){
  String a=row("1","Hospitalar","DATABASE","EXAMPLE.invalid:8002"),b=row("1","Ambulatorial","DATABASE","example.invalid:7002"),c=row("1","Painel","DATABASE","example.invalid:8002");
  var result=GestaoCatalog.parse(catalog(a+","+b+","+c+","+a));check(result.size()==3,"Deduplicação local");
  check(result.stream().anyMatch(e->e.endpoint().equals("jdbc:postgresql://example.invalid:8002/salutem_hospitalar")),"Banco hospitalar");check(result.stream().anyMatch(e->e.endpoint().endsWith("/salutem")),"Banco ambulatorial");check(result.stream().anyMatch(e->e.endpoint().endsWith("/painel")),"Banco painel");check(result.get(0).name(false).startsWith("(001) Cliente"),"Código estável");check(result.get(0).name(true).startsWith("001 - Cliente"),"Padrão alternativo");
  check(!GestaoCatalog.parse(catalog(row("2","Hospitalar Homologação","DATABASE","example.invalid:8"))).get(0).problem().isEmpty(),"Não presumir homologação");
  check(!GestaoCatalog.parse(catalog(row("2","Hospitalar","DATABASE","https://example.invalid:8"))).get(0).problem().isEmpty(),"Não consertar endpoint silenciosamente");
  check(GestaoCatalog.serverUrl(" https://EXAMPLE.invalid/jasperserver/ ").equals("https://example.invalid/jasperserver"),"Normalizar URL");
  try{GestaoCatalog.serverUrl("https://user:secret@example.invalid/jasperserver");throw new AssertionError("Credencial embutida aceita");}catch(IllegalArgumentException expected){}
  check(GestaoCatalog.jdbcKey("jdbc:postgresql://EXAMPLE.invalid:8002/salutem?ssl=true").equals("jdbc:postgresql://example.invalid:8002/salutem"),"Identidade separada de opções JDBC");
  var shared=GestaoCatalog.parse(catalog(a+","+row("2","Hospitalar","DATABASE","example.invalid:8002")));check(shared.size()==2 && shared.stream().allMatch(e->!e.problem().isEmpty()),"Não descartar clientes com endpoint compartilhado");
  try{GestaoCatalog.parse(catalog(a).replace("salutem-gestao-v1","outro"));throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(GestaoCatalog.warnings(catalog(a)).isEmpty(),"Compatibilidade com exportação anterior sem avisos");
  check(GestaoCatalog.warnings(catalog(a).replace("\"connections\":", "\"warnings\":[\"Cliente Sem ID\"],\"connections\":")).size()==1,"Avisos do coletor disponíveis para revisão");
  System.out.println("PASS: mapeamento PostgreSQL, nomes, normalização, duplicidades e validação de origem.");
 }
}
