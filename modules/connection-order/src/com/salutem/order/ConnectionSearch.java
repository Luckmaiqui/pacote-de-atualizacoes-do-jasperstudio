package com.salutem.order;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.jaspersoft.studio.data.DataAdapterDescriptor;
import com.jaspersoft.studio.data.widget.DataAdapterAction;
import org.eclipse.jface.action.ActionContributionItem;
import java.lang.reflect.Field;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/** Presents the original adapter menu items in a searchable popup; never edits storage. */
public final class ConnectionSearch {
 private Shell popup;
 public void install(Display display) {
  display.addFilter(SWT.Show,event->{
   if(event.widget instanceof Menu) {
    Menu menu=(Menu)event.widget;
    // Run after the application's Show listeners have finished populating the menu.
    display.asyncExec(()->{
     if(!menu.isDisposed() && menu.isVisible() && isAdapterMenu(menu))open(menu);
    });
   }
  });
 }
 static boolean isAdapterMenu(Menu menu) {
  for(MenuItem item:menu.getItems())if(item.getData("da.key") instanceof DataAdapterDescriptor)return true;
  return false;
 }
 static String normalize(String value) {
  return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
 }
 static boolean matches(String label,String query) {
  String value=normalize(label);
  for(String term:normalize(query).strip().split("\\s+"))if(!value.contains(term))return false;
  return true;
 }
 // The native menu belongs to a toolbar action. Focus may still be in the SQL
 // editor or a preview parameter, so it cannot be used as the dropdown anchor.
 static ToolItem findButton(Composite root,Menu menu) {
  for(Control child:root.getChildren()) {
   if(child instanceof ToolBar)for(ToolItem item:((ToolBar)child).getItems()) {
    Object contribution=item.getData();
    if(contribution instanceof ActionContributionItem) {
     Object action=((ActionContributionItem)contribution).getAction();
     if(action instanceof DataAdapterAction)try {
      Field field=DataAdapterAction.class.getDeclaredField("listMenu");field.setAccessible(true);
      if(field.get(action)==menu)return item;
     }catch(ReflectiveOperationException ignored){/* Version guard pins the supported action. */}
    }
   }
   if(child instanceof Composite){ToolItem found=findButton((Composite)child,menu);if(found!=null)return found;}
  }
  return null;
 }
 static Rectangle place(Rectangle button,Rectangle area,int preferredWidth,int preferredHeight) {
  int width=Math.min(preferredWidth,area.width),height=Math.min(preferredHeight,area.height);
  int below=button.y+button.height;
  int y=below+height<=area.y+area.height?below:button.y-height;
  return new Rectangle(Math.max(area.x,Math.min(button.x,area.x+area.width-width)),
   Math.max(area.y,Math.min(y,area.y+area.height-height)),width,height);
 }
 void open(Menu menu) {
  if(menu.isDisposed())return;
  if(popup!=null && !popup.isDisposed())popup.dispose();
  List<MenuItem> source=new ArrayList<>();
  for(MenuItem item:menu.getItems())if((item.getStyle()&SWT.SEPARATOR)==0 && item.getMenu()==null)source.add(item);
  if(source.isEmpty())return;
  Display display=menu.getDisplay();Shell owner=menu.getShell();
  ToolItem button=findButton(owner,menu);
  Point position=display.getCursorLocation();
  Rectangle anchor=new Rectangle(position.x,position.y,0,0);
  Monitor monitor=owner.getMonitor();
  if(button!=null) {
   Rectangle bounds=button.getBounds();Point origin=button.getParent().toDisplay(bounds.x,bounds.y);
   anchor=new Rectangle(origin.x,origin.y,bounds.width,bounds.height);monitor=button.getParent().getMonitor();
  }
  menu.setVisible(false);
  Shell window=new Shell(owner,SWT.ON_TOP|SWT.TOOL|SWT.RESIZE);
  popup=window;window.setText(pt()?"Pesquisar conexão":"Search connections");
  window.setLayout(new GridLayout(1,false));
  window.setBackground(owner.getBackground());window.setForeground(owner.getForeground());
  Text search=new Text(window,SWT.SEARCH|SWT.ICON_SEARCH|SWT.ICON_CANCEL|SWT.BORDER);
  search.setMessage(pt()?"Pesquisar por código ou nome…":"Search by code or name…");
  search.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  Table table=new Table(window,SWT.SINGLE|SWT.FULL_SELECTION|SWT.V_SCROLL);
  table.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
  table.setBackground(owner.getBackground());table.setForeground(owner.getForeground());
  search.setBackground(table.getBackground());search.setForeground(table.getForeground());
  Button importGestao=new Button(window,SWT.PUSH);importGestao.setText(pt()?"Importar Conexões do Gestão…":"Import connections from Gestão…");importGestao.addListener(SWT.Selection,e->{window.dispose();display.asyncExec(()->{if(!owner.isDisposed())new GestaoImportDialog(owner).open();});});
  Label status=new Label(window,SWT.NONE);status.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  status.setBackground(owner.getBackground());status.setForeground(owner.getForeground());
  Runnable filter=()->{
   table.setRedraw(false);
   try {
    table.removeAll();
    for(MenuItem original:source)if(!original.isDisposed() && matches(original.getText(),search.getText())) {
     TableItem row=new TableItem(table,SWT.NONE);row.setText(original.getText());row.setData(original);
     if(original.getImage()!=null)row.setImage(original.getImage());
     if(!original.getEnabled())row.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
    }
    if(table.getItemCount()>0)table.setSelection(0);
    int count=table.getItemCount();status.setText(count==0?(pt()?"Nenhuma conexão encontrada":"No matching connections"):
     count+(pt()?" opções — Enter: selecionar · Esc: cancelar":" choices — Enter: select · Esc: cancel"));
   }finally{table.setRedraw(true);}
  };
  Runnable accept=()->{
   if(window.isDisposed() || table.getSelectionCount()==0)return;
   MenuItem original=(MenuItem)table.getSelection()[0].getData();
   if(original.isDisposed() || !original.getEnabled())return;
   // Keep the native item's listeners and descriptor identity, including default/no-data options.
   window.dispose();
   if(!original.isDisposed())original.notifyListeners(SWT.Selection,new Event());
  };
  search.addModifyListener(e->filter.run());
  search.addListener(SWT.DefaultSelection,e->{if(e.detail!=SWT.ICON_CANCEL)accept.run();});
  Listener keys=e->{
   if(e.keyCode==SWT.ESC){e.doit=false;window.dispose();}
   else if(e.widget==search && (e.keyCode==SWT.ARROW_DOWN || e.keyCode==SWT.ARROW_UP)) {
    e.doit=false;int size=table.getItemCount();
    if(size>0){int index=table.getSelectionIndex()+(e.keyCode==SWT.ARROW_DOWN?1:-1);table.setSelection(Math.max(0,Math.min(size-1,index)));table.showSelection();}
   }else if(e.widget==table && (e.keyCode==SWT.CR || e.keyCode==SWT.KEYPAD_CR)){e.doit=false;accept.run();}
  };
  search.addListener(SWT.KeyDown,keys);table.addListener(SWT.KeyDown,keys);
  table.addListener(SWT.MouseUp,e->{if(e.button==1 && table.getItem(new Point(e.x,e.y))!=null)accept.run();});
  window.addListener(SWT.Deactivate,e->display.asyncExec(()->{if(!window.isDisposed() && display.getActiveShell()!=window)window.dispose();}));
  Listener originalDisposed=e->{if(!window.isDisposed())window.dispose();};
  menu.addListener(SWT.Dispose,originalDisposed);
  window.addListener(SWT.Dispose,e->{if(!menu.isDisposed())menu.removeListener(SWT.Dispose,originalDisposed);if(popup==window)popup=null;});
  filter.run();
  window.setBounds(place(anchor,monitor.getClientArea(),700,450));
  window.open();search.setFocus();
 }
 private boolean pt(){return Locale.getDefault().getLanguage().equals("pt");}
}
