package com.salutem.theme;
import java.util.*;
import java.util.List;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.MUILabel;
import org.eclipse.e4.ui.model.application.ui.advanced.MArea;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.services.IStylingEngine;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.Section;

/**
 * User-chosen background colours for workbench blocks (editor area, views, stacks).
 * Works with any theme. Containers, labels and read-only content (trees, tables,
 * lists, read-only text, Draw2D views) take the chosen background; text is switched
 * to black or white only when the theme colour would be hard to read. Editable fields,
 * buttons and the report editor keep their own colours. Removing a colour re-skins the
 * block so the active CSS theme restores it.
 */
public final class CustomColors {
 static final String NODE="com.salutem.jasper.theme.deepblue";
 static final String KEY="customColors";
 static final String EDITOR_AREA="org.eclipse.ui.editorss";
 /** Stored id of the report designer surface (the area around the page in the visual editor). */
 static final String DESIGNER="salutem.reportDesigner";
 /** Widget data key set by the E4 renderers (AbstractPartRenderer.OWNING_ME). */
 static final String OWNER="modelElement";
 /** Widget data: id of the block whose chosen colour this control carries. */
 private static final String PAINTED="salutem.customColor";
 /** Widget data: the text colour was switched for contrast and goes back to the theme on removal. */
 private static final String FOREGROUND="salutem.customForeground";
 /** Widget data: gradient a label had before the chosen colour replaced it. */
 private static final String GRADIENT="salutem.customGradient";
 /** Widget data: the property page title has the painter for the chosen colour. */
 private static final String TITLE="salutem.customTitle";
 /** Widget data: title bar colours a section had before the chosen colour. */
 private static final String SECTION="salutem.customSection";
 private static boolean installed,queued,refreshQueued;
 /** Controls carrying a chosen colour (weak: disposed widgets drop out). */
 private static final Map<Control,Boolean> MARKED=new WeakHashMap<>();
 private static final Map<Integer,Color> POOL=new HashMap<>();
 /** Stored entries ("id|label|RRGGBB" lines). Replaceable by tests that run without the OSGi platform. */
 static java.util.function.Supplier<String> stored=()->preferences().get(KEY,"");
 private static String activeValue;
 private static Map<String,RGB> active=Collections.emptyMap();

 /** One stored block: model element id, readable label and RGB colour. */
 public static final class Entry {
  public final String id,label;public final RGB rgb;
  public Entry(String id,String label,RGB rgb){this.id=id;this.label=label;this.rgb=rgb;}
 }

 private CustomColors(){}

 static boolean portuguese(){return Locale.getDefault().getLanguage().equals("pt");}
 static String text(String pt,String en){return portuguese()?pt:en;}

 static IEclipsePreferences preferences(){return InstanceScope.INSTANCE.getNode(NODE);}

 /** Format: id|label|RRGGBB, one entry per line. */
 public static List<Entry> load(){return parseEntries(stored.get());}
 static List<Entry> parseEntries(String value){
  List<Entry> entries=new ArrayList<>();
  for(String line:value.split("\n")){
   String[] parts=line.split("\\|");
   if(parts.length!=3 || parts[0].isEmpty())continue;
   RGB rgb=parse(parts[2]);if(rgb!=null)entries.add(new Entry(parts[0],parts[1],rgb));
  }
  return entries;
 }
 public static void save(List<Entry> entries){
  StringBuilder value=new StringBuilder();
  for(Entry e:entries){if(value.length()>0)value.append('\n');value.append(e.id).append('|').append(clean(e.label)).append('|').append(hex(e.rgb));}
  IEclipsePreferences node=preferences();
  if(value.length()==0)node.remove(KEY);else node.put(KEY,value.toString());
  try{node.flush();}catch(org.osgi.service.prefs.BackingStoreException error){System.err.println("Salutem custom colours: "+error);}
 }
 public static void put(String id,String label,RGB rgb){
  List<Entry> entries=load();entries.removeIf(e->e.id.equals(id));entries.add(new Entry(id,label,rgb));save(entries);
 }
 static String clean(String label){return label==null?"":label.replace('|',' ').replace('\n',' ').trim();}
 public static String hex(RGB rgb){return String.format("%02X%02X%02X",rgb.red,rgb.green,rgb.blue);}
 public static RGB parse(String hex){
  String value=hex==null?"":hex.trim().replace("#","");
  if(!value.matches("[0-9a-fA-F]{6}"))return null;
  int v=Integer.parseInt(value,16);return new RGB((v>>16)&255,(v>>8)&255,v&255);
 }

