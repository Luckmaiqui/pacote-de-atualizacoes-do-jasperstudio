package com.salutem.order;
import java.util.*;import java.lang.reflect.Field;import org.eclipse.ui.IStartup;import org.eclipse.swt.SWT;import org.eclipse.swt.widgets.Display;import com.jaspersoft.studio.data.DataAdapterManager;import com.jaspersoft.studio.data.DataAdapterDescriptor;import com.jaspersoft.studio.data.storage.ADataAdapterStorage;
/** Orders the existing LinkedHashMap in place; descriptor objects, keys and selections are retained. */
public final class Startup implements IStartup {
 private final NumericOrder comparator=new NumericOrder();private Field storagesField,descriptorsField;private boolean reported,queued;
 private final Set<ADataAdapterStorage> observed=Collections.newSetFromMap(new WeakHashMap<>());
 public void earlyStartup(){Display display=Display.getDefault();display.asyncExec(()->{
  new ConnectionSearch().install(display);
  ExplorerSearch.install(display);
  new ConnectionUi().install(display);
  new SubreportOpener().install(display);
  try{
   storagesField=DataAdapterManager.class.getDeclaredField("storages");storagesField.setAccessible(true);
   descriptorsField=ADataAdapterStorage.class.getDeclaredField("daDescriptors");descriptorsField.setAccessible(true);
   DataAdapterManager.getPreferencesStorage();sortAll(display);
   // Menu filters run before application Show listeners populate adapter choices.
   display.addFilter(SWT.Show,e->{if(e.widget instanceof org.eclipse.swt.widgets.Menu)sortAll(display);});
   display.timerExec(5000,new Runnable(){public void run(){if(display.isDisposed())return;try{sortAll(display);}finally{if(!display.isDisposed())display.timerExec(5000,this);}}});
  }catch(ReflectiveOperationException|RuntimeException error){report(error);}
 });}
 @SuppressWarnings("unchecked") private void sortAll(Display display){
  try{RepositoryOrder.attachOpenViews();ExplorerSearch.attachOpenViews();}catch(RuntimeException error){report(error);}
  if(storagesField==null||descriptorsField==null)return;
  try{
   Map<?,?> stores=(Map<?,?>)storagesField.get(null);
   for(Object value:new ArrayList<>(stores.values()))if(value instanceof ADataAdapterStorage){
    ADataAdapterStorage storage=(ADataAdapterStorage)value;
    // JR defaults are generated per dataset and contain at most one active adapter.
    if(storage.getClass().getSimpleName().equals("JRDefaultDataAdapterStorage"))continue;
    if(observed.add(storage))storage.addPropertyChangeListener(event->{if(!display.isDisposed())display.asyncExec(()->requestSort(display));});
    Object map=descriptorsField.get(storage);
    if(map instanceof LinkedHashMap)comparator.sort((Map<String,DataAdapterDescriptor>)map,DataAdapterDescriptor::getName);
   }
  }catch(ReflectiveOperationException|RuntimeException error){report(error);}
 }
 private void requestSort(Display display){if(queued || display.isDisposed())return;queued=true;display.timerExec(100,()->{queued=false;if(!display.isDisposed())sortAll(display);});}
 private void report(Exception error){if(!reported){reported=true;System.err.println("Salutem numeric connections: "+error);}}
}
