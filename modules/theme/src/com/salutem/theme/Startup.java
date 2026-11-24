package com.salutem.theme;
import org.eclipse.ui.IStartup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.custom.CCombo;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.IFigure;
import org.eclipse.ui.forms.FormColors;
import org.eclipse.ui.forms.IFormColors;
import org.eclipse.swt.graphics.Color;
import org.eclipse.core.runtime.preferences.InstanceScope;

/** Complements CSS only while Salutem Noite is selected. Never visits Draw2D report figures. */
public final class Startup implements IStartup {
 private Color fg, bg, border, accent, muted;
 private String paletteId;
 private boolean initialized;
 private boolean visibleOnly,colorsRegistered;
 private final java.util.Map<Integer,Color> colorPool=new java.util.HashMap<>();
 private java.lang.reflect.Field customComboList;
 public void earlyStartup() {
  Display d=Display.getDefault();
  // Block colours chosen by the user apply to any theme, not only Salutem palettes.
  d.asyncExec(()->CustomColors.install(d));
  PaletteSurface.theme=()->bg!=null && activeTheme()?new Color[]{bg,fg,accent}:null;
  d.asyncExec(new Runnable() { public void run() {
   if(d.isDisposed()) return;
   ThemePalette palette=ThemePalette.find(InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").get("themeid", ""));
   boolean active=palette!=null;
   if(active) {
    if(!palette.id.equals(paletteId))applyPalette(d,palette);
    if(!initialized) {

     d.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", Boolean.TRUE);
     d.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor", fg);
     d.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor", bg);
     d.setData("org.eclipse.swt.internal.win32.menuBarBorderColor", border);
     org.eclipse.core.runtime.preferences.IEclipsePreferences editors=InstanceScope.INSTANCE.getNode("org.eclipse.ui.editors");
     editors.put("lineNumberRulerColor",rgbString(palette.muted));
     editors.put("AbstractTextEditor.Color.Background",rgbString(palette.background));
     editors.putBoolean("AbstractTextEditor.Color.Background.SystemDefault",false);
     editors.put("AbstractTextEditor.Color.Foreground",rgbString(palette.foreground));
     editors.putBoolean("AbstractTextEditor.Color.Foreground.SystemDefault",false);
     editors.put("currentLineColor",rgbString(palette.accent));
     d.setData("org.eclipse.swt.internal.win32.Combo.useDarkTheme",Boolean.TRUE);
     d.setData("org.eclipse.swt.internal.win32.Table.headerLineColor",border);
     d.addFilter(SWT.Show,event->{if(activeTheme() && event.widget instanceof Menu){Menu opening=(Menu)event.widget;menu(opening);d.asyncExec(()->{if(activeTheme() && !opening.isDisposed() && opening.isVisible())menu(opening);});}});
     d.addFilter(SWT.MouseDown,event->{if(activeTheme() && (event.widget instanceof Combo || event.widget instanceof org.eclipse.swt.widgets.List))nativeCombo((Control)event.widget);});
     RefreshQueue queue=new RefreshQueue(d,Startup.this::visitVisible);
     org.eclipse.swt.widgets.Listener changes=e->{if(activeTheme() && e.widget instanceof Control){Control c=(Control)e.widget;queue.request(c instanceof StyledText?c.getParent():c);}};
     for(int type:new int[]{SWT.Show,SWT.Selection,SWT.Expand,SWT.FocusIn,SWT.Modify})d.addFilter(type,changes);

     configureDisabledLabels(d);
     initialized=true;
    }
    try {for(Shell s:d.getShells()) visitVisible(s);}
    finally {if(!d.isDisposed())d.timerExec(3000,this);}
    return;
   }
   d.timerExec(3000,this);
  }});
 }
 private Color rgb(Display display,int value){
  if(!colorsRegistered){colorsRegistered=true;display.disposeExec(()->{for(Color c:colorPool.values())if(!c.isDisposed())c.dispose();colorPool.clear();});}
  return colorPool.computeIfAbsent(value,key->new Color(display,(key>>16)&255,(key>>8)&255,key&255));
 }
 private String rgbString(int value){return ((value>>16)&255)+","+((value>>8)&255)+","+(value&255);}
 private void applyPalette(Display display,ThemePalette palette){
  paletteId=palette.id;fg=rgb(display,palette.foreground);bg=rgb(display,palette.background);border=rgb(display,palette.border);accent=rgb(display,palette.accent);muted=rgb(display,palette.muted);
  syntaxColors.clear();
  display.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor",fg);
  display.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor",bg);
  display.setData("org.eclipse.swt.internal.win32.menuBarBorderColor",border);
  display.setData("org.eclipse.swt.internal.win32.Table.headerLineColor",border);
  configureDisabledLabels(display);
  org.eclipse.core.runtime.preferences.IEclipsePreferences editors=InstanceScope.INSTANCE.getNode("org.eclipse.ui.editors");
  editors.put("lineNumberRulerColor",rgbString(palette.muted));
  editors.put("AbstractTextEditor.Color.Background",rgbString(palette.background));
  editors.put("AbstractTextEditor.Color.Foreground",rgbString(palette.foreground));
  editors.put("currentLineColor",rgbString(palette.accent));
 }
 private boolean dark(Color c) { return c.getRed()+c.getGreen()+c.getBlue()<330; }
 /** Palette background, unless the user chose a colour for this control's block (CustomColors). */
 private Color surface(Control c) { return CustomColors.background(c,bg); }
 /** Palette text colour, or black/white when content sits on a chosen background it would be hard to read on. */
 private Color text(Control c) { return text(c,fg); }
 private Color text(Control c,Color theme) { return CustomColors.foreground(c,theme); }
 private void visitVisible(Control c){
  if(c.isDisposed())return;
  ThemePalette palette=ThemePalette.find(InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").get("themeid", ""));
  if(palette==null)return;if(!palette.id.equals(paletteId))applyPalette(c.getDisplay(),palette);
  boolean previous=visibleOnly;visibleOnly=true;try{visit(c);}finally{visibleOnly=previous;}
 }
 private void visit(Control c) {
  if(c.isDisposed() || (visibleOnly && !c.isVisible()))return;
  if(inProperties(c) || c.getClass().getSimpleName().equals("TabbedPropertyTitle")) propertyColors(c);
  try { extras(c); }
  catch(RuntimeException error) {
   if(c.getData("salutem.errorReported")==null) {
    c.setData("salutem.errorReported",Boolean.TRUE);
    System.err.println("Salutem theme: "+c.getClass().getName()+": "+error);
   }
  }
  if(c instanceof Label && (c.getStyle()&SWT.SEPARATOR)==0)disabledLabel((Label)c);
  if(c instanceof Text || c instanceof Combo || c instanceof CCombo) {
   if(dark(c.getBackground()) && dark(c.getForeground())) c.setForeground(fg);
  }
  if(c instanceof Button && (c.getStyle()&(SWT.CHECK|SWT.RADIO))!=0) {
   Color parent=c.getParent().getBackground();
   if(dark(parent)) { if(!c.getBackground().equals(parent))c.setBackground(parent); if(CustomColors.background(c)==null && dark(c.getForeground()))c.setForeground(fg); }
  }
  if(c instanceof Sash && !c.getBackground().equals(border))c.setBackground(border);
  if(c instanceof Composite) for(Control child:((Composite)c).getChildren())visit(child);
 }
 private void extras(Control c) {
  String name=c.getClass().getName();
  if(Boolean.TRUE.equals(c.getData("salutem.accountSurface")))publishColors(c);
  if(inProgressPanel(c))publishColors(c);
  if(isPublishingDatasource(c) || (c instanceof TabFolder && containsDatasourcePanel((Composite)c,0))) publishColors(c);
  if(name.startsWith("org.eclipse.nebula.widgets.gallery.")) gallery(c);
  if(c instanceof Scale || c instanceof Slider) {
   c.setBackground(bg);c.setForeground(fg);c.getParent().setBackground(surface(c.getParent()));
  }
  if(c instanceof Scrollable && c.getData("salutem.nativeTheme")==null) {
   if(c instanceof Combo || c instanceof org.eclipse.swt.widgets.List)nativeCombo(c);
   else org.eclipse.swt.internal.win32.OS.SetWindowTheme(c.handle,"DarkMode_Explorer\0".toCharArray(),null);
   c.setData("salutem.nativeTheme",Boolean.TRUE);
  }
  if(name.contains("FlyoutPaletteComposite$TitleCanvas") && c instanceof Canvas && c.getData("salutem.titlePaint")==null) {
   c.setData("salutem.titlePaint",Boolean.TRUE);
   ((Canvas)c).addPaintListener(e->{if(activeTheme()){
    e.gc.setBackground(bg);e.gc.setForeground(fg);e.gc.fillRectangle(((Canvas)c).getClientArea());
    e.gc.drawText(java.util.Locale.getDefault().getLanguage().equals("pt")?"Paleta":"Palette",24,3,true);
   }});
  }
  if(c instanceof Shell) {Menu m=((Shell)c).getMenuBar(); if(m!=null)menu(m);}
  if(c.getMenu()!=null)menu(c.getMenu());
  if(c instanceof Tree) {
   Tree t=(Tree)c;Color back=surface(t),text=text(t);
   if(t.getData("salutem.strongSelection")==null){
    t.setData("salutem.strongSelection",Boolean.TRUE);
    t.addListener(SWT.EraseItem,event->{if(activeTheme() && (event.detail&SWT.SELECTED)!=0){
     event.gc.setBackground(selectionColor());event.gc.setForeground(fg);event.gc.fillRectangle(event.x,event.y,event.width,event.height);event.detail&=~(SWT.SELECTED|SWT.HOT);
    }});
   }
   t.setLinesVisible(false);t.setHeaderBackground(back);t.setHeaderForeground(text);
   for(TreeItem i:t.getItems())uniformItem(i,Math.max(1,t.getColumnCount()),back,text);
  }
  if(c instanceof org.eclipse.swt.custom.CTabFolder) {
   org.eclipse.swt.custom.CTabFolder tabs=(org.eclipse.swt.custom.CTabFolder)c;
   tabs.setSelectionBackground(tabs.getItemCount()==0?surface(c):bg);tabs.setSelectionForeground(fg);
  }
  if(c instanceof org.eclipse.swt.custom.CLabel) {
   ((org.eclipse.swt.custom.CLabel)c).setBackground(surface(c));c.setForeground(text(c));
  }
  if(c instanceof CCombo) {
   CCombo combo=(CCombo)c;
   try{
    if(customComboList==null){customComboList=CCombo.class.getDeclaredField("list");customComboList.setAccessible(true);}
    Object popup=customComboList.get(combo);if(popup instanceof org.eclipse.swt.widgets.List)nativeCombo((Control)popup);
   }catch(ReflectiveOperationException ignored){ }
   for(Control child:combo.getChildren()) if(child instanceof Text) {
    Text field=(Text)child;if(!combo.getEditable())field.clearSelection();field.setBackground(bg);field.setForeground(fg);
   }
  }
  if(c instanceof Combo || c instanceof CCombo || c instanceof org.eclipse.swt.widgets.List) {
   c.setBackground(surface(c));c.setForeground(text(c));
  }
  if(c instanceof org.eclipse.ui.forms.widgets.ExpandableComposite) {
   c.setBackground(surface(c));c.setForeground(text(c));
  }
  if(c instanceof org.eclipse.swt.custom.CLabel && inProperties(c)) {
   ((org.eclipse.swt.custom.CLabel)c).setBackground(surface(c));c.setForeground(text(c));
  }
  if(c instanceof Table) {
   Table t=(Table)c;
   if(t.getLinesVisible()) t.setLinesVisible(false);
   t.setHeaderBackground(surface(t)); t.setHeaderForeground(text(t));
  }
  if(c instanceof Text && (c.getStyle()&SWT.PASSWORD)!=0) {c.setBackground(bg);c.setForeground(fg);}
  if(c instanceof ToolBar || c instanceof org.eclipse.swt.widgets.CoolBar) {c.setBackground(surface(c));c.setForeground(text(c));}
  if(c instanceof Button && (c.getStyle()&SWT.PUSH)!=0 && name.contains("preview")) c.setBackground(bg);
  if(name.startsWith("org.eclipse.jface.text.source.") || name.contains("ViewerCanvas") || name.contains("FlyoutPaletteComposite$TitleCanvas")) {
   c.setBackground(bg);c.setForeground(fg);
  }
  if(c instanceof StyledText) {
   syntax((StyledText)c);
   for(Control sibling:c.getParent().getChildren())if(sibling instanceof Canvas && !(sibling instanceof StyledText)) {
        sibling.setBackground(bg);sibling.setForeground(fg);
    ruler((Canvas)sibling,(StyledText)c);
   }
  }
  if(c instanceof FigureCanvas) PaletteSurface.wrap((FigureCanvas)c);
  if(c.getClass().getSimpleName().equals("TabbedPropertyTitle")) {
   if(!java.util.Objects.equals(paletteId,c.getData("salutem.titleColors")))try {
    java.lang.reflect.Method method=c.getClass().getMethod("setColor",String.class,org.eclipse.swt.graphics.RGBA.class);
    for(String key:new String[]{IFormColors.H_GRADIENT_START,IFormColors.H_GRADIENT_END,IFormColors.H_BOTTOM_KEYLINE1,IFormColors.H_BOTTOM_KEYLINE2})
     method.invoke(c,key,new org.eclipse.swt.graphics.RGBA(bg.getRed(),bg.getGreen(),bg.getBlue(),255));
    c.setData("salutem.titleColors",paletteId);c.redraw();
   }catch(ReflectiveOperationException ignored){}
   for(Class<?> type=c.getClass();type!=null;type=type.getSuperclass()) for(java.lang.reflect.Field f:type.getDeclaredFields()) {
    if(FormColors.class.isAssignableFrom(f.getType()))try {
     f.setAccessible(true); FormColors colors=(FormColors)f.get(c);
     if(colors!=null && !bg.equals(colors.getColor(IFormColors.H_GRADIENT_START))) {
      colors.createColor(IFormColors.H_GRADIENT_START,bg.getRGB());
      colors.createColor(IFormColors.H_GRADIENT_END,bg.getRGB());
      colors.createColor(IFormColors.H_BOTTOM_KEYLINE1,border.getRGB());
      colors.createColor(IFormColors.H_BOTTOM_KEYLINE2,border.getRGB());
     }
    }catch(ReflectiveOperationException ignored){}
   }
  }
 }
 private void configureDisabledLabels(Display display) {
  display.setData("org.eclipse.swt.internal.win32.Label.disabledForegroundColor",muted);
  display.setData("org.eclipse.swt.internal.win32.ProgressBar.useColors",Boolean.TRUE);
 }
 private void disabledLabel(Label label) {
  // Parent-disabled labels keep getEnabled()==true. Recompute their native
  // drawing style from effective enablement without changing command state.
  boolean enabled=label.isEnabled();
  Object previous=label.getData("salutem.effectiveEnabled");
  if(!Boolean.valueOf(enabled).equals(previous))try {
   java.lang.reflect.Method update=Label.class.getDeclaredMethod("updateStyleBits",boolean.class);
   update.setAccessible(true);update.invoke(label,enabled);
   label.setData("salutem.effectiveEnabled",enabled);label.redraw();
  }catch(ReflectiveOperationException ignored){ }
  if(!enabled) {
   if(label.getData("salutem.enabledForeground")==null)label.setData("salutem.enabledForeground",label.getForeground());
   Color disabled=text(label,muted);
   if(!disabled.equals(label.getForeground()))label.setForeground(disabled);
  }else if(label.getData("salutem.enabledForeground") instanceof Color) {
   Color original=(Color)label.getData("salutem.enabledForeground");
   label.setForeground(text(label,original.isDisposed() || dark(original)?fg:original));label.setData("salutem.enabledForeground",null);
  }
 }
 private boolean inProgressPanel(Control control) {
  for(Control p=control;p!=null;p=p.getParent())for(Class<?> type=p.getClass();type!=null;type=type.getSuperclass()) {
   String name=type.getName();
   if(name.equals("org.eclipse.jface.wizard.ProgressMonitorPart") || name.equals("org.eclipse.jface.dialogs.ProgressIndicator"))return true;
  }
  return false;
 }
 private boolean isPublishingDatasource(Control c) {
  for(Control parent=c;parent!=null;parent=parent.getParent())
   for(Class<?> type=parent.getClass();type!=null;type=type.getSuperclass())
    if(type.getName().equals("com.jaspersoft.studio.server.publish.wizard.page.DatasourceSelectionComposite"))return true;
  return false;
 }
 private boolean containsDatasourcePanel(Composite parent,int depth) {
  if(depth>5)return false;
  for(Control child:parent.getChildren()) {
   if(isPublishingDatasource(child))return true;
   if(child instanceof Composite && containsDatasourcePanel((Composite)child,depth+1))return true;
  }
  return false;
 }
 private void publishColors(Control control) {
  Color surface=surface(control),text=text(control);
  // Inherited colours can compare equal before a native control has received
  // its own brush; explicitly set them so native buttons do not stay white.
  control.setBackground(surface);control.setForeground(text);
  if((control instanceof Button || control instanceof TabFolder) && control.getData("salutem.surfaceTheme")==null){
   control.setData("salutem.surfaceTheme",Boolean.TRUE);
   org.eclipse.swt.internal.win32.OS.AllowDarkModeForWindow(control.handle,true);
   org.eclipse.swt.internal.win32.OS.SetWindowTheme(control.handle,(control instanceof Button?"DarkMode_Explorer\0":"\0").toCharArray(),null);
  }
  if(control instanceof Composite) {
   Composite composite=(Composite)control;
   if(composite.getBackgroundMode()!=SWT.INHERIT_FORCE)composite.setBackgroundMode(SWT.INHERIT_FORCE);
   for(Control child:composite.getChildren())publishColors(child);
  }
 }
 private void gallery(Control control) {
  try {
   control.setBackground(bg);control.setForeground(fg);
   control.getClass().getMethod("setUseControlColors",boolean.class).invoke(control,true);
   Object renderer=control.getClass().getMethod("getItemRenderer").invoke(control);
   galleryColor(renderer,"setBackgroundColor",bg);
   galleryColor(renderer,"setForegroundColor",fg);
   galleryColor(renderer,"setSelectionBackgroundColor",accent);
   galleryColor(renderer,"setSelectionForegroundColor",fg);
   Object group=control.getClass().getMethod("getGroupRenderer").invoke(control);
   galleryColor(group,"setTitleBackground",bg);galleryColor(group,"setTitleForeground",fg);
   Object[] items=(Object[])control.getClass().getMethod("getItems").invoke(control);
   for(Object item:items)galleryItem(item);
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Gallery theme",error);}
 }
 private void galleryColor(Object target,String method,Color color)throws ReflectiveOperationException {
  if(target==null)return;
  try {target.getClass().getMethod(method,Color.class).invoke(target,color);}
  catch(NoSuchMethodException ignored){/* A custom renderer can omit optional color setters. */}
 }
 private void galleryItem(Object item)throws ReflectiveOperationException {
  galleryColor(item,"setBackground",bg);galleryColor(item,"setForeground",fg);
  for(Object child:(Object[])item.getClass().getMethod("getItems").invoke(item))galleryItem(child);
 }
 private void ruler(Canvas canvas,StyledText text) {
  if(canvas.getData("salutem.rulerNative")!=null)return;
  for(Listener listener:canvas.getListeners(SWT.Paint)) {
   Object target=listener instanceof TypedListener?((TypedListener)listener).getEventListener():listener;
   if(rulerOwner(target,0)){canvas.setData("salutem.rulerNative",Boolean.TRUE);canvas.redraw();}
  }
 }
 private boolean rulerOwner(Object owner,int depth) {
  if(owner==null || depth>4)return false;
  if(owner instanceof org.eclipse.jface.text.source.LineNumberRulerColumn) {
   org.eclipse.jface.text.source.LineNumberRulerColumn column=(org.eclipse.jface.text.source.LineNumberRulerColumn)owner;
   column.setBackground(bg);column.setForeground(fg);return true;
  }
  for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())for(java.lang.reflect.Field field:type.getDeclaredFields()) {
   if(field.getName().equals("this$0"))try {field.setAccessible(true);if(rulerOwner(field.get(owner),depth+1))return true;}catch(ReflectiveOperationException ignored){}
  }
  return false;
 }
 private boolean activeTheme() {
  return ThemePalette.find(InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").get("themeid",""))!=null;
 }
 private void uniformItem(TreeItem item,int columns,Color back,Color text) {
  for(int col=0;col<columns;col++) {
   if(!back.equals(item.getBackground(col)))item.setBackground(col,back);
   if(!text.equals(item.getForeground(col)))item.setForeground(col,text);
  }
  for(TreeItem child:item.getItems())uniformItem(child,columns,back,text);
 }
 private void menu(Menu m) {
  // SWT.Show is delivered before isVisible() becomes true. Skipping invisible
  // menus here misses precisely the opening event and leaves native light popups.
  if(m.isDisposed())return;
  nativeDark(m.getDisplay());
  org.eclipse.swt.internal.win32.OS.AllowDarkModeForWindow(m.getShell().handle,true);
  try {
   java.lang.reflect.Method method=Menu.class.getDeclaredMethod("setBackground",Color.class);
   // Custom popup brushes force classic Windows disabled-text embossing.
   // Retain native dark popup painting; only the menu bar uses palette colors.
   boolean bar=(m.getStyle()&SWT.BAR)!=0;
   method.setAccessible(true);method.invoke(m,bar?bg:null);
   java.lang.reflect.Method foreground=Menu.class.getDeclaredMethod("setForeground",Color.class); foreground.setAccessible(true);foreground.invoke(m,bar?fg:null);
  }catch(ReflectiveOperationException ignored){}
  if(m.getData("salutem.menuShow")==null) {
   m.setData("salutem.menuShow",Boolean.TRUE);
   m.addListener(SWT.Show,e->{if(activeTheme())menu(m);});
  }
  for(MenuItem i:m.getItems()) {
   if((i.getStyle()&SWT.SEPARATOR)!=0){i.dispose();continue;}
   if(i.getMenu()!=null)menu(i.getMenu());
  }
 }
 private Color selectionColor(){
  // More saturated than the resting panel colour, with readable foreground.
  return rgb(Display.getCurrent(),0x245A86);
 }
 private void nativeDark(Display display){
  // Eclipse can reset these process flags after our startup/theme application.
  // Restore immediately before native popups, rather than during every key press.
  display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme",Boolean.TRUE);
  display.setData("org.eclipse.swt.internal.win32.Combo.useDarkTheme",Boolean.TRUE);
  if(org.eclipse.swt.internal.win32.OS.IsDarkModeAvailable())org.eclipse.swt.internal.win32.OS.SetPreferredAppMode(2);
 }
 private void nativeCombo(Control control){
  if(control.isDisposed())return;nativeDark(control.getDisplay());
  // SetWindowTheme sends WM_THEMECHANGED synchronously. Repeating it while the
  // combo is processing a click, or while its popup is open, dismisses the list.
  // Configure each native list handle once; palette colours are supplied live
  // by NativeListSelection and do not require resetting the Windows theme.
  if(control instanceof Combo && ((Combo)control).getListVisible())return;
  Object configured=control.getData("salutem.nativeListTheme");
  if(configured instanceof Long && ((Long)configured).longValue()==control.handle)return;
  org.eclipse.swt.internal.win32.OS.AllowDarkModeForWindow(control.handle,true);
  org.eclipse.swt.internal.win32.OS.SetWindowTheme(control.handle,(control instanceof Combo?"CFD\0":"\0").toCharArray(),null);
  if(control instanceof Combo){
   org.eclipse.swt.internal.win32.COMBOBOXINFO info=new org.eclipse.swt.internal.win32.COMBOBOXINFO();info.cbSize=org.eclipse.swt.internal.win32.COMBOBOXINFO.sizeof;
   if(org.eclipse.swt.internal.win32.OS.GetComboBoxInfo(control.handle,info) && info.hwndList!=0){
    org.eclipse.swt.internal.win32.OS.AllowDarkModeForWindow(info.hwndList,true);
    org.eclipse.swt.internal.win32.OS.SetWindowTheme(info.hwndList,"\0".toCharArray(),null);
    selectionPainter(control,info.hwndList);
    control.setData("salutem.nativeListTheme",Long.valueOf(control.handle));
   }
  }else if(control instanceof org.eclipse.swt.widgets.List){selectionPainter(control,control.handle);control.setData("salutem.nativeListTheme",Long.valueOf(control.handle));}
 }
 private void selectionPainter(Control owner,long handle){
  Object existing=owner.getData("salutem.selectionPainter");
  if(existing instanceof NativeListSelection){if(((NativeListSelection)existing).matches(handle))return;((NativeListSelection)existing).close();}
  owner.setData("salutem.selectionPainter",new NativeListSelection(owner,handle,()->accent,()->fg));
 }
 private final java.util.Map<org.eclipse.swt.graphics.RGB,Color> syntaxColors=new java.util.HashMap<>();
 private Color readable(Color old) {
  if(old==null)return null;
  org.eclipse.swt.graphics.RGB r=old.getRGB();
  double light=.2126*r.red+.7152*r.green+.0722*r.blue;
  if(light>=145)return old;
  return syntaxColors.computeIfAbsent(r,k->{
   if(k.blue>k.red*1.2 && k.blue>k.green*1.2)return rgb((Display)old.getDevice(),0x87B4FF);
   if(k.green>k.red*1.2)return rgb((Display)old.getDevice(),0x7ED6B1);
   if(k.red>k.green*1.5 && k.red>k.blue*1.2)return rgb((Display)old.getDevice(),0xF4A0A0);
   if(k.red>60 && k.blue>60)return rgb((Display)old.getDevice(),0xC9A7FF);
   return fg;
  });
 }
 private void syntax(StyledText text) {
  if(!dark(text.getBackground()))return;
  StyleRange[] styles=text.getStyleRanges(); boolean changed=false;
  for(StyleRange s:styles) {Color color=readable(s.foreground);if(color!=s.foreground){s.foreground=color;changed=true;}}
  if(changed)text.setStyleRanges(styles);
 }
 private boolean inProperties(Control c) {
  for(Control p=c;p!=null;p=p.getParent()) {
   String name=p.getClass().getName();
   if(name.contains("TabbedPropertyComposite") || name.contains("TabbedPropertyTitle"))return true;
  }
  return false;
 }
 private void propertyColors(Control c) {
  Color surface=(c instanceof Label && (c.getStyle()&SWT.SEPARATOR)!=0)?border:surface(c);
  if(!c.getBackground().equals(surface))c.setBackground(surface);
  Color text=text(c);
  if(!c.getForeground().equals(text))c.setForeground(text);
  // The header paints its own gradient rather than the Control background.
  if(c.getClass().getSimpleName().equals("TabbedPropertyTitle")) {
   if(!java.util.Objects.equals(paletteId,c.getData("salutem.titleColors")))try {
    java.lang.reflect.Method method=c.getClass().getMethod("setColor",String.class,org.eclipse.swt.graphics.RGBA.class);
    for(String key:new String[]{IFormColors.H_GRADIENT_START,IFormColors.H_GRADIENT_END,IFormColors.H_BOTTOM_KEYLINE1,IFormColors.H_BOTTOM_KEYLINE2})
     method.invoke(c,key,new org.eclipse.swt.graphics.RGBA(bg.getRed(),bg.getGreen(),bg.getBlue(),255));
    c.setData("salutem.titleColors",paletteId);c.redraw();
   }catch(ReflectiveOperationException ignored){}
   for(String method:new String[]{"setBackgroundGradientStart","setBackgroundGradientEnd","setBackgroundBottomKeyline1","setBackgroundBottomKeyline2"}) {
    try { c.getClass().getMethod(method,Color.class).invoke(c,method.contains("Keyline")?border:bg); }
    catch(ReflectiveOperationException ignored) { /* CSS remains the fallback. */ }
   }
  }
 }
}
