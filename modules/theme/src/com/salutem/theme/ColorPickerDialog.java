package com.salutem.theme;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/**
 * Styled colour picker: saturation/brightness field, hue bar, hex and RGB fields,
 * current/new preview, basic and recent colours and a screen eyedropper.
 * Enter applies, Esc cancels. Returns null when cancelled.
 */
final class ColorPickerDialog {
 private static final int FIELD_W=250,FIELD_H=200,HUE_W=18,SWATCH=22,SWATCH_GAP=6,COLUMNS=10;
 private static final String RECENT="customColors.recent";
 private static final RGB[] BASIC=rgbs(
  "1E1F22","2B2D30","3C3F41","4E5257","6F737A","9DA3AB","C9CDD2","E6E9EF","F5F6F8","FFFFFF",
  "0D1117","162033","1F3A5F","2F5D9A","3D7BFD","56A8F5","2AA198","3FB950","A3BE4C","E5C07B",
  "F0883E","E06C75","D2386C","A371F7","7A5AF8","5C4B8A","4B2E39","6B3A2A","3B4A2F","264D45");

 private final Shell parent;private final RGB initial;
 private Shell shell;private RGB result;
 private float hue,sat,val;
 private Canvas field,hueBar,preview;
 private Text hex,red,green,blue;private Label status;
 private Image fieldImage,hueImage;private float fieldHue=-1;
 private boolean updating;
 private Point drag;
 private Color bg,surface,border,text,muted,accent,accentHover,input,danger;
 private final List<Color> owned=new ArrayList<>();

 ColorPickerDialog(Shell parent,RGB initial){this.parent=parent;this.initial=initial==null?new RGB(60,63,65):initial;}

 RGB open(){
  Display display=parent.getDisplay();
  palette(display);
  build(display);
  setRgb(initial);
  shell.pack();
  Rectangle p=parent.getBounds(),s=shell.getBounds();
  shell.setLocation(p.x+(p.width-s.width)/2,p.y+Math.max(0,(p.height-s.height)/3));
  shell.open();hex.setFocus();hex.selectAll();
  while(!shell.isDisposed())if(!display.readAndDispatch())display.sleep();
  return result;
 }

 // ---------------------------------------------------------------- appearance
 private void palette(Display d){
  RGB base=parent.getBackground().getRGB();
  boolean dark=.2126*base.red+.7152*base.green+.0722*base.blue<128;
  bg=own(d,dark?"1E2126":"F7F8FA");surface=own(d,dark?"2A2E35":"FFFFFF");border=own(d,dark?"3A404A":"D0D5DD");
  text=own(d,dark?"E6E9EF":"1F2328");muted=own(d,dark?"9AA3AF":"667085");input=own(d,dark?"16191D":"FFFFFF");
  accent=own(d,dark?"3D7BFD":"2563EB");accentHover=own(d,dark?"5A8FFF":"1D4ED8");danger=own(d,dark?"F87171":"DC2626");
 }
 private Color own(Display d,String hex){Color c=new Color(d,CustomColors.parse(hex));owned.add(c);return c;}

 private void build(Display display){
  shell=new Shell(parent,SWT.NO_TRIM|SWT.APPLICATION_MODAL);
  shell.setText(CustomColors.text("Escolher cor","Choose colour"));
  shell.setBackground(border);
  GridLayout frame=new GridLayout(1,false);frame.marginWidth=frame.marginHeight=1;frame.verticalSpacing=0;
  shell.setLayout(frame);
  shell.addListener(SWT.Dispose,e->{for(Color c:owned)c.dispose();if(fieldImage!=null)fieldImage.dispose();if(hueImage!=null)hueImage.dispose();});
  shell.addListener(SWT.Traverse,e->{
   if(e.detail==SWT.TRAVERSE_ESCAPE){e.doit=false;shell.close();}
   else if(e.detail==SWT.TRAVERSE_RETURN){e.doit=false;apply();}
  });
  header();
  Composite body=panel(shell,3,16,16);body.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
  ((GridLayout)body.getLayout()).horizontalSpacing=12;
  field=new Canvas(body,SWT.DOUBLE_BUFFERED|SWT.NO_BACKGROUND);
  field.setLayoutData(size(FIELD_W,FIELD_H));
  field.addListener(SWT.Paint,this::paintField);
  dragOn(field,(x,y)->{Rectangle r=field.getClientArea();sat=clamp(x/(float)(r.width-1));val=1-clamp(y/(float)(r.height-1));changed(field);});
  hueBar=new Canvas(body,SWT.DOUBLE_BUFFERED|SWT.NO_BACKGROUND);
  hueBar.setLayoutData(size(HUE_W,FIELD_H));
  hueBar.addListener(SWT.Paint,this::paintHue);
  dragOn(hueBar,(x,y)->{hue=clamp(y/(float)(hueBar.getClientArea().height-1))*359.9f;changed(hueBar);});
  side(body);
  swatches(CustomColors.text("Cores básicas","Basic colours"),BASIC);
  RGB[] recent=recent();
  if(recent.length>0)swatches(CustomColors.text("Recentes","Recent"),recent);
  footer();
 }

