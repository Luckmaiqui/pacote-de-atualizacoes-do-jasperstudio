package com.salutem.theme;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.ScaledGraphics;
import org.eclipse.draw2d.StackLayout;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Control;

/**
 * Wraps the figures of a GEF palette, which paint fixed colours of their own instead of the
 * canvas background. While a Salutem palette is active they get its colours; when the user
 * chose a colour for the palette's block, neutral fills take that colour in any theme and text
 * stays readable on it. Colours are swapped at paint time only, so removing a choice or
 * switching theme needs no restore.
 */
final class PaletteSurface extends Figure {
 /** Salutem colours {background, foreground, accent} while a Salutem palette is active, otherwise null. */
 static java.util.function.Supplier<Color[]> theme=()->null;
 private final Control canvas;

 private PaletteSurface(IFigure contents,Control canvas){this.canvas=canvas;setLayoutManager(new StackLayout());add(contents);setOpaque(false);}

 /** Wraps the canvas contents once, when they hold a palette. */
 static void wrap(FigureCanvas canvas){
  IFigure root=canvas.getContents();
  if(root==null || root instanceof PaletteSurface || !hasPalette(root,0))return;
  canvas.setContents(null);
  canvas.setContents(new PaletteSurface(root,canvas));
 }
 static boolean hasPalette(IFigure figure,int depth){
  if(figure.getClass().getName().contains("palette"))return true;
  if(depth>5)return false;
  for(Object child:figure.getChildren())if(hasPalette((IFigure)child,depth+1))return true;
  return false;
 }

 @Override public void paint(Graphics graphics){
  Color[] salutem=theme.get();
  Color chosen=canvas.isDisposed()?null:CustomColors.background(canvas);
  if(salutem==null && chosen==null){super.paint(graphics);return;}
  Color back=chosen!=null?chosen:salutem[0];
  ScaledGraphics colors=new ScaledGraphics(graphics){
   @Override public void setForegroundColor(Color color){
    Color text=salutem!=null?salutem[1]:color;
    super.setForegroundColor(chosen!=null?CustomColors.readable(text,chosen):text);
   }
   @Override public void setBackgroundColor(Color color){
    if(color==null){super.setBackgroundColor(back);return;}
    boolean neutral=Math.abs(color.getRed()-color.getGreen())<35 && Math.abs(color.getGreen()-color.getBlue())<35;
    super.setBackgroundColor(neutral?back:salutem!=null?salutem[2]:color);
   }
   @Override public void fillGradient(int x,int y,int width,int height,boolean vertical){
    pushState();super.setBackgroundColor(back);super.fillRectangle(x,y,width,height);popState();
   }
  };
  try{super.paint(colors);}finally{colors.dispose();}
 }
}
