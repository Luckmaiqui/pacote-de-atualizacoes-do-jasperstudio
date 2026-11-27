package com.salutem.order;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.List;
import org.eclipse.swt.widgets.*;

/** Loads and renders a JSON export; never tests/adds a connection or reads credentials. */
public final class CheckGestaoImport {
 public static void main(String[] args)throws Exception{
  String file=System.getenv("SALUTEM_TEST_GESTAO_JSON");
  String json=file==null?"{\"format\":\"salutem-gestao-v1\",\"source\":\"https://gestao.sistemasalutem.com.br/management\",\"connections\":[{\"code\":\"1\",\"client\":\"Teste\",\"label\":\"Hospitalar\",\"kind\":\"DATABASE\",\"endpoint\":\"localhost:5432\"}]}":Files.readString(Path.of(file));
  int expected=GestaoCatalog.parse(json).size();
  Shell shell=new Shell(Display.getDefault());GestaoImportDialog dialog=new GestaoImportDialog(shell);
  try{dialog.create();Method load=GestaoImportDialog.class.getDeclaredMethod("load",String.class);load.setAccessible(true);load.invoke(dialog,json);
   List<?> rows=(List<?>)ConnectionUi.field(dialog,"rows");Table table=(Table)ConnectionUi.field(dialog,"table");
   if(rows.size()!=expected||expected==0||table.getItemCount()!=expected||!Boolean.TRUE.equals(ConnectionUi.field(dialog,"registryReady")))throw new AssertionError("JSON did not initialize the complete review table");
   Text filter=(Text)ConnectionUi.field(dialog,"filterText");filter.setText("no-match-fixture-928178");if(table.getItemCount()!=0)throw new AssertionError("Filter did not remove nonmatches");filter.setText("");if(table.getItemCount()!=expected)throw new AssertionError("Clearing filter lost rows");
   System.out.println("PASS: JSON parsed and rendered in the real import dialog; rows="+expected+"; no connection, credential or server write performed.");
  }finally{dialog.close();shell.dispose();}
  Shell reopenedShell=new Shell(Display.getDefault());GestaoImportDialog reopened=new GestaoImportDialog(reopenedShell);try{reopened.create();Method load=GestaoImportDialog.class.getDeclaredMethod("load",String.class);load.setAccessible(true);load.invoke(reopened,json);if(((Table)ConnectionUi.field(reopened,"table")).getItemCount()!=expected||!((Text)ConnectionUi.field(reopened,"filterText")).getText().isEmpty())throw new AssertionError("Stale filter on reopen");}finally{reopened.close();reopenedShell.dispose();}
  System.out.println("PASS: import filter, clearing and reopening preserve the complete data set.");
 }
}