 private void header(){
  Composite head=panel(shell,2,14,8);head.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
  head.setBackground(surface);
  Label title=label(head,CustomColors.text("Escolher cor","Choose colour"),text);
  title.setBackground(surface);title.setFont(bold(title));
  title.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  FlatButton close=new FlatButton(head,"×",false,this::cancel);close.setLayoutData(size(28,24));close.setBackground(surface);
  for(Control c:new Control[]{head,title}){
   c.setCursor(c.getDisplay().getSystemCursor(SWT.CURSOR_SIZEALL));
   c.addListener(SWT.MouseDown,e->{if(e.button==1){Point at=c.toDisplay(e.x,e.y),origin=shell.getLocation();drag=new Point(at.x-origin.x,at.y-origin.y);}});
   c.addListener(SWT.MouseUp,e->drag=null);
   c.addListener(SWT.MouseMove,e->{if(drag!=null){Point at=c.toDisplay(e.x,e.y);shell.setLocation(at.x-drag.x,at.y-drag.y);}});
  }
 }

 private void side(Composite body){
  Composite side=panel(body,1,0,0);side.setLayoutData(new GridData(SWT.FILL,SWT.FILL,false,true));
  ((GridLayout)side.getLayout()).verticalSpacing=6;
  preview=new Canvas(side,SWT.DOUBLE_BUFFERED);
  preview.setLayoutData(size(170,54));preview.setBackground(bg);
  preview.addListener(SWT.Paint,this::paintPreview);
  preview.addListener(SWT.MouseUp,e->{if(e.x<preview.getClientArea().width/2)setRgb(initial);});
  preview.setToolTipText(CustomColors.text("Clique em \"Atual\" para voltar à cor de antes","Click \"Current\" to go back"));
  caption(side,CustomColors.text("Código hexadecimal","Hex code"));
  hex=field(side,170);hex.setTextLimit(7);
  hex.addListener(SWT.Modify,e->{if(updating)return;RGB rgb=CustomColors.parse(expand(hex.getText()));if(rgb==null){invalid(true);return;}invalid(false);setRgb(rgb,hex);});
  hex.addListener(SWT.FocusOut,e->{if(!updating)showValues(null);});
  Composite rgb=panel(side,3,0,0);((GridLayout)rgb.getLayout()).horizontalSpacing=6;
  rgb.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
  caption(rgb,"R");caption(rgb,"G");caption(rgb,"B");
  red=channel(rgb);green=channel(rgb);blue=channel(rgb);
  status=label(side,"",danger);
  GridData statusData=new GridData(SWT.FILL,SWT.TOP,true,false);statusData.widthHint=170;statusData.heightHint=status.getFont().getFontData()[0].getHeight()*2;
  status.setLayoutData(statusData);
  FlatButton dropper=new FlatButton(side,CustomColors.text("Conta-gotas","Eyedropper"),false,this::eyedropper);
  dropper.icon=true;dropper.setLayoutData(size(170,32));
  dropper.setToolTipText(CustomColors.text("Pega a cor de qualquer ponto da tela (Esc cancela)","Pick a colour anywhere on screen (Esc cancels)"));
 }

