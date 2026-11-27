import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Rebuild resource fragments against the target host's real keys/values.
 * Reuses a translation only for unchanged English text; unknown text remains
 * readable English and is counted for review instead of being guessed. */
public final class PortTranslations {
 static Properties read(InputStream input)throws IOException{Properties p=new Properties();if(input!=null)try(InputStream s=input){p.load(s);}return p;}
 static Properties read(Path path)throws IOException{return read(Files.newInputStream(path));}
 static String escape(String s){StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\'||c=='='||c==':'||c=='#'||c=='!'||c==' ')b.append('\\').append(c);else if(c=='\n')b.append("\\n");else if(c=='\r')b.append("\\r");else if(c=='\t')b.append("\\t");else if(c<32||c>126)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}return b.toString();}
 static String params(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{\\d[^}]*\\}|\\$[PFRV]\\{[^}]*\\}|%(?:\\d+\\$)?[sdif]").matcher(value);List<String> result=new ArrayList<>();while(m.find())result.add(m.group());Collections.sort(result);return result.toString();}
 static void write(Path path,Properties p)throws IOException{Files.createDirectories(path.getParent());try(Writer w=Files.newBufferedWriter(path,java.nio.charset.StandardCharsets.US_ASCII)){for(String key:new TreeSet<>(p.stringPropertyNames()))w.write(escape(key)+"="+escape(p.getProperty(key))+"\n");}}
 static Map<String,Path> bundles(Path plugins)throws Exception{Map<String,Path> result=new HashMap<>();try(java.util.stream.Stream<Path> files=Files.list(plugins)){for(Path file:(Iterable<Path>)files.filter(p->p.toString().endsWith(".jar"))::iterator)try(JarFile jar=new JarFile(file.toFile())){Manifest mf=jar.getManifest();if(mf!=null){String id=mf.getMainAttributes().getValue("Bundle-SymbolicName");if(id!=null)result.put(id.split(";")[0],file);}}}return result;}
 static String attachmentHost(String id,Map<String,Path> hosts)throws Exception{
  Set<String> visited=new HashSet<>();
  while(true){
   if(!visited.add(id))throw new IOException("Fragment host cycle: "+id);
   Path file=hosts.get(id);if(file==null)throw new IOException("Fragment host unavailable: "+id);
   try(JarFile jar=new JarFile(file.toFile())){String parent=jar.getManifest().getMainAttributes().getValue("Fragment-Host");if(parent==null)return id;id=parent.split(";")[0].trim();}
  }
 }
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]),plugins=Paths.get(args[1]),output=Paths.get(args[2]),baselinePlugins=Paths.get(args[3]);
  String qualifier=args[4];Map<String,Path> hosts=bundles(plugins),baseline=bundles(baselinePlugins);
  int resources=0,keys=0,translated=0,review=0,fragments=0;
  List<String> coverage=new ArrayList<>();coverage.add("host\tresource\tkeys\ttranslated\tenglishForReview");
  List<Path> sources=new ArrayList<>();try(java.util.stream.Stream<Path> list=Files.list(root.resolve("translations/fragments"))){for(Path p:(Iterable<Path>)list.filter(Files::isDirectory)::iterator)sources.add(p);}
  // Include Eclipse translations using a baseline host as the English reference.
  Path babel=root.resolve("vendor/eclipse-babel");try(java.util.stream.Stream<Path> list=Files.list(babel)){for(Path p:(Iterable<Path>)list.filter(p->p.toString().endsWith(".jar"))::iterator)sources.add(p);}
  sources.sort(Comparator.comparing(p->(Files.isDirectory(p)?"1":"0")+p.getFileName().toString()));
  Map<String,Map<String,Properties>> translations=new TreeMap<>();
  Map<String,String> owners=new HashMap<>();
  for(Path source:sources){
   boolean own=Files.isDirectory(source);String host;
   if(own){String id=source.getFileName().toString();host=id.substring("com.salutem.l10n.".length(),id.length()-".pt_BR".length());}
   else{try(JarFile jar=new JarFile(source.toFile())){String value=jar.getManifest().getMainAttributes().getValue("Fragment-Host");if(value==null)continue;host=value.split(";")[0];}}
   if(!hosts.containsKey(host))continue;
   try(JarFile targetJar=new JarFile(hosts.get(host).toFile());JarFile baselineJar=baseline.containsKey(host)?new JarFile(baseline.get(host).toFile()):null){
    List<String> paths=new ArrayList<>();JarFile sourceJar=null;
    try{
     if(own){try(java.util.stream.Stream<Path> walk=Files.walk(source)){for(Path p:(Iterable<Path>)walk.filter(p->p.toString().endsWith("_pt_BR.properties"))::iterator)paths.add(source.relativize(p).toString().replace('\\','/'));}}
     else{sourceJar=new JarFile(source.toFile());Enumeration<JarEntry> entries=sourceJar.entries();while(entries.hasMoreElements()){String name=entries.nextElement().getName();if(name.endsWith("_pt_BR.properties"))paths.add(name);}}
     for(String name:paths){
      String englishName=name.replace("_pt_BR.properties",".properties");JarEntry targetEntry=targetJar.getJarEntry(englishName);if(targetEntry==null)continue;
      Properties en=read(targetJar.getInputStream(targetEntry)),pt=own?read(source.resolve(name)):read(sourceJar.getInputStream(sourceJar.getJarEntry(name))),reference;
      if(own){Path ref=root.resolve("translations/reference").resolve(host).resolve(englishName);if(!Files.exists(ref))continue;reference=read(ref);}
      else{JarEntry ref=baselineJar==null?null:baselineJar.getJarEntry(englishName);if(ref==null)continue;reference=read(baselineJar.getInputStream(ref));}
      Properties result=new Properties();int changed=0,pending=0;
      for(String key:en.stringPropertyNames()){
       String original=en.getProperty(key),value=original,candidate=pt.getProperty(key);
       if(candidate!=null && original.equals(reference.getProperty(key)) && params(original).equals(params(candidate)))value=candidate;
       if(!value.equals(original))changed++;else if(original.matches(".*[A-Za-z]{3}.*"))pending++;
       result.setProperty(key,value);
      }
      String attachment=attachmentHost(host,hosts),key=attachment+"/"+name,previous=owners.put(key,host);
      if(previous!=null && !previous.equals(host))throw new IOException("Conflicting fragment resource: "+key+" from "+previous+" and "+host);
      translations.computeIfAbsent(attachment,k->new TreeMap<>()).put(name,result);coverage.add(host+"\t"+name+"\t"+en.size()+"\t"+changed+"\t"+pending);
      resources++;keys+=en.size();translated+=changed;review+=pending;
     }
    }finally{if(sourceJar!=null)sourceJar.close();}
   }
  }
  for(Map.Entry<String,Map<String,Properties>> entry:translations.entrySet()){
   String host=entry.getKey(),id="com.salutem.l10n."+host+".pt_BR";String version;
   try(JarFile jar=new JarFile(hosts.get(host).toFile())){version=jar.getManifest().getMainAttributes().getValue("Bundle-Version");}
   Path fragment=output.resolve(id);Files.createDirectories(fragment.resolve("META-INF"));
   Manifest manifest=new Manifest();java.util.jar.Attributes a=manifest.getMainAttributes();a.putValue("Manifest-Version","1.0");a.putValue("Bundle-ManifestVersion","2");a.putValue("Bundle-SymbolicName",id+";singleton:=true");a.putValue("Bundle-Version",qualifier);a.putValue("Fragment-Host",host+";bundle-version=\"["+version+","+version+"]\"");a.putValue("Bundle-Name","Salutem pt_BR - "+host);
   try(OutputStream s=Files.newOutputStream(fragment.resolve("META-INF/MANIFEST.MF"))){manifest.write(s);}
   for(Map.Entry<String,Properties> resource:entry.getValue().entrySet())write(fragment.resolve(resource.getKey()),resource.getValue());fragments++;
  }
  Files.createDirectories(output);Files.write(output.resolve("coverage.tsv"),coverage,java.nio.charset.StandardCharsets.UTF_8);
  System.out.println("fragments="+fragments+" resources="+resources+" keys="+keys+" translated="+translated+" englishForReview="+review);
 }
}
