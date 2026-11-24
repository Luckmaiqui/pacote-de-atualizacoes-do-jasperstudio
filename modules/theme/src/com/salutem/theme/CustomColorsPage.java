package com.salutem.theme;
import java.util.*;
import java.util.List;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/** General > Appearance > Custom colours: list, change and remove block colours. */
public final class CustomColorsPage extends PreferencePage implements IWorkbenchPreferencePage {
 private final List<CustomColors.Entry> entries=new ArrayList<>();
 private final List<Color> swatches=new ArrayList<>();
 private Table table;private Button change,remove;

 @Override public void init(IWorkbench workbench){
  setDescription(CustomColors.text(
   "Cores escolhidas para blocos da interface. Funcionam com qualquer tema; o conteúdo do relatório não muda.",
   "Colours chosen for interface blocks. They work with any theme; report content is not changed."));
 }

 @Override protected Control createContents(Composite parent){
  Composite page=new Composite(parent,SWT.NONE);
  page.setLayout(new GridLayout(2,false));
  table=new Table(page,SWT.BORDER|SWT.FULL_SELECTION|SWT.SINGLE);
  table.setHeaderVisible(true);
  // Small width hint: the table grows with the dialog but never pushes the buttons out of a narrow one.
  GridData tableData=new GridData(SWT.FILL,SWT.FILL,true,true,1,5);tableData.widthHint=230;
  table.setLayoutData(tableData);
  column(CustomColors.text("Bloco","Block"),150);column(CustomColors.text("Cor","Colour"),80);
  pick(page,CustomColors.text("Escolher bloco na tela...","Pick a block on screen..."),this::pickOnScreen);
  change=pick(page,CustomColors.text("Alterar cor...","Change colour..."),this::changeColor);
  remove=pick(page,CustomColors.text("Remover","Remove"),this::removeSelected);
  Label hint=new Label(page,SWT.WRAP);
  hint.setText(CustomColors.text(
   "Também em Janela > Escolher cor de um bloco. Esc cancela a escolha.",
   "Also in Window > Pick a block colour. Esc cancels picking."));
  GridData hintData=new GridData(SWT.FILL,SWT.TOP,true,false,2,1);hintData.widthHint=200;
  hint.setLayoutData(hintData);
  table.addListener(SWT.Selection,e->buttons());
  table.addListener(SWT.DefaultSelection,e->changeColor());
  table.addListener(SWT.Dispose,e->disposeSwatches());
  entries.addAll(CustomColors.load());
  refresh();
  return page;
 }
 private void column(String title,int width){TableColumn c=new TableColumn(table,SWT.LEFT);c.setText(title);c.setWidth(width);}
 private Button pick(Composite parent,String label,Runnable action){
  Button b=new Button(parent,SWT.PUSH);b.setText(label);
  b.setLayoutData(new GridData(SWT.FILL,SWT.TOP,false,false));
  b.addListener(SWT.Selection,e->action.run());
  return b;
 }

 private void refresh(){
  int selected=table.getSelectionIndex();
  table.removeAll();disposeSwatches();
  for(CustomColors.Entry e:entries){
   TableItem item=new TableItem(table,SWT.NONE);
   item.setText(new String[]{e.label.isEmpty()?e.id:e.label,"#"+CustomColors.hex(e.rgb)});
   Color swatch=new Color(table.getDisplay(),e.rgb);swatches.add(swatch);
   item.setBackground(1,swatch);
   item.setForeground(1,table.getDisplay().getSystemColor(light(e.rgb)?SWT.COLOR_BLACK:SWT.COLOR_WHITE));
  }
  if(selected>=0 && selected<table.getItemCount())table.setSelection(selected);
  buttons();
 }
 private static boolean light(RGB rgb){return .2126*rgb.red+.7152*rgb.green+.0722*rgb.blue>140;}
 private void disposeSwatches(){for(Color c:swatches)if(!c.isDisposed())c.dispose();swatches.clear();}
 private void buttons(){boolean one=table.getSelectionIndex()>=0;change.setEnabled(one);remove.setEnabled(one);}

 private void changeColor(){
  int i=table.getSelectionIndex();if(i<0)return;
  CustomColors.Entry e=entries.get(i);
  RGB rgb=CustomColors.chooseColor(getShell(),e.rgb);
  if(rgb==null)return;
  entries.set(i,new CustomColors.Entry(e.id,e.label,rgb));refresh();
 }
 private void removeSelected(){
  int i=table.getSelectionIndex();if(i<0)return;
  entries.remove(i);refresh();
 }
 /** Saves the page, closes Preferences and starts picking on the workbench. */
 private void pickOnScreen(){
  CustomColors.save(entries);
  Display display=getShell().getDisplay();
  if(getContainer() instanceof PreferenceDialog)((PreferenceDialog)getContainer()).close();
  display.asyncExec(()->BlockPicker.start(display));
 }

 @Override public boolean performOk(){CustomColors.save(entries);return true;}
 @Override protected void performApply(){CustomColors.save(entries);}
 @Override protected void performDefaults(){entries.clear();refresh();super.performDefaults();}
}