 /** Opens the styled colour picker (hex code, eyedropper, recent colours). Returns null when cancelled. */
 static RGB chooseColor(Shell parent,RGB initial){
  return new ColorPickerDialog(parent,initial).open();
 }

 /** Called once from the theme start-up; independent of the active theme. */
 public static void install(Display display){
  if(installed || display.isDisposed())return;installed=true;
  // The CSS engine styles new or re-skinned widgets and stacks that change their
  // active/inactive class; repaint the chosen blocks right after it.
  for(int type:new int[]{SWT.Skin,SWT.Activate,SWT.FocusIn})display.addFilter(type,e->{if(hasEntries())schedule(display,80);});
  // Activating a part restyles its stack directly (IStylingEngine.setClassname), without a
  // Skin event: put the colour back on the painted controls as soon as that handling ends.
  for(int type:new int[]{SWT.Activate,SWT.FocusIn,SWT.MouseUp})display.addFilter(type,e->{
   if(!refreshQueued && !MARKED.isEmpty()){refreshQueued=true;display.asyncExec(()->{refreshQueued=false;refresh();});}
  });
  // The workbench registers the CSS engine's Skin listener before any IStartup runs, so this
  // one runs right after it: a control styled by CSS gets its chosen colour back before it is
  // painted, instead of flashing the theme colour until the scheduled pass.
  display.addListener(SWT.Skin,SKIN);
  hookStyling();
  preferences().addPreferenceChangeListener(e->{if(KEY.equals(e.getKey()))display.asyncExec(()->schedule(display,0));});
  InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").addPreferenceChangeListener(e->display.asyncExec(()->schedule(display,600)));
  display.disposeExec(()->{for(Color c:POOL.values())if(!c.isDisposed())c.dispose();POOL.clear();});
  schedule(display,0);
  heartbeat(display);
 }
 private static void heartbeat(Display display){
  display.timerExec(2000,()->{if(display.isDisposed())return;if(hasEntries())applyAll(display);heartbeat(display);});
 }
 private static boolean hasEntries(){return !stored.get().isEmpty();}

 /** Active choices by block id, re-read only when the stored value changes. */
 private static Map<String,RGB> active(){
  String value=stored.get();
  if(!value.equals(activeValue)){
   Map<String,RGB> map=new HashMap<>();for(Entry e:parseEntries(value))map.put(e.id,e.rgb);
   active=map;activeValue=value;
  }
  return active;
 }

 /**
  * Background chosen by the user for this control, or null when it has none.
  * Every routine that paints control backgrounds (Salutem palettes included) uses
  * this instead of its own colour, so no later repaint removes the choice. The
  * mark alone is not trusted: once a choice is removed this returns null at once.
  */
 public static Color background(Control control){
  Object id=control.getData(PAINTED);
  if(!(id instanceof String))return null;
  RGB rgb=active().get(id);
  return rgb==null?null:color(control.getDisplay(),rgb);
 }
 /** Colour stored for a block id, or null. */
 static RGB chosen(String id){return id==null?null:active().get(id);}
 /** The chosen background if any, otherwise the theme colour given. */
 public static Color background(Control control,Color theme){
  Color chosen=background(control);return chosen!=null?chosen:theme;
 }
 /** Skin listener: re-applies the chosen colour to the control the CSS engine has just styled. */
 static final Listener SKIN=e->{if(e.widget instanceof Control && !active().isEmpty())reapply((Control)e.widget);};

