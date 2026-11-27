import java.util.Locale;
import com.salutem.l10n.LocaleSelection;
import com.salutem.l10n.LocaleRestartArguments;
public class LocaleSelectionTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  check(LocaleSelection.portuguese("pt_BR",Locale.ENGLISH));
  check(!LocaleSelection.portuguese("en_EN",new Locale("pt","BR")));
  check(LocaleSelection.portuguese(null,new Locale("pt","BR")));
  String input="-data\nC:/workspace com espaço\n-nl\nen_EN\n-nl\nen_US\n-vmargs\n-Xmx1g\n";
  String out=LocaleRestartArguments.portuguese(input);
  check(out.equals("-data\nC:/workspace com espaço\n-nl\npt_BR\n-vmargs\n-Xmx1g\n"));
  check(LocaleRestartArguments.portuguese(out).equals(out));
  check(LocaleRestartArguments.portuguese(null).equals("-nl\npt_BR\n"));
  boolean failed=false;try{LocaleRestartArguments.portuguese("-nl\n-vmargs\n");}catch(IllegalArgumentException expected){failed=true;}check(failed);
  System.out.println("PASS: app locale precedence, JVM fallback, relaunch arguments, VM/workspace preservation, deduplication and malformed input.");
 }
}
