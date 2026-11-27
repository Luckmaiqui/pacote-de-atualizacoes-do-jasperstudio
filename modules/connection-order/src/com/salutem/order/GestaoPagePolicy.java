package com.salutem.order;
/** The client list is a SPA: its route is not an authentication signal. */
final class GestaoPagePolicy {
 static boolean allowed(String source){
  try{java.net.URI uri=java.net.URI.create(source);return "https".equalsIgnoreCase(uri.getScheme())&&"gestao.sistemasalutem.com.br".equalsIgnoreCase(uri.getHost())&&(uri.getPort()==-1||uri.getPort()==443);}catch(RuntimeException invalid){return false;}
 }
}