 /**
  * Puts the chosen colour back on one control, using the nearest painted ancestor block.
  * Stops at another part that was not painted, like paint() does, so report content is never entered.
  */
 static void reapply(Control control){
  if(control.isDisposed())return;
  // The report designer surface is its own target: never reached through the editor's blocks.
  if(designer(control)){
   RGB rgb=active().get(DESIGNER);
   if(rgb!=null)apply(control,DESIGNER,color(control.getDisplay(),rgb));
   return;
  }
  for(Control c=control;c!=null;c=c.getParent()){
   Object marked=c.getData(PAINTED),owner=c.getData(OWNER);
   // A block root created just now (e.g. a view opened for the first time) has no mark yet.
   Object id=marked!=null?marked:owner instanceof MUIElement && ((MUIElement)owner).getWidget()==c?((MUIElement)owner).getElementId():null;
   if(id instanceof String){
    RGB rgb=active().get(id);
    if(rgb!=null){paintOne(control,(String)id,color(control.getDisplay(),rgb));return;}
    if(marked!=null)return;
   }
   if(owner instanceof MPart)return;
  }
 }
 /**
  * One control of a block: paintable controls take the colour; other controls that show it
  * anyway (fields of panels that force their background on children, like the property
  * sections) only get readable text. Returns whether the control was handled.
  */
 private static boolean paintOne(Control control,String id,Color color){
  if(paintable(control)){apply(control,id,color);return true;}
  if(!color.equals(control.getBackground()))return false;
  Color current=control.getForeground(),text=readable(current,color);
  if(!text.equals(current)){control.setForeground(text);control.setData(FOREGROUND,Boolean.TRUE);}
  return true;
 }
 /**
  * Activating a part restyles its stack directly through the workbench styling service,
  * without a Skin event. Renderers look the service up in the context on each call, so a
  * delegating wrapper puts the chosen colours back right after CSS, before painting.
  */
 private static void hookStyling(){
  try{
   MApplication application=PlatformUI.getWorkbench().getService(MApplication.class);
   if(application==null)return;
   IEclipseContext context=application.getContext();
   Object current=context.get(IStylingEngine.SERVICE_NAME);
   if(current instanceof IStylingEngine && !(current instanceof Styling))context.set(IStylingEngine.SERVICE_NAME,new Styling((IStylingEngine)current));
  }catch(RuntimeException error){System.err.println("Salutem custom colours: "+error);}
 }
 /** Same behaviour as the workbench styling engine, plus the chosen colours afterwards. */
 static final class Styling implements IStylingEngine {
  private final IStylingEngine engine;
  Styling(IStylingEngine engine){this.engine=engine;}
  @Override public void setClassname(Object widget,String classname){engine.setClassname(widget,classname);after(widget);}
  @Override public void setId(Object widget,String id){engine.setId(widget,id);after(widget);}
  @Override public void style(Object widget){engine.style(widget);after(widget);}
  @Override public org.w3c.dom.css.CSSStyleDeclaration getStyle(Object widget){return engine.getStyle(widget);}
  @Override public void setClassnameAndId(Object widget,String classname,String id){engine.setClassnameAndId(widget,classname,id);after(widget);}
  private static void after(Object widget){if(widget instanceof Control && !active().isEmpty())reapplyTree((Control)widget);}
 }
 static void reapplyTree(Control control){
  if(control.isDisposed())return;
  reapply(control);
  if(control instanceof Composite)for(Control child:((Composite)control).getChildren())reapplyTree(child);
 }

 /** Cheap pass over the controls already painted: puts back colours the CSS engine replaced. */
 static void refresh(){
  for(Control c:new ArrayList<>(MARKED.keySet())){
   if(c.isDisposed()){MARKED.remove(c);continue;}
   Color chosen=background(c);
   if(chosen!=null)apply(c,(String)c.getData(PAINTED),chosen);
  }
 }
 static void schedule(Display display,int delay){
  if(queued || display.isDisposed())return;queued=true;
  display.timerExec(delay,()->{queued=false;if(!display.isDisposed())applyAll(display);});
 }

 private static Color color(Display display,RGB rgb){
  int key=(rgb.red<<16)|(rgb.green<<8)|rgb.blue;
  return POOL.computeIfAbsent(key,k->new Color(display,rgb));
 }

