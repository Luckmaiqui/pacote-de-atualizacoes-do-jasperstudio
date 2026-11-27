package com.salutem.l10n;
import java.util.*;
/** Equinox eclipse.commands uses newline-delimited arguments, not shell quoting. */
public final class LocaleRestartArguments {
 public static String portuguese(String commands){
  List<String> output=new ArrayList<>();String[] args=commands==null?new String[0]:commands.split("\\r?\\n");int vm=args.length;
  for(int i=0;i<args.length;i++){
   if(args[i].equals("-vmargs")){vm=i;break;}
   if(args[i].equals("-nl")){if(i+1>=args.length||args[i+1].startsWith("-"))throw new IllegalArgumentException("Argumento de idioma inválido.");i++;continue;}
   if(!args[i].isEmpty())output.add(args[i]);
  }
  output.add("-nl");output.add("pt_BR");
  for(int i=vm;i<args.length;i++)output.add(args[i]);
  return String.join("\n",output)+"\n";
 }
}
