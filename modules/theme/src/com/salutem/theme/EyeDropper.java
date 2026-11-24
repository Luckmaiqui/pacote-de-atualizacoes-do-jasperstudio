package com.salutem.theme;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * Screen eyedropper: hides the owner, freezes a picture of every monitor and lets the
 * user click any pixel. A magnifier with the hex code follows the mouse; Esc or the
 * right button cancels. Returns null when cancelled.
 * One overlay per monitor: a single window across monitors with different scales
 * (e.g. 100% and 150%) is drawn at one scale and leaves part of the other monitor uncovered.
 */
final class EyeDropper {
 private static final int CELLS=15,ZOOM=9,LOUPE=CELLS*ZOOM,GAP=22;
 private final Display display;
 private final List<Area> areas=new ArrayList<>();
 private RGB result;private boolean done;

 /**
  * One monitor: its bounds, frozen picture and overlay window. The Studio process is
  * system-DPI aware: on a monitor with a larger scale its bounds are virtualised (1920 px
  * at 150% are 1280 points) while the screen copy returns real pixels, so the picture is
  * taken at real size and points are multiplied by the monitor scale.
  */
 private final class Area {
  final Rectangle screen;final double scale;Image shot;ImageData pixels;Shell overlay;Point mouse=new Point(-1,-1);
  Area(Monitor monitor){screen=monitor.getBounds();scale=scale(monitor);}
  int px(int x){return (int)Math.round(x*scale);}
 }
 /**
  * Real pixels per point on this monitor: its display mode width (AWT reports the real
  * resolution; Windows hides the monitor DPI from system-aware processes) over its width in
  * points. 1 when the monitor cannot be matched.
  */
 private static double scale(Monitor monitor){
  Rectangle bounds=monitor.getBounds();
  try{
   for(java.awt.GraphicsDevice device:java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()){
    java.awt.Rectangle awt=device.getDefaultConfiguration().getBounds();
    if(awt.x!=bounds.x || awt.y!=bounds.y)continue;
    double s=device.getDisplayMode().getWidth()/(double)bounds.width;
    return s>=1 && s<=4?s:1;
   }
  }catch(RuntimeException|LinkageError ignored){ }
  return 1;
 }

 private EyeDropper(Display display){this.display=display;}

 static RGB pick(Shell owner){
  EyeDropper dropper=new EyeDropper(owner.getDisplay());
  owner.setVisible(false);
  try{dropper.settle(220);return dropper.run();}
  finally{if(!owner.isDisposed()){owner.setVisible(true);owner.forceActive();}}
 }