 static void applyAll(Display display){
  List<Object[]> roots=new ArrayList<>();
  for(Map.Entry<String,RGB> entry:active().entrySet())
   for(Control root:widgets(entry.getKey()))roots.add(new Object[]{root,entry.getKey(),entry.getValue()});
  // Outer blocks first, so the most specific block wins whatever the stored order.
  roots.sort(Comparator.comparingInt(r->depth((Control)r[0])));
  Set<Control> painted=Collections.newSetFromMap(new IdentityHashMap<>());
  for(Object[] r:roots)paint((Control)r[0],(String)r[1],color(display,(RGB)r[2]),(Control)r[0],painted);
  RGB surface=active().get(DESIGNER);
  if(surface!=null)for(Control canvas:designers())if(!canvas.isDisposed()){apply(canvas,DESIGNER,color(display,surface));painted.add(canvas);}
  // Blocks whose colour was removed go back to the theme.
  for(Shell shell:display.getShells())restore(shell,painted);
 }
 private static int depth(Control control){int depth=0;for(Control c=control;c!=null;c=c.getParent())depth++;return depth;}

 /**
  * Report designer surfaces: the Draw2D canvas of each visual report editor. The area around
  * the page is this canvas's own background (the page figure is transparent outside the page
  * and paints the page itself), so only the canvas background changes: no figure of the
  * document is touched, and the preview (another control) is not a designer.
  */
 static boolean designer(Control control){
  return control instanceof FigureCanvas && pageFigure(((FigureCanvas)control).getContents(),0);
 }
 private static boolean pageFigure(org.eclipse.draw2d.IFigure figure,int depth){
  if(figure==null || depth>5)return false;
  for(Class<?> type=figure.getClass();type!=null && type!=Object.class;type=type.getSuperclass())
   if(type.getSimpleName().endsWith("PageFigure"))return true;
  for(Object child:figure.getChildren())if(pageFigure((org.eclipse.draw2d.IFigure)child,depth+1))return true;
  return false;
 }
 /** Designer surfaces of the open report editors, in every editor area. */
 static List<Control> designers(){
  List<Control> found=new ArrayList<>();
  for(Control area:widgets(EDITOR_AREA))collectDesigners(area,found);
  return found;
 }
 private static void collectDesigners(Control control,List<Control> found){
  if(control.isDisposed())return;
  if(designer(control)){if(!found.contains(control))found.add(control);return;}
  if(control instanceof Composite)for(Control child:((Composite)control).getChildren())collectDesigners(child,found);
 }

 /** Widgets of every model element with this id, in every workbench window. */
 static List<Control> widgets(String id){
  List<Control> controls=new ArrayList<>();
  try{
   MApplication application=PlatformUI.getWorkbench().getService(MApplication.class);
   EModelService models=PlatformUI.getWorkbench().getService(EModelService.class);
   if(application==null || models==null)return controls;
   for(MWindow window:application.getChildren())
    for(MUIElement element:models.findElements(window,id,MUIElement.class,null,EModelService.ANYWHERE))
     if(element.getWidget() instanceof Control && !((Control)element.getWidget()).isDisposed())controls.add((Control)element.getWidget());
  }catch(IllegalStateException workbenchClosing){ }
  return controls;
 }

