package com.salutem.order;
import java.nio.file.*;
/** Resolves constant expressions only; it never executes report expressions. */
public final class SubreportPaths {
 private SubreportPaths(){}
 public static String sourceName(String expression){
  if(expression==null)return null;String text=expression.strip();if(!text.matches("\"(?:[^\"\\\\]|\\\\.)*\""))return null;
  StringBuilder value=new StringBuilder();for(int i=1;i<text.length()-1;i++){char c=text.charAt(i);if(c=='\\'){char next=text.charAt(++i);if(next!='\\' && next!='\"')return null;c=next;}value.append(c);}
  String path=value.toString();if(path.startsWith("repo:") || path.startsWith("http:") || path.startsWith("https:"))return null;
  if(path.toLowerCase(java.util.Locale.ROOT).endsWith(".jasper"))return path.substring(0,path.length()-7)+".jrxml";
  return path.toLowerCase(java.util.Locale.ROOT).endsWith(".jrxml")?path:null;
 }
 public static Path resolve(Path mainFile,String expression){String name=sourceName(expression);if(name==null || mainFile==null || mainFile.getParent()==null)return null;try{Path path=Path.of(name);return (path.isAbsolute()?path:mainFile.getParent().resolve(path)).normalize();}catch(InvalidPathException error){return null;}}
}
