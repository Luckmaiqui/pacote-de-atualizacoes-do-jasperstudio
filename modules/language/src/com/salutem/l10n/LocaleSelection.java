package com.salutem.l10n;
import java.util.Locale;
/** The application selection takes precedence over the JVM's regional defaults. */
public final class LocaleSelection {
 public static boolean portuguese(String selected,Locale fallback){
  if(selected==null||selected.trim().isEmpty())return "pt".equals(fallback.getLanguage());
  String value=selected.replace('-','_').toLowerCase(Locale.ROOT);
  return value.equals("pt")||value.startsWith("pt_");
 }
}