 static void paint(Control control,String id,Color color,Control root,Set<Control> painted){
  if(control.isDisposed())return;
  // Never enter another part's content (e.g. the report designer inside the editor area).
  if(control!=root && control.getData(OWNER) instanceof MPart && !(root.getData(OWNER) instanceof MPart))return;
  if(paintOne(control,id,color))painted.add(control);
  if(control instanceof Composite)for(Control child:((Composite)control).getChildren())paint(child,id,color,root,painted);
 }
 private static void apply(Control control,String id,Color color){
  if(DESIGNER.equals(id)){
   // Background only: the canvas foreground is inherited by figures of the document.
   if(!color.equals(control.getBackground()))control.setBackground(color);
   control.setData(PAINTED,id);MARKED.put(control,Boolean.TRUE);
   return;
  }
  // Before the plain background, which would drop the gradient without saving it.
  if(control instanceof CLabel)gradient((CLabel)control,color);
  if(!color.equals(control.getBackground()))control.setBackground(color);
  if(control instanceof CTabFolder){
   CTabFolder tabs=(CTabFolder)control;
   if(tabs.getItemCount()==0 && !color.equals(tabs.getSelectionBackground()))tabs.setSelectionBackground(color);
  }
  if(showsText(control)){
   // Keep text readable on the chosen colour: the theme colour stays unless it is hard to read.
   Color current=control.getForeground(),text=readable(current,color);
   if(!text.equals(current)){control.setForeground(text);control.setData(FOREGROUND,Boolean.TRUE);}
  }
  if(content(control)){
   // GEF palettes paint fixed colours of their own: recolour them at paint time.
   if(control instanceof FigureCanvas)PaletteSurface.wrap((FigureCanvas)control);
   if(control instanceof Tree){
    Tree tree=(Tree)control;Color header=tree.getHeaderForeground(),readable=readable(header,color);
    if(!color.equals(tree.getHeaderBackground()))tree.setHeaderBackground(color);
    if(!readable.equals(header))tree.setHeaderForeground(readable);
   }
   if(control instanceof Table){
    Table table=(Table)control;Color header=table.getHeaderForeground(),readable=readable(header,color);
    if(!color.equals(table.getHeaderBackground()))table.setHeaderBackground(color);
    if(!readable.equals(header))table.setHeaderForeground(readable);
   }
  }
  title(control);
  tabList(control,color);
  if(control instanceof Section)section((Section)control,color);
  control.setData(PAINTED,id);MARKED.put(control,Boolean.TRUE);
 }
 /**
  * Labels with a gradient (titles of property pages) ignore a plain background. The gradient
  * is kept to put it back when the colour is removed.
  */
 private static void gradient(CLabel label,Color color){
  try{
   java.lang.reflect.Field colors=CLabel.class.getDeclaredField("gradientColors"),percents=CLabel.class.getDeclaredField("gradientPercents"),vertical=CLabel.class.getDeclaredField("gradientVertical");
   colors.setAccessible(true);percents.setAccessible(true);vertical.setAccessible(true);
   if(color!=null){
    if(colors.get(label)==null)return;
    label.setData(GRADIENT,new Object[]{colors.get(label),percents.get(label),vertical.get(label)});
    label.setBackground(color);
   }else if(label.getData(GRADIENT) instanceof Object[]){
    Object[] saved=(Object[])label.getData(GRADIENT);label.setData(GRADIENT,null);
    Color[] stops=(Color[])saved[0];
    for(Color stop:stops)if(stop!=null && stop.isDisposed())return;
    label.setBackground(stops,(int[])saved[1],(Boolean)saved[2]);
   }
  }catch(ReflectiveOperationException | RuntimeException error){ /* Another SWT version: the label keeps its gradient. */ }
 }
 /** Property page titles paint their own gradient behind the label: cover it with the chosen colour. */
 private static void title(Control control){
  if(control.getData(TITLE)!=null || !control.getClass().getName().endsWith(".TabbedPropertyTitle"))return;
  control.setData(TITLE,Boolean.TRUE);
  // Runs after the title's own painter; does nothing once the colour is removed.
  control.addListener(SWT.Paint,e->{
   Color chosen=background(control);
   if(chosen!=null){e.gc.setBackground(chosen);e.gc.fillRectangle(((Composite)control).getClientArea());}
  });
  control.redraw();
 }
 /**
  * The tab list of tabbed property pages paints its own gradients from these colours
  * instead of the control background. The originals are kept to restore them.
  */
 private static void tabList(Control control,Color color){
  if(!control.getClass().getName().endsWith(".TabbedPropertyList"))return;
  try{
   for(String property:new String[]{"ListBackground","WidgetBackground"}){
    String key="salutem.original"+property;
    if(color!=null && control.getData(key)==null)control.setData(key,control.getClass().getMethod("get"+property+"Color").invoke(control));
    Object value=color!=null?color:control.getData(key);
    if(value instanceof Color && !((Color)value).isDisposed())control.getClass().getMethod("set"+property+"Color",Color.class).invoke(control,value);
    if(color==null)control.setData(key,null);
   }
   for(Control child:((Composite)control).getChildren())child.redraw();
   control.redraw();
  }catch(ReflectiveOperationException | RuntimeException error){ /* Another version of the tab list: it keeps the theme colours. */ }
 }
 /**
  * Sections paint their title bar with colours of their own (set by the theme CSS). They take
  * the chosen colour, with readable title text; the originals come back when it is removed.
  */
 private static void section(Section section,Color color){
  if(color!=null){
   if(section.getData(SECTION)==null)section.setData(SECTION,new Color[]{section.getTitleBarBackground(),section.getTitleBarGradientBackground(),section.getTitleBarForeground()});
   if(!color.equals(section.getTitleBarBackground()))section.setTitleBarBackground(color);
   if(!color.equals(section.getTitleBarGradientBackground()))section.setTitleBarGradientBackground(color);
   Color current=section.getTitleBarForeground(),text=readable(current,color);
   if(!text.equals(current))section.setTitleBarForeground(text);
  }else if(section.getData(SECTION) instanceof Color[]){
   Color[] saved=(Color[])section.getData(SECTION);section.setData(SECTION,null);
   for(Color c:saved)if(c!=null && c.isDisposed())return;
   section.setTitleBarBackground(saved[0]);section.setTitleBarGradientBackground(saved[1]);section.setTitleBarForeground(saved[2]);
  }
 }
 /** The theme text colour, or black/white when it would be hard to read on the chosen background (WCAG 4.5:1). */
 static Color readable(Color text,Color background){
  if(text!=null && contrast(text.getRGB(),background.getRGB())>=4.5)return text;
  Display display=background.getDevice() instanceof Display?(Display)background.getDevice():Display.getCurrent();
  boolean black=contrast(new RGB(0,0,0),background.getRGB())>=contrast(new RGB(255,255,255),background.getRGB());
  return display.getSystemColor(black?SWT.COLOR_BLACK:SWT.COLOR_WHITE);
 }
 static double contrast(RGB a,RGB b){
  double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
 }
 private static double luminance(RGB c){return .2126*channel(c.red)+.7152*channel(c.green)+.0722*channel(c.blue);}
 private static double channel(int value){double v=value/255.0;return v<=.03928?v/12.92:Math.pow((v+.055)/1.055,2.4);}
 /** Text colour for a control: readable on its chosen background, otherwise the theme colour given. */
 public static Color foreground(Control control,Color theme){
  Color chosen=showsText(control)?background(control):null;
  return chosen==null?theme:readable(theme,chosen);
 }
 /** Controls whose own foreground draws text on the chosen background. */
 static boolean showsText(Control c){
  return content(c) || c instanceof CLabel || c instanceof CTabFolder || c instanceof ToolBar || c instanceof ExpandableComposite
   || (c instanceof Label && (c.getStyle()&SWT.SEPARATOR)==0) || (c instanceof Button && (c.getStyle()&(SWT.CHECK|SWT.RADIO))!=0);
 }
 static void restore(Control control,Set<Control> painted){
  if(control.isDisposed())return;
  if(control.getData(PAINTED)!=null && !painted.contains(control)){
   clear(control,painted);control.reskin(SWT.ALL);return;
  }
  if(control instanceof Composite)for(Control child:((Composite)control).getChildren())restore(child,painted);
 }
 /** Forgets removed choices in the whole subtree; controls still painted in this pass keep theirs. */
 private static void clear(Control control,Set<Control> painted){
  if(control.isDisposed())return;
  if(!painted.contains(control) && control.getData(PAINTED)!=null){
   control.setData(PAINTED,null);MARKED.remove(control);
   // Items coloured from the chosen colour (Salutem palettes colour every tree item) go back with it.
   if(control instanceof Tree)for(TreeItem item:((Tree)control).getItems())forget(item,control.getBackground(),control.getForeground(),Math.max(1,((Tree)control).getColumnCount()));
   // Back to the default first: the re-skin only changes what the theme CSS has a rule for
   // (the editor area, for example, has none and would keep the removed colour).
   control.setBackground(null);
   if(control instanceof CTabFolder && ((CTabFolder)control).getItemCount()==0)((CTabFolder)control).setSelectionBackground((Color)null);
   if(control instanceof Tree){((Tree)control).setHeaderBackground(null);((Tree)control).setHeaderForeground(null);}
   if(control instanceof Table){((Table)control).setHeaderBackground(null);((Table)control).setHeaderForeground(null);}
   if(control instanceof CLabel)gradient((CLabel)control,null);
   tabList(control,null);
   if(control instanceof Section)section((Section)control,null);
   if(control instanceof FigureCanvas || control.getData(TITLE)!=null)control.redraw();
  }
  if(!painted.contains(control) && control.getData(FOREGROUND)!=null){control.setData(FOREGROUND,null);control.setForeground(null);}
  if(control instanceof Composite)for(Control child:((Composite)control).getChildren())clear(child,painted);
 }

