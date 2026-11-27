package com.salutem.order;
public class GestaoPagePolicyTest {
 public static void main(String[] args){
  for(String path:new String[]{"/management","/clientes","/","/#/clients","/management/clientes"})if(!GestaoPagePolicy.allowed("https://gestao.sistemasalutem.com.br"+path))throw new AssertionError("Rota SPA bloqueada: "+path);
  for(String url:new String[]{"http://gestao.sistemasalutem.com.br/management","https://gestao.sistemasalutem.com.br.evil/management","https://gestao.sistemasalutem.com.br:8443/management","https://gestao.sistemasalutem.com.br@evil/management","invalid"})if(GestaoPagePolicy.allowed(url))throw new AssertionError("Origem incorreta aceita");
  System.out.println("PASS: rotas SPA do Gestão permitidas; HTTPS e origem exata preservados.");
 }
}
