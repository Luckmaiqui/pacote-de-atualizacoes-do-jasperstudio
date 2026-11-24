package com.salutem.theme;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.widgets.*;

/**
 * "Pick a block" mode: the block under the mouse gets a red outline; a click opens
 * the Windows colour dialog for it. Esc or the right button cancels. The click
 * that picks is consumed, so nothing underneath is activated.
 */
final class BlockPicker {
 private static final int FRAME=3;
 private static BlockPicker active;
 private final Display display;
 private final Shell outline;
 private final Listener move=this::moved,down=this::pressed,key=this::keyed;
 private final Cursor cross;
 /** Target under the mouse: stored id, readable label and the widget outlined. */
 private String blockId,blockLabel;private Control blockWidget;

 private BlockPicker(Display display){
  this.display=display;
  outline=new Shell(display,SWT.NO_TRIM|SWT.ON_TOP|SWT.TOOL|SWT.NO_FOCUS);
  outline.setBackground(display.getSystemColor(SWT.COLOR_RED));
  cross=new Cursor(display,SWT.CURSOR_CROSS);
 }

 static void start(Display display){
  if(active!=null || display==null || display.isDisposed())return;
  active=new BlockPicker(display);active.begin();
 }

 private void begin(){
  display.addFilter(SWT.MouseMove,move);display.addFilter(SWT.MouseDown,down);display.addFilter(SWT.KeyDown,key);
  for(Shell shell:display.getShells())if(shell!=outline && !shell.isDisposed())shell.setCursor(cross);
  Control under=display.getCursorControl();if(under!=null)highlight(under);
 }
 private void end(){
  display.removeFilter(SWT.MouseMove,move);display.removeFilter(SWT.MouseDown,down);display.removeFilter(SWT.KeyDown,key);
  for(Shell shell:display.getShells())if(!shell.isDisposed())shell.setCursor(null);
  if(!outline.isDisposed()){Region region=outline.getRegion();outline.dispose();if(region!=null)region.dispose();}
  cross.dispose();active=null;
 }

 private void moved(Event event){
  if(event.widget instanceof Control && ((Control)event.widget).getShell()!=outline)highlight((Control)event.widget);
 }
 private void keyed(Event event){
  if(event.keyCode==SWT.ESC){event.doit=false;end();}
 }
 private void pressed(Event event){
  event.doit=false;event.type=SWT.None;
  if(event.button!=1){end();return;}
  String id=blockId,label=blockLabel;Control widget=blockWidget;
  end();
  if(id==null || widget==null || widget.isDisposed())return;
  // Open the dialog after the mouse event has finished.
  display.asyncExec(()->choose(id,label,widget));
 }

 private void highlight(Control control){
  if(control.isDisposed())return;
  String id=null,label=null;Control widget=null;
  if(CustomColors.designer(control)){
   // The surface around the report page is a target of its own, inside the editor area.
   id=CustomColors.DESIGNER;label=CustomColors.designerLabel();widget=control;
  }else{
   MUIElement element=CustomColors.blockOf(control);
   if(element!=null && element.getWidget() instanceof Control){id=element.getElementId();label=CustomColors.label(element);widget=(Control)element.getWidget();}
  }
  if(widget==null || widget.isDisposed() || !widget.isVisible()){outline.setVisible(false);blockId=null;blockLabel=null;blockWidget=null;return;}
  if(widget==blockWidget && outline.isVisible())return;
  blockId=id;blockLabel=label;blockWidget=widget;
  Rectangle area=widget.getBounds();
  Rectangle bounds=display.map(widget.getParent(),null,area);
  Region frame=new Region(display);
  frame.add(0,0,bounds.width,bounds.height);
  frame.subtract(FRAME,FRAME,Math.max(0,bounds.width-2*FRAME),Math.max(0,bounds.height-2*FRAME));
  Region previous=outline.getRegion();
  outline.setRegion(frame);
  if(previous!=null)previous.dispose();
  outline.setBounds(bounds);
  if(!outline.isVisible())outline.setVisible(true);
 }

 private static void choose(String id,String label,Control widget){
  // Start from the colour already chosen for this block; the widget may show the theme colour.
  RGB current=CustomColors.chosen(id);
  RGB rgb=CustomColors.chooseColor(widget.getShell(),current!=null?current:widget.getBackground().getRGB());
  if(rgb==null)return;
  CustomColors.put(id,label,rgb);
 }
}