 private static void forget(TreeItem item,Color back,Color text,int columns){
  for(int column=0;column<columns;column++){
   if(back.equals(item.getBackground(column)))item.setBackground(column,null);
   if(text.equals(item.getForeground(column)))item.setForeground(column,null);
  }
  for(TreeItem child:item.getItems())forget(child,back,text,columns);
 }

 /** Everything in a block that shows the chosen background: containers, plain labels and read-only content. */
 static boolean paintable(Control c){return structural(c) || content(c);}

 /**
  * Containers and plain labels: any composite (anonymous and view-specific ones included),
  * except native or self-drawn widgets built on Composite, which keep their look.
  */
 static boolean structural(Control c){
  if(c instanceof CTabFolder || c instanceof CLabel || (c instanceof Label && (c.getStyle()&SWT.SEPARATOR)==0))return true;
  if(c instanceof ToolBar || c instanceof CoolBar || c instanceof Group)return true;
  // Check boxes and radio buttons are labels with a box; sections of property pages (Eclipse Forms) are containers.
  if(c instanceof Button && (c.getStyle()&(SWT.CHECK|SWT.RADIO))!=0)return true;
  if(c instanceof ExpandableComposite)return true;
  if(!(c instanceof Composite) || c instanceof Canvas)return false;
  if(c instanceof Tree || c instanceof Table || c instanceof Combo || c instanceof Spinner || c instanceof DateTime
   || c instanceof TabFolder || c instanceof ExpandBar)return false;
  return !c.getClass().getName().startsWith("org.eclipse.swt.browser.");
 }

