package com.salutem.order;
import java.util.*;
public final class RemoteSubreportMatcher {
 public record Resource(String name,String type,String uri,Object nativeValue){}
 public static Resource choose(String expression,List<Resource> resources){
  String source=SubreportPaths.sourceName(expression);if(source==null)return null;String file=source.replace('\\','/');file=file.substring(file.lastIndexOf('/')+1);String stem=file.substring(0,file.length()-6);
  List<Resource> matches=new ArrayList<>();Set<String> uris=new HashSet<>();for(Resource r:resources)if("jrxml".equalsIgnoreCase(r.type()) && (r.name().equals(file)||r.name().equals(stem)) && (r.uri().isBlank() || uris.add(r.uri())))matches.add(r);
  if(matches.size()>1)throw new IllegalStateException("Há mais de um fonte com esse nome na unidade de relatório. Abra o recurso desejado no Explorador de Repositórios.");return matches.isEmpty()?null:matches.get(0);
 }
}
