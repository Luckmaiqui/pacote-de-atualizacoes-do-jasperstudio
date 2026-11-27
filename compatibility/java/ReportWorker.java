package com.salutem.compat;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** One engine and one JVM per process. No Studio, OSGi or dependency on a specific JR binary API. */
public final class ReportWorker {
 private static Class<?> type(String name)throws Exception{return Class.forName(name);}
 private static Object call(String owner,String name,Class<?>[] parameters,Object...args)throws Exception{return type(owner).getMethod(name,parameters).invoke(null,args);}
 private static String sha(Path file)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
 private static String json(Object value){
  if(value==null)return "null";if(value instanceof Number||value instanceof Boolean)return value.toString();
  if(value instanceof Map){List<String> parts=new ArrayList<String>();for(Object entry:((Map<?,?>)value).entrySet()){Map.Entry<?,?> e=(Map.Entry<?,?>)entry;parts.add(json(e.getKey().toString())+":"+json(e.getValue()));}return "{"+join(parts)+"}";}
  if(value instanceof Collection){List<String> parts=new ArrayList<String>();for(Object item:(Collection<?>)value)parts.add(json(item));return "["+join(parts)+"]";}
  return "\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t")+"\"";
 }
 private static String join(List<String> values){StringBuilder out=new StringBuilder();for(String value:values){if(out.length()>0)out.append(',');out.append(value);}return out.toString();}
 public static Map<String,Object> inspect(Path file)throws Exception{
  if(Files.size(file)>20_000_000)throw new IllegalArgumentException("Limite de JRXML: 20 MB");
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
  Document document=f.newDocumentBuilder().parse(file.toFile());Element root=document.getDocumentElement();
  if(!"jasperReport".equals(root.getLocalName()))throw new IllegalArgumentException("O documento não é um relatório JRXML");
  boolean legacy="http://jasperreports.sourceforge.net/jasperreports".equals(root.getNamespaceURI()),modern=false;
  Set<String> elements=new TreeSet<String>();NodeList all=root.getElementsByTagName("*");for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);String name=e.getLocalName();if(name==null)name=e.getTagName();elements.add(name);if(name.equals("element") && e.hasAttribute("kind"))modern=true;if(name.equals("reportElement")||name.equals("textFieldExpression"))legacy=true;}
  Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("exactVersion",null);result.put("format",legacy&&modern?"mixed":legacy?"digester-6":modern?"jackson-7":"undetermined");result.put("name",root.getAttribute("name"));result.put("language",root.hasAttribute("language")?root.getAttribute("language"):"java");result.put("elements",elements);result.put("sha256",sha(file));
  result.put("warning","Formato e recursos não identificam a versão exata; valide com o motor escolhido.");return result;
 }
 public static void main(String[] args){
  try{System.setOut(new PrintStream(System.out,true,"UTF-8"));}catch(UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
  Map<String,Object> result=new LinkedHashMap<String,Object>();
  try{
   if(args.length!=4)throw new IllegalArgumentException("Uso: ReportWorker <versão|inspect> <JRXML> <diretório novo> <validate|compile|smoke>");
   if(!Arrays.asList("validate","compile","smoke").contains(args[3]))throw new IllegalArgumentException("Operação inválida");
   Path input=Paths.get(args[1]).toRealPath(),output=Paths.get(args[2]).toAbsolutePath();Map<String,Object> info=inspect(input);result.put("inspection",info);result.put("java",System.getProperty("java.version"));
   if(args[0].equals("inspect")){result.put("ok",true);System.out.println("SALUTEM_RESULT="+json(result));return;}
   String actual=type("net.sf.jasperreports.engine.JasperCompileManager").getPackage().getImplementationVersion();result.put("engine",args[0]);result.put("engineManifestVersion",actual);
   String artifact=Paths.get(type("net.sf.jasperreports.engine.JasperCompileManager").getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString();
   result.put("loadedArtifact",artifact);
   if(!artifact.equals("jasperreports-"+args[0]+".jar"))throw new IllegalStateException("Artefato carregado diverge do motor selecionado");
   result.put("manifestVersionMatches",actual==null || actual.equals(args[0]) || actual.startsWith(args[0]+"-"));
   // Some official artifacts have stale Implementation-Version values; the parent verifies GAV + SHA256.
   String format=(String)info.get("format");if(format.equals("mixed")||(format.equals("digester-6")&&args[0].startsWith("7."))||(format.equals("jackson-7")&&args[0].startsWith("6.")))throw new IllegalArgumentException("Formato incompatível com a família do motor; o original não foi convertido");
   Class<?> designType=type("net.sf.jasperreports.engine.design.JasperDesign"),reportType=type("net.sf.jasperreports.engine.JasperReport");
   Object design;try(InputStream in=Files.newInputStream(input)){design=call("net.sf.jasperreports.engine.xml.JRXmlLoader","load",new Class<?>[]{InputStream.class},in);}result.put("read",true);
   Object faults=call("net.sf.jasperreports.engine.JasperCompileManager","verifyDesign",new Class<?>[]{designType},design);if(!((Collection<?>)faults).isEmpty())throw new IllegalArgumentException("Falhas de validação do modelo: "+((Collection<?>)faults).size());result.put("validate",true);
   if(!args[3].equals("validate")){
    Files.createDirectories(output);Object report=call("net.sf.jasperreports.engine.JasperCompileManager","compileReport",new Class<?>[]{designType},design);result.put("compile",true);
    call("net.sf.jasperreports.engine.util.JRSaver","saveObject",new Class<?>[]{Object.class,String.class},report,output.resolve("report.jasper").toString());
    if(args[3].equals("smoke")){
     report=call("net.sf.jasperreports.engine.util.JRLoader","loadObject",new Class<?>[]{File.class},output.resolve("report.jasper").toFile());
     if(!reportType.isInstance(report))throw new IllegalStateException("Releitura do compilado retornou outro tipo");result.put("compiledRoundTrip",true);
     // An edited copy is written only into the fresh output directory, never over the source.
     designType.getMethod("setName",String.class).invoke(design,"compat_edited");
     call("net.sf.jasperreports.engine.xml.JRXmlWriter","writeReport",new Class<?>[]{type("net.sf.jasperreports.engine.JRReport"),String.class,String.class},design,output.resolve("edited.jrxml").toString(),"UTF-8");
     try(InputStream in=Files.newInputStream(output.resolve("edited.jrxml"))){call("net.sf.jasperreports.engine.xml.JRXmlLoader","load",new Class<?>[]{InputStream.class},in);}result.put("editRoundTrip",true);
     List<Map<String,?>> rows=new ArrayList<Map<String,?>>();Map<String,String> first=new HashMap<String,String>();first.put("value","Ação");rows.add(first);Map<String,String> second=new HashMap<String,String>();second.put("value","Segunda Linha");rows.add(second);
     Object data=type("net.sf.jasperreports.engine.data.JRMapCollectionDataSource").getConstructor(Collection.class).newInstance(rows);Map<String,Object> params=new HashMap<String,Object>();params.put("TITLE","Compatibilidade");
     Object print=call("net.sf.jasperreports.engine.JasperFillManager","fillReport",new Class<?>[]{reportType,Map.class,type("net.sf.jasperreports.engine.JRDataSource")},report,params,data);
     Class<?> printType=type("net.sf.jasperreports.engine.JasperPrint");int pages=((List<?>)printType.getMethod("getPages").invoke(print)).size();if(pages<1)throw new IllegalStateException("Nenhuma página gerada");result.put("fill",true);result.put("pages",pages);
     call("net.sf.jasperreports.engine.JasperExportManager","exportReportToXmlFile",new Class<?>[]{printType,String.class,boolean.class},print,output.resolve("print.xml").toString(),false);
     String xml=new String(Files.readAllBytes(output.resolve("print.xml")),StandardCharsets.UTF_8);if(!xml.contains("Compatibilidade")||!xml.contains("Ação")||!xml.contains("Segunda Linha"))throw new IllegalStateException("Texto esperado ausente da saída");result.put("exportXml",true);
    }
   }
   if(!info.get("sha256").equals(sha(input)))throw new IllegalStateException("O arquivo original foi alterado");result.put("originalPreserved",true);result.put("ok",true);
  }catch(Throwable error){while((error instanceof InvocationTargetException||error instanceof ExceptionInInitializerError)&&error.getCause()!=null)error=error.getCause();result.put("ok",false);result.put("errorType",error.getClass().getName());result.put("error",Objects.toString(error.getMessage(),"").replaceAll("[\\r\\n]+"," "));}
  System.out.println("SALUTEM_RESULT="+json(result));if(!Boolean.TRUE.equals(result.get("ok")))System.exit(2);
 }
}