 /**
  * Content drawn by other engines that would otherwise keep the theme background:
  * native trees, tables and lists (Win32), read-only text and Draw2D canvases (GEF views
  * such as the palette). Editable fields keep the theme colours, and report editors stay
  * out because paint() never enters another part.
  */
 static boolean content(Control c){
  if(c instanceof Tree || c instanceof Table || c instanceof org.eclipse.swt.widgets.List)return true;
  if(c instanceof Text)return !((Text)c).getEditable() && !(c.getParent() instanceof CCombo);
  if(c instanceof StyledText)return !((StyledText)c).getEditable();
  return c instanceof FigureCanvas;
 }

 /** The block under a control: the editor area as a whole, otherwise the nearest view or stack. */
 static MUIElement blockOf(Control control){
  MUIElement found=null;
  for(Control c=control;c!=null;c=c.getParent()){
   Object owner=c.getData(OWNER);
   if(owner instanceof MUIElement && ((MUIElement)owner).getElementId()!=null && !((MUIElement)owner).getElementId().isEmpty()){found=(MUIElement)owner;break;}
  }
  if(found==null)return null;
  MUIElement area=editorArea(found);
  return area!=null?area:found;
 }
 private static MUIElement editorArea(MUIElement element){
  for(MUIElement e=element;e!=null;e=e.getParent()!=null?e.getParent():e.getCurSharedRef())
   if(e instanceof MArea)return e;
  return null;
 }
 static String designerLabel(){return text("Fundo do designer","Designer background");}
 static String label(MUIElement element){
  if(EDITOR_AREA.equals(element.getElementId()) || element instanceof MArea)return text("Área do editor","Editor area");
  if(element instanceof MUILabel){String label=((MUILabel)element).getLocalizedLabel();if(label!=null && !label.isBlank())return label;}
  return element.getElementId();
 }
}
