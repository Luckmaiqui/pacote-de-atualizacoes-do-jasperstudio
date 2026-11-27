import com.salutem.order.NumericOrder;
import com.salutem.theme.ThemePalette;
import java.util.*;
import java.nio.file.*;
import java.io.*;

/** Deterministic data/code regression; deliberately separate from SWT visual checks. */
public final class ModuleRegressionTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void themes(){
  Set<String> ids=new HashSet<>();
  for(ThemePalette palette:ThemePalette.all()){
   check(ids.add(palette.id),"Paleta duplicada");check(ThemePalette.find(palette.id)==palette,"Identidade da paleta");
   check(palette.background!=palette.foreground && palette.accent!=palette.background,"Texto/seleção invisível: "+palette.id);
   check((palette.background&0xFFFFFF)==palette.background,"Cor inválida");
  }
  check(ids.size()==7,"Quantidade de paletas");check(ThemePalette.find("unknown")==null,"Paleta inexistente não deve ser inventada");
  try{ThemePalette.all().clear();throw new AssertionError("Paletas mutáveis");}catch(UnsupportedOperationException expected){}
  System.out.println("PASS: carregamento real das sete paletas, identidade e imutabilidade.");
 }
 static void order(){
  NumericOrder comparator=new NumericOrder();List<String> labels=new ArrayList<>(Arrays.asList(null,"", "Sample DB","One Empty Record", "(000) Matriz","001 - Mauá","(001) Mauá", "CopyOf(001) Mauá", "(999999999999999999999999999999) Longo"));
  Random random=new Random(270);for(int i=0;i<150;i++){int n=random.nextInt(500);labels.add(i%2==0?String.format("(%03d) Unidade %d",n,i):n+" - Unidade "+i);}
  for(String a:labels)for(String b:labels)check(Integer.signum(comparator.compare(a,b))==-Integer.signum(comparator.compare(b,a)),"Antissimetria");
  for(int i=0;i<20000;i++){String a=labels.get(random.nextInt(labels.size())),b=labels.get(random.nextInt(labels.size())),c=labels.get(random.nextInt(labels.size()));if(comparator.compare(a,b)<=0&&comparator.compare(b,c)<=0)check(comparator.compare(a,c)<=0,"Transitividade");}
  labels.sort(comparator);for(int i=1;i<labels.size();i++)check(comparator.compare(labels.get(i-1),labels.get(i))<=0,"Ordenação total");
  Map<Integer,Object> connections=new LinkedHashMap<>();IdentityHashMap<Object,String> names=new IdentityHashMap<>();for(int i=0;i<80;i++){Object value=new Object();connections.put(i,value);names.put(value,"("+(80-i)+") Unidade");}
  Map<Integer,Object> before=new HashMap<>(connections);check(comparator.sort(connections,names::get),"Não ordenou");check(!comparator.sort(connections,names::get),"Ordenação não idempotente");for(int key:before.keySet())check(connections.get(key)==before.get(key),"Conexão substituída");
  System.out.println("PASS: formatos numéricos, IDs longos, nulos, antissimetria, transitividade e identidade de conexões.");
 }
 static void translation(Path root)throws Exception{
  Properties dictionary=new Properties();try(InputStream input=Files.newInputStream(root.resolve("modules/language/resources/translations.properties"))){dictionary.load(input);}
  String[][] expected={{"ID","ID"},{"Java Bean","Java Bean"},{"Data Adapters","Banco de Dados"},{"Overwrite","Escrever"},{"Overwrite Only Expression","Escrever Apenas Expressão"},{"Problems","Problemas"},{"Format Factory Class","Classe da Fábrica de Formatação"}};
  for(String[] item:expected)check(item[1].equals(dictionary.getProperty(item[0])),"Regressão de tradução/encoding: "+item[0]);
  System.out.println("PASS: propriedades carregadas pelo Java, acentos, ID, nomes técnicos e estratégias de publicação.");
 }
 public static void main(String[] args)throws Exception{themes();order();translation(Paths.get(args[0]));}
}
