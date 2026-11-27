package com.salutem.order;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.ws.rs.client.*;
import org.glassfish.jersey.client.*;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.eclipse.core.runtime.NullProgressMonitor;

/** Real JAX-RS requests against a synthetic loopback repository. Never contacts a JasperServer. */
public final class PublicationWireTest {
 public static final class Rest {private final WebTarget root;Rest(WebTarget root){this.root=root;}public WebTarget getTarget(){return root;}}
 static final class Repository implements AutoCloseable {
  final ServerSocket socket;final Thread worker;volatile Throwable failure;volatile boolean closed,exists;int version,patches,conflicts=1;String description="Descrição antiga\r\ncom espaços  ";
  Repository()throws Exception{socket=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));worker=new Thread(this::serve,"publication-synthetic-http");worker.setDaemon(true);worker.start();}
  void serve(){while(!closed)try(Socket connection=socket.accept()){
   connection.setSoTimeout(5000);InputStream input=connection.getInputStream();String first=line(input);Map<String,String> headers=new HashMap<>();String header;while(!(header=line(input)).isEmpty()){int colon=header.indexOf(':');headers.put(header.substring(0,colon).toLowerCase(Locale.ROOT),header.substring(colon+1).trim());}
   ByteArrayOutputStream content=new ByteArrayOutputStream();
   if("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))){for(int size;(size=Integer.parseInt(line(input).split(";")[0],16))>0;){copy(input,content,size);line(input);}while(!line(input).isEmpty()){} }
   else copy(input,content,Integer.parseInt(headers.getOrDefault("content-length","0")));
   byte[] bytes=content.toByteArray();
   if(!first.contains("/rest_v2/resources/reports/Teste%20Hist"))throw new AssertionError("URI incorreta: "+first);
   if(!"session=synthetic".equals(headers.get("cookie")))throw new AssertionError("Sessão do target não foi reutilizada");
   int status=200;String response="";
   if(first.startsWith("GET ")){if(!exists)status=404;else response=resource();}
   else if(first.startsWith("PATCH ")){
    JsonObject json=new JsonParser().parse(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
    if(json.size()!=2||!json.has("version")||!json.has("patch"))throw new AssertionError("PATCH contém campos adicionais");
    JsonArray items=json.getAsJsonArray("patch");JsonObject item=items.get(0).getAsJsonObject();if(items.size()!=1||item.size()!=2||!item.get("field").getAsString().equals("description")||item.has("expression"))throw new AssertionError("PATCH alterou outro campo");
    if(conflicts-->0){description+="\nOutra publicação";version++;status=409;}
    else if(json.get("version").getAsInt()!=version)status=409;
    else{patches++;description=item.get("value").getAsString();version++;response=resource();}
   }else throw new AssertionError("Operação inesperada: "+first);
   byte[] body=response.getBytes(StandardCharsets.UTF_8);OutputStream out=connection.getOutputStream();out.write(("HTTP/1.1 "+status+" Test\r\nContent-Type: application/repository.reportUnit+json\r\nContent-Length: "+body.length+"\r\nDate: Wed, 25 Nov 2026 20:02:00 GMT\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
  }catch(Throwable error){if(!closed){failure=error;error.printStackTrace();}}}
  String resource(){JsonObject json=new JsonObject();json.addProperty("version",version);json.addProperty("description",description);json.addProperty("label","Não alterar");json.addProperty("jrxmlFileReference","Não alterar");return json.toString();}
  static String line(InputStream input)throws Exception{ByteArrayOutputStream data=new ByteArrayOutputStream();for(int c;(c=input.read())!=-1;){if(c=='\n')return data.toString("US-ASCII").replace("\r","");data.write(c);}throw new EOFException();}
  static void copy(InputStream input,OutputStream output,int size)throws Exception{for(int i=0;i<size;i++){int value=input.read();if(value<0)throw new EOFException();output.write(value);}}
  public void close()throws Exception{closed=true;socket.close();worker.join(1000);}
 }
 public static void main(String[] args)throws Exception{
  try(Repository repository=new Repository()){
   // Only strings are exchanged; disable automatic unrelated JAXB/Jackson providers in this fixture.
   Client client=new JerseyClientBuilder().withConfig(new ClientConfig().property("jersey.config.disableAutoDiscovery",true).property("jersey.config.disableMetainfServicesLookup",true).connectorProvider(new ApacheConnectorProvider())).build();
   try{
    client.register((ClientRequestFilter)request->request.getHeaders().putSingle("Cookie","session=synthetic"));
    Rest rest=new Rest(client.target("http://127.0.0.1:"+repository.socket.getLocalPort()+"/rest_v2"));
    PublicationDescriptionStore store=new PublicationDescriptionStore(rest,"/reports/Teste Hist",new NullProgressMonitor());
    PublicationHistory.Session session=new PublicationHistory.Session(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
    session.execute("test-server/report","Usuário de Teste",ZoneId.of("America/Sao_Paulo"),store,()->repository.exists=true,()->false);
    String expected="25/11/2026 17:02 - (Usuário de Teste) - Alteração no select e/ou design.";
    if(repository.failure!=null)throw new AssertionError(repository.failure);
    if(!repository.description.equals("Descrição antiga\r\ncom espaços  \nOutra publicação\n"+expected)||repository.patches!=1)throw new AssertionError("HTTP não preservou histórico/conflito/relógio do servidor: "+repository.description);
    session.execute("test-server/report","Usuário de Teste",ZoneId.of("America/Sao_Paulo"),store,()->{},()->false);
    if(repository.patches!=1)throw new AssertionError("Reprocessamento enviou outro PATCH");
    System.out.println("PASS: JAX-RS real em loopback; URI, sessão e data do servidor; PATCH exclusivo description/version; conflito relido; histórico UTF-8/CRLF e repetição idempotente.");
   }finally{client.close();}
  }
 }
}
