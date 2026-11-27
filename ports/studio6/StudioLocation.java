package com.salutem.order;
import java.net.*;
import java.nio.file.*;
import java.io.IOException;
/** Equinox may expose an unescaped file URL pointing at plugins/, not the executable folder. */
final class StudioLocation {
 static Path executable(URL location,String launcher)throws Exception{
  if(launcher!=null&&!launcher.isEmpty()){Path file=Paths.get(launcher);if(Files.isRegularFile(file)&&file.getFileName().toString().equalsIgnoreCase("Jaspersoft Studio.exe"))return file;}
  URI uri;try{uri=location.toURI();}catch(URISyntaxException unescaped){uri=new URI(location.getProtocol(),location.getAuthority(),location.getPath(),location.getQuery(),location.getRef());}
  Path folder=Paths.get(uri);
  for(int i=0;i<3&&folder!=null;i++,folder=folder.getParent()){Path file=folder.resolve("Jaspersoft Studio.exe");if(Files.isRegularFile(file))return file;}
  throw new IOException("Executável do Studio não localizado a partir da instalação.");
 }
}
