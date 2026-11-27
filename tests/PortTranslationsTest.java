import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Synthetic hosts prove resource migration, not Studio UI compatibility. */
public final class PortTranslationsTest {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void properties(Path file,Properties values)throws Exception{Files.createDirectories(file.getParent());try(OutputStream stream=Files.newOutputStream(file)){values.store(stream,null);}}
 static Properties values(String... entries){Properties p=new Properties();for(int i=0;i<entries.length;i+=2)p.setProperty(entries[i],entries[i+1]);return p;}
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]);Files.createDirectories(root);
  String host="example.studio",resource="messages.properties",localized="messages_pt_BR.properties";
  Path plugins=root.resolve("plugins"),baseline=root.resolve("baseline"),output=root.resolve("result");
  Files.createDirectories(plugins);Files.createDirectories(baseline);Files.createDirectories(root.resolve("vendor/eclipse-babel"));
  Properties english=values("stable","Save {0}","changed","New meaning","unsafe","Value {1}","new","Brand new","unicode","Language");
  Manifest manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Bundle-SymbolicName",host);manifest.getMainAttributes().putValue("Bundle-Version","6.6.0.final");
  Path target=plugins.resolve(host+".jar");
  try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(target),manifest)){jar.putNextEntry(new JarEntry(resource));english.store(jar,null);jar.closeEntry();}
  byte[] before=Files.readAllBytes(target);
  Path fragment=root.resolve("translations/fragments/com.salutem.l10n."+host+".pt_BR");
  properties(fragment.resolve(localized),values("stable","Salvar {0}","changed","Significado antigo","unsafe","Valor {0}","unicode","Linguagem"));
  properties(root.resolve("translations/reference/"+host+"/"+resource),values("stable","Save {0}","changed","Old meaning","unsafe","Value {1}","unicode","Language"));
  Manifest nested=new Manifest(manifest);nested.getMainAttributes().putValue("Bundle-SymbolicName","example.samples");nested.getMainAttributes().putValue("Fragment-Host",host+";bundle-version=\"6.6.0\"");
  try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(plugins.resolve("samples.jar")),nested)){jar.putNextEntry(new JarEntry("samples/messages.properties"));values("sample","Sample").store(jar,null);jar.closeEntry();}
  properties(root.resolve("translations/fragments/com.salutem.l10n.example.samples.pt_BR/samples/messages_pt_BR.properties"),values("sample","Exemplo"));
  properties(root.resolve("translations/reference/example.samples/samples/messages.properties"),values("sample","Sample"));
  PortTranslations.main(new String[]{root.toString(),plugins.toString(),output.toString(),baseline.toString(),"1.0.0.port660"});
  Path generated=output.resolve(fragment.getFileName());Properties actual=PortTranslations.read(generated.resolve(localized));
  require(actual.stringPropertyNames().equals(english.stringPropertyNames()),"Keys lost or invented");
  require("Salvar {0}".equals(actual.getProperty("stable")),"Unchanged English was not translated");
  require("New meaning".equals(actual.getProperty("changed")),"Changed meaning reused an obsolete translation");
  require("Value {1}".equals(actual.getProperty("unsafe")),"Invalid placeholders accepted");
  require("Brand new".equals(actual.getProperty("new")),"Unknown key lost its readable fallback");
  require("Linguagem".equals(actual.getProperty("unicode")),"Portuguese resource corrupted");
  require("Exemplo".equals(PortTranslations.read(generated.resolve("samples/messages_pt_BR.properties")).getProperty("sample")),"Fragment translation was not attached to its real host");
  require(!Files.exists(output.resolve("com.salutem.l10n.example.samples.pt_BR")),"Invalid fragment-of-fragment generated");
  try(InputStream stream=Files.newInputStream(generated.resolve("META-INF/MANIFEST.MF"))){Manifest result=new Manifest(stream);require((host+";bundle-version=\"[6.6.0.final,6.6.0.final]\"").equals(result.getMainAttributes().getValue("Fragment-Host")),"Host version not exactly pinned");}
  require(Arrays.equals(before,Files.readAllBytes(target)),"Original host changed");
  System.out.println("PASS: versioned translation, changed English fallback, placeholder integrity, complete keys and original host preserved.");
 }
}