 /** Lets the windows behind the hidden dialog repaint before the screen is copied. */
 private void settle(long millis){
  long end=System.currentTimeMillis()+millis;
  while(System.currentTimeMillis()<end){if(!display.readAndDispatch())try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}}
 }

 private RGB run(){
  Cursor cross=new Cursor(display,SWT.CURSOR_CROSS);
  try{
   for(Monitor monitor:display.getMonitors())areas.add(new Area(monitor));
   for(Area area:areas){
    area.shot=new Image(display,area.px(area.screen.width),area.px(area.screen.height));
    GC copy=new GC(display);
    try{copy.copyArea(area.shot,area.screen.x,area.screen.y);}finally{copy.dispose();}
    area.pixels=area.shot.getImageData();
   }
   for(Area area:areas){
    Shell overlay=new Shell(display,SWT.NO_TRIM|SWT.ON_TOP);area.overlay=overlay;
    overlay.setBounds(area.screen);overlay.setCursor(cross);
    overlay.addListener(SWT.Paint,e->paint(area,e));
    overlay.addListener(SWT.MouseMove,e->move(area,new Point(e.x,e.y)));
    overlay.addListener(SWT.MouseExit,e->move(area,new Point(-1,-1)));
    overlay.addListener(SWT.MouseDown,e->{if(e.button==1)result=at(area,e.x,e.y);done=true;});
    overlay.addListener(SWT.KeyDown,e->{if(e.keyCode==SWT.ESC)done=true;});
    // Activating another overlay is not leaving the eyedropper; another application is.
    overlay.addListener(SWT.Deactivate,e->display.asyncExec(()->{if(!done && !overlay(display.getActiveShell()))done=true;}));
    overlay.open();
   }
   Point start=display.getCursorLocation();
   for(Area area:areas){
    if(area.screen.contains(start)){area.overlay.forceActive();move(area,new Point(start.x-area.screen.x,start.y-area.screen.y));}
   }
   while(!done && !display.isDisposed())if(!display.readAndDispatch())display.sleep();
  }finally{
   for(Area area:areas){
    if(area.overlay!=null && !area.overlay.isDisposed())area.overlay.dispose();
    if(area.shot!=null)area.shot.dispose();
   }
   cross.dispose();
  }
  return result;
 }

 private boolean overlay(Shell shell){
  for(Area area:areas)if(area.overlay==shell)return true;
  return false;
 }

 /** Colour under an overlay point (points are converted to picture pixels). */
 private RGB at(Area area,int x,int y){
  ImageData pixels=area.pixels;
  int px=Math.max(0,Math.min(pixels.width-1,area.px(x))),py=Math.max(0,Math.min(pixels.height-1,area.px(y)));
  return pixels.palette.getRGB(pixels.getPixel(px,py));
 }

 private Rectangle loupe(Area area,Point p){
  int w=LOUPE+2,h=LOUPE+30;
  int x=p.x+GAP,y=p.y+GAP;
  if(x+w>area.screen.width)x=p.x-GAP-w;
  if(y+h>area.screen.height)y=p.y-GAP-h;
  return new Rectangle(Math.max(0,x),Math.max(0,y),w,h);
 }
 private void move(Area area,Point p){
  if(area.overlay.isDisposed())return;
  Rectangle old=area.mouse.x<0?null:loupe(area,area.mouse);
  area.mouse=p;
  if(old!=null)area.overlay.redraw(old.x-2,old.y-2,old.width+4,old.height+4,false);
  if(p.x<0)return;
  Rectangle now=loupe(area,p);
  area.overlay.redraw(now.x-2,now.y-2,now.width+4,now.height+4,false);
 }

 private void paint(Area area,Event e){
  GC gc=e.gc;Image shot=area.shot;Point mouse=area.mouse;
  Rectangle size=shot.getBounds();
  int x0=Math.min(size.width-1,area.px(e.x)),y0=Math.min(size.height-1,area.px(e.y));
  int w=Math.max(1,Math.min(size.width-x0,area.px(e.width))),h=Math.max(1,Math.min(size.height-y0,area.px(e.height)));
  gc.drawImage(shot,x0,y0,w,h,e.x,e.y,e.width,e.height);
  if(mouse.x<0)return;
  Rectangle box=loupe(area,mouse);
  int half=CELLS/2;
  gc.setInterpolation(SWT.NONE);
  gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
  gc.fillRectangle(box);
  // The magnifier shows real pixels around the point under the mouse.
  int mx=Math.min(size.width-1,area.px(mouse.x)),my=Math.min(size.height-1,area.px(mouse.y));
  int sx=Math.max(0,Math.min(size.width-CELLS,mx-half)),sy=Math.max(0,Math.min(size.height-CELLS,my-half));
  gc.drawImage(shot,sx,sy,CELLS,CELLS,box.x+1,box.y+1,LOUPE,LOUPE);
  int cx=box.x+1+(mx-sx)*ZOOM,cy=box.y+1+(my-sy)*ZOOM;
  gc.setLineWidth(2);gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
  gc.drawRectangle(cx-1,cy-1,ZOOM+1,ZOOM+1);
  gc.setLineWidth(1);gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
  gc.drawRectangle(cx-2,cy-2,ZOOM+3,ZOOM+3);
  gc.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
  gc.drawRectangle(box.x,box.y,box.width-1,LOUPE+1);
  RGB rgb=at(area,mouse.x,mouse.y);
  Color swatch=new Color(display,rgb);
  try{
   int ty=box.y+LOUPE+4;
   gc.setBackground(swatch);gc.fillRectangle(box.x+6,ty+4,18,18);
   gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));gc.drawRectangle(box.x+6,ty+4,18,18);
   gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
   gc.drawText("#"+CustomColors.hex(rgb),box.x+32,ty+5,true);
  }finally{swatch.dispose();}
 }
}