 private void swatches(String title,RGB[] colors){
  Composite box=panel(shell,1,16,0);box.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
  ((GridLayout)box.getLayout()).marginBottom=10;
  caption(box,title);
  int rows=(colors.length+COLUMNS-1)/COLUMNS;
  Canvas grid=new Canvas(box,SWT.DOUBLE_BUFFERED);grid.setBackground(bg);
  grid.setLayoutData(size(COLUMNS*(SWATCH+SWATCH_GAP),rows*(SWATCH+SWATCH_GAP)));
  int[] hover={-1};
  grid.addListener(SWT.Paint,e->{
   e.gc.setAntialias(SWT.ON);
   for(int i=0;i<colors.length;i++){
    int x=(i%COLUMNS)*(SWATCH+SWATCH_GAP),y=(i/COLUMNS)*(SWATCH+SWATCH_GAP);
    Color c=new Color(grid.getDisplay(),colors[i]);
    e.gc.setBackground(c);e.gc.fillRoundRectangle(x,y,SWATCH,SWATCH,6,6);c.dispose();
    e.gc.setForeground(i==hover[0] || colors[i].equals(currentRgb())?accent:border);
    e.gc.setLineWidth(i==hover[0] || colors[i].equals(currentRgb())?2:1);
    e.gc.drawRoundRectangle(x,y,SWATCH-1,SWATCH-1,6,6);
   }
  });
  grid.addListener(SWT.MouseMove,e->{int i=swatchAt(e.x,e.y,colors.length);if(i!=hover[0]){hover[0]=i;grid.redraw();grid.setToolTipText(i<0?null:"#"+CustomColors.hex(colors[i]));}});
  grid.addListener(SWT.MouseExit,e->{hover[0]=-1;grid.redraw();});
  grid.addListener(SWT.MouseUp,e->{int i=swatchAt(e.x,e.y,colors.length);if(i>=0){setRgb(colors[i]);grid.redraw();}});
  grid.setCursor(grid.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
 }
 private static int swatchAt(int x,int y,int count){
  int col=x/(SWATCH+SWATCH_GAP),row=y/(SWATCH+SWATCH_GAP);
  if(x%(SWATCH+SWATCH_GAP)>=SWATCH || y%(SWATCH+SWATCH_GAP)>=SWATCH || col>=COLUMNS)return -1;
  int i=row*COLUMNS+col;return i<count?i:-1;
 }

 private void footer(){
  Composite foot=panel(shell,3,16,12);foot.setLayoutData(new GridData(SWT.FILL,SWT.BOTTOM,true,false));
  foot.setBackground(surface);
  Label hint=label(foot,CustomColors.text("Enter aplica · Esc cancela","Enter applies · Esc cancels"),muted);
  hint.setBackground(surface);hint.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  FlatButton cancel=new FlatButton(foot,CustomColors.text("Cancelar","Cancel"),false,this::cancel);cancel.setLayoutData(size(96,32));cancel.setBackground(surface);
  FlatButton ok=new FlatButton(foot,CustomColors.text("Aplicar","Apply"),true,this::apply);ok.setLayoutData(size(96,32));ok.setBackground(surface);
 }

 // ---------------------------------------------------------------- behaviour
 private void apply(){result=currentRgb();remember(result);shell.close();}
 private void cancel(){result=null;shell.close();}
 private void eyedropper(){RGB rgb=EyeDropper.pick(shell);if(rgb!=null && !shell.isDisposed())setRgb(rgb);}

 private RGB currentRgb(){return hsvToRgb(hue,sat,val);}
 private void setRgb(RGB rgb){setRgb(rgb,null);}
 private void setRgb(RGB rgb,Control source){
  float[] hsb=rgb.getHSB();
  if(hsb[1]>0 && hsb[2]>0)hue=hsb[0];
  sat=hsb[1];val=hsb[2];
  invalid(false);showValues(source);redrawAll();
 }
 private void changed(Control source){showValues(null);redrawAll();}
 private void redrawAll(){field.redraw();hueBar.redraw();preview.redraw();for(Control c:shell.getChildren())redrawSwatches(c);}
 private void redrawSwatches(Control c){if(c instanceof Canvas && c!=field && c!=hueBar && c!=preview)c.redraw();if(c instanceof Composite)for(Control child:((Composite)c).getChildren())redrawSwatches(child);}
 private void showValues(Control source){
  RGB rgb=currentRgb();updating=true;
  try{
   if(source!=hex)set(hex,"#"+CustomColors.hex(rgb));
   set(red,Integer.toString(rgb.red));set(green,Integer.toString(rgb.green));set(blue,Integer.toString(rgb.blue));
  }finally{updating=false;}
 }
 private static void set(Text t,String value){if(!t.getText().equals(value))t.setText(value);}
 private void invalid(boolean bad){
  status.setText(bad?CustomColors.text("Use 6 dígitos, ex.: #3C3F41","Use 6 digits, e.g. #3C3F41"):"");
  ((Composite)hex.getParent()).setBackground(bad?danger:border);
 }
 private void channels(){
  if(updating)return;
  try{
   int r=Integer.parseInt(red.getText().trim()),g=Integer.parseInt(green.getText().trim()),b=Integer.parseInt(blue.getText().trim());
   if(r<0||r>255||g<0||g>255||b<0||b>255)return;
   RGB rgb=new RGB(r,g,b);float[] hsb=rgb.getHSB();if(hsb[1]>0 && hsb[2]>0)hue=hsb[0];sat=hsb[1];val=hsb[2];
   updating=true;try{set(hex,"#"+CustomColors.hex(rgb));}finally{updating=false;}
   invalid(false);redrawAll();
  }catch(NumberFormatException ignored){ }
 }
 /** Accepts RRGGBB, #RRGGBB and the short form RGB. */
 static String expand(String value){
  String v=value==null?"":value.trim().replace("#","");
  if(v.matches("[0-9a-fA-F]{3}"))v=""+v.charAt(0)+v.charAt(0)+v.charAt(1)+v.charAt(1)+v.charAt(2)+v.charAt(2);
  return v;
 }

 private RGB[] recent(){
  List<RGB> values=new ArrayList<>();
  String stored;
  try{stored=CustomColors.preferences().get(RECENT,"");}catch(RuntimeException|LinkageError noPlatform){stored="";}
  for(String h:stored.split(",")){RGB rgb=CustomColors.parse(h);if(rgb!=null)values.add(rgb);}
  return values.toArray(new RGB[0]);
 }
 private void remember(RGB rgb){
  List<String> values=new ArrayList<>();values.add(CustomColors.hex(rgb));
  for(RGB old:recent())if(!old.equals(rgb) && values.size()<COLUMNS)values.add(CustomColors.hex(old));
  CustomColors.preferences().put(RECENT,String.join(",",values));
 }

 // ---------------------------------------------------------------- painting
 private void paintField(Event e){
  Rectangle r=field.getClientArea();
  if(fieldImage==null || fieldHue!=hue || fieldImage.getBounds().width!=r.width){
   if(fieldImage!=null)fieldImage.dispose();
   ImageData data=new ImageData(r.width,r.height,24,new PaletteData(0xFF0000,0xFF00,0xFF));
   for(int y=0;y<r.height;y++){float v=1-y/(float)(r.height-1);for(int x=0;x<r.width;x++){RGB c=hsvToRgb(hue,x/(float)(r.width-1),v);data.setPixel(x,y,(c.red<<16)|(c.green<<8)|c.blue);}}
   fieldImage=new Image(field.getDisplay(),data);fieldHue=hue;
  }
  e.gc.drawImage(fieldImage,0,0);
  int x=Math.round(sat*(r.width-1)),y=Math.round((1-val)*(r.height-1));
  e.gc.setAntialias(SWT.ON);
  e.gc.setLineWidth(3);e.gc.setForeground(field.getDisplay().getSystemColor(SWT.COLOR_BLACK));e.gc.drawOval(x-7,y-7,14,14);
  e.gc.setLineWidth(2);e.gc.setForeground(field.getDisplay().getSystemColor(SWT.COLOR_WHITE));e.gc.drawOval(x-7,y-7,14,14);
 }
 private void paintHue(Event e){
  Rectangle r=hueBar.getClientArea();
  if(hueImage==null || hueImage.getBounds().height!=r.height){
   if(hueImage!=null)hueImage.dispose();
   ImageData data=new ImageData(r.width,r.height,24,new PaletteData(0xFF0000,0xFF00,0xFF));
   for(int y=0;y<r.height;y++){RGB c=hsvToRgb(y/(float)(r.height-1)*359.9f,1,1);int p=(c.red<<16)|(c.green<<8)|c.blue;for(int x=0;x<r.width;x++)data.setPixel(x,y,p);}
   hueImage=new Image(hueBar.getDisplay(),data);
  }
  e.gc.setBackground(bg);e.gc.fillRectangle(r);
  e.gc.drawImage(hueImage,0,0);
  int y=Math.round(hue/359.9f*(r.height-1));
  e.gc.setAntialias(SWT.ON);
  e.gc.setLineWidth(3);e.gc.setForeground(hueBar.getDisplay().getSystemColor(SWT.COLOR_BLACK));e.gc.drawRoundRectangle(0,y-3,r.width-1,6,4,4);
  e.gc.setLineWidth(2);e.gc.setForeground(hueBar.getDisplay().getSystemColor(SWT.COLOR_WHITE));e.gc.drawRoundRectangle(0,y-3,r.width-1,6,4,4);
 }
 private void paintPreview(Event e){
  Rectangle r=preview.getClientArea();int half=r.width/2;
  GC gc=e.gc;gc.setAntialias(SWT.ON);
  gc.setBackground(bg);gc.fillRectangle(r);
  Color before=new Color(preview.getDisplay(),initial),after=new Color(preview.getDisplay(),currentRgb());
  try{
   gc.setBackground(before);gc.fillRoundRectangle(0,0,half+8,r.height,10,10);
   gc.setBackground(after);gc.fillRoundRectangle(half,0,r.width-half,r.height,10,10);gc.fillRectangle(half,0,8,r.height);
   gc.setForeground(border);gc.drawRoundRectangle(0,0,r.width-1,r.height-1,10,10);
   tag(gc,CustomColors.text("Atual","Current"),initial,8,r.height-20);
   tag(gc,CustomColors.text("Nova","New"),currentRgb(),half+8,r.height-20);
  }finally{before.dispose();after.dispose();}
 }
 private void tag(GC gc,String label,RGB under,int x,int y){
  gc.setForeground(gc.getDevice().getSystemColor(light(under)?SWT.COLOR_BLACK:SWT.COLOR_WHITE));
  gc.drawText(label,x,y,true);
 }
 static boolean light(RGB c){return .2126*c.red+.7152*c.green+.0722*c.blue>150;}

 // ---------------------------------------------------------------- helpers
 interface Drag{void at(int x,int y);}
 private static void dragOn(Canvas canvas,Drag drag){
  boolean[] down={false};
  canvas.addListener(SWT.MouseDown,e->{if(e.button==1){down[0]=true;drag.at(e.x,e.y);}});
  canvas.addListener(SWT.MouseMove,e->{if(down[0])drag.at(e.x,e.y);});
  canvas.addListener(SWT.MouseUp,e->down[0]=false);
  canvas.setCursor(canvas.getDisplay().getSystemCursor(SWT.CURSOR_CROSS));
 }
 private static float clamp(float v){return Math.max(0,Math.min(1,v));}
 static RGB hsvToRgb(float h,float s,float v){
  float c=v*s,x=c*(1-Math.abs((h/60f)%2-1)),m=v-c;float r,g,b;
  if(h<60){r=c;g=x;b=0;}else if(h<120){r=x;g=c;b=0;}else if(h<180){r=0;g=c;b=x;}
  else if(h<240){r=0;g=x;b=c;}else if(h<300){r=x;g=0;b=c;}else{r=c;g=0;b=x;}
  return new RGB(Math.round((r+m)*255),Math.round((g+m)*255),Math.round((b+m)*255));
 }
 private static RGB[] rgbs(String... hex){RGB[] values=new RGB[hex.length];for(int i=0;i<hex.length;i++)values[i]=CustomColors.parse(hex[i]);return values;}
 private static GridData size(int w,int h){GridData d=new GridData(SWT.LEFT,SWT.TOP,false,false);d.widthHint=w;d.heightHint=h;return d;}
 private Composite panel(Composite parent,int columns,int mw,int mh){
  Composite c=new Composite(parent,SWT.NONE);c.setBackground(bg);
  GridLayout l=new GridLayout(columns,false);l.marginWidth=mw;l.marginHeight=mh;c.setLayout(l);return c;
 }
 private Label label(Composite parent,String value,Color color){Label l=new Label(parent,SWT.NONE);l.setText(value);l.setForeground(color);l.setBackground(bg);return l;}
 private void caption(Composite parent,String value){label(parent,value,muted);}
 private Font bold(Control c){FontData[] data=c.getFont().getFontData();for(FontData f:data){f.setStyle(SWT.BOLD);f.setHeight(f.getHeight()+1);}Font font=new Font(c.getDisplay(),data);c.addListener(SWT.Dispose,e->font.dispose());return font;}
 private Text field(Composite parent,int width){
  Composite frame=new Composite(parent,SWT.NONE);frame.setBackground(border);
  GridLayout l=new GridLayout(1,false);l.marginWidth=l.marginHeight=1;frame.setLayout(l);
  GridData d=new GridData(SWT.FILL,SWT.TOP,true,false);d.widthHint=width;frame.setLayoutData(d);
  Composite inner=new Composite(frame,SWT.NONE);inner.setBackground(input);
  GridLayout il=new GridLayout(1,false);il.marginWidth=6;il.marginHeight=4;inner.setLayout(il);inner.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
  Text t=new Text(inner,SWT.SINGLE);t.setBackground(input);t.setForeground(text);
  t.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false));
  return t;
 }
 private Text channel(Composite parent){
  Text t=field(parent,40);t.setTextLimit(3);
  ((GridData)t.getParent().getParent().getLayoutData()).widthHint=40;
  t.addListener(SWT.Verify,e->{for(char ch:e.text.toCharArray())if(!Character.isDigit(ch)){e.doit=false;return;}});
  t.addListener(SWT.Modify,e->channels());
  t.addListener(SWT.FocusOut,e->{if(!updating)showValues(null);});
  return t;
 }

 /** Flat, rounded button drawn by hand so it matches the dialog in dark and light themes. */
 private final class FlatButton extends Canvas {
  private final String label;private final boolean primary;private final Runnable action;
  private boolean hover;boolean icon;
  FlatButton(Composite parent,String label,boolean primary,Runnable action){
   super(parent,SWT.DOUBLE_BUFFERED);this.label=label;this.primary=primary;this.action=action;
   setBackground(bg);setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
   addListener(SWT.Paint,this::paint);
   addListener(SWT.MouseEnter,e->{hover=true;redraw();});
   addListener(SWT.MouseExit,e->{hover=false;redraw();});
   addListener(SWT.MouseUp,e->{if(e.button==1 && getClientArea().contains(e.x,e.y))action.run();});
  }
  private void paint(Event e){
   Rectangle r=getClientArea();GC gc=e.gc;gc.setAntialias(SWT.ON);gc.setTextAntialias(SWT.ON);
   boolean plain=label.length()==1;
   if(primary){gc.setBackground(hover?accentHover:accent);gc.fillRoundRectangle(0,0,r.width,r.height,8,8);}
   else if(!plain || hover){gc.setBackground(hover?border:surface);gc.fillRoundRectangle(0,0,r.width,r.height,8,8);
    if(!plain){gc.setForeground(border);gc.drawRoundRectangle(0,0,r.width-1,r.height-1,8,8);}}
   gc.setForeground(primary?getDisplay().getSystemColor(SWT.COLOR_WHITE):text);
   Point size=gc.textExtent(label);
   int iconWidth=icon?22:0;
   int x=(r.width-size.x-iconWidth)/2+iconWidth,y=(r.height-size.y)/2;
   if(icon)dropperIcon(gc,x-iconWidth+2,(r.height-16)/2);
   gc.drawText(label,x,y,true);
  }
  /** Simple pipette: bulb, body and tip. */
  private void dropperIcon(GC gc,int x,int y){
   gc.setLineCap(SWT.CAP_ROUND);
   gc.setLineWidth(3);gc.drawLine(x+3,y+13,x+10,y+6);
   gc.setLineWidth(1);gc.setBackground(text);gc.fillOval(x+9,y+1,6,6);
   gc.drawLine(x+1,y+15,x+3,y+13);
  }
 }
}
