import java.io.*;
import java.security.*;
import javax.crypto.Mac;

/** Algorithm readiness check; optional fixtures contain no user certificates or secrets. */
public final class CryptoRuntimeProbe {
 private static final char[] SYNTHETIC_PASSWORD="synthetic-fixture-only".toCharArray();
 public static void main(String[] args)throws Exception{
  if(args.length==2&&args[0].equals("create-fixture")){
   // Process-local test property, never written to java.security.
   Security.setProperty("keystore.pkcs12.macAlgorithm","HmacPBESHA256");
   KeyStore store=KeyStore.getInstance("PKCS12");store.load(null,SYNTHETIC_PASSWORD);
   try(OutputStream output=new FileOutputStream(args[1])){store.store(output,SYNTHETIC_PASSWORD);}
   System.out.println("PASS: synthetic PKCS12 fixture created");return;
  }
  Mac mac=Mac.getInstance("HmacPBESHA256");
  if(args.length==2&&args[0].equals("read-fixture")){
   KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream input=new FileInputStream(args[1])){store.load(input,SYNTHETIC_PASSWORD);}
   boolean rejected=false;try(InputStream input=new FileInputStream(args[1])){store.load(input,"incorrect-synthetic-password".toCharArray());}catch(IOException expected){rejected=true;}
   if(!rejected)throw new AssertionError("PKCS12 integrity validation was not enforced");
  }
  System.out.println("PASS: HmacPBESHA256 provider="+mac.getProvider().getName()+"; Java="+System.getProperty("java.version"));
 }
}
