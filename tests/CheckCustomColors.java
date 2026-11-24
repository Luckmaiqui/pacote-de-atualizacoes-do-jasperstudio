package com.salutem.theme;
import java.lang.reflect.*;
import java.util.*;
import org.eclipse.swt.*;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.e4.ui.model.application.ui.basic.impl.BasicFactoryImpl;
import org.eclipse.draw2d.FigureCanvas;

/**
 * Cor personalizada por bloco × pintura das paletas Salutem (Startup).
 * Teste de componentes SWT; não substitui a conferência no Studio completo.
 */
public class CheckCustomColors {
 static int failures;
 static void check(boolean ok,String message){System.out.println((ok?"PASS: ":"FAIL: ")+message);if(!ok)failures++;}

 public static void main(String[] args)throws Exception{
  Display display=new Display();Shell shell=new Shell(display);
  Color custom=new Color(display,20,150,90),ancestor=new Color(display,120,40,160),fg=new Color(display,230,237,243);
  Color palette=new Color(display,13,17,23),otherPalette=new Color(display,22,27,34);
  String[] stored={"view|Visão|14965A\nstack|Pilha|7828A0"};
  CustomColors.stored=()->stored[0];
  try{
   Composite stack=new Composite(shell,SWT.NONE);
   Composite part=new Composite(stack,SWT.NONE);part.setData(CustomColors.OWNER,BasicFactoryImpl.eINSTANCE.createPart());
   ToolBar toolbar=new ToolBar(part,SWT.NONE);CLabel label=new CLabel(part,SWT.NONE);label.setText("Bloco de teste");
   Composite panel=new Composite(part,SWT.NONE);CLabel panelLabel=new CLabel(panel,SWT.NONE);Scale scale=new Scale(panel,SWT.NONE);
   CTabFolder emptyTabs=new CTabFolder(part,SWT.NONE);
   Table table=new Table(part,SWT.NONE);Text text=new Text(part,SWT.NONE);text.setText("Conteudo preservado");
   RGB tableBefore=table.getBackground().getRGB(),textBefore=text.getBackground().getRGB();
   Control[] chosen={toolbar,label,panel,panelLabel,emptyTabs};

   // Limite MPart: a cor da pilha ancestral não entra na visão, nas duas ordens.
   for(boolean partFirst:new boolean[]{true,false}){
    Set<Control> painted=Collections.newSetFromMap(new IdentityHashMap<>());
    if(partFirst){CustomColors.paint(part,"view",custom,part,painted);CustomColors.paint(stack,"stack",ancestor,stack,painted);}
    else{CustomColors.paint(stack,"stack",ancestor,stack,painted);CustomColors.paint(part,"view",custom,part,painted);}
    check(all(chosen,custom)&&custom.equals(emptyTabs.getSelectionBackground()),"limite MPart protege a visão da cor do ancestral ("+(partFirst?"visão antes":"pilha antes")+")");
   }
   check(stack.getBackground().equals(ancestor),"pilha ancestral recebe a própria cor");
   check(custom.equals(CustomColors.background(toolbar))&&custom.equals(CustomColors.background(table))&&CustomColors.background(text)==null,"consulta central: contêineres e conteúdo só de leitura (Table) do bloco têm a cor escolhida; campo editável não");

   // Rotina Salutem com fundo 13,17,23 não retira a escolha, nas duas ordens e em várias passagens.
   Object startup=Class.forName("com.salutem.theme.Startup").getConstructor().newInstance();
   setPalette(startup,palette,fg);
   Method extras=method(startup,"extras"),visit=method(startup,"visit"),publish=method(startup,"publishColors"),property=method(startup,"propertyColors");
   boolean stable=true;
   for(int pass=0;pass<6;pass++){
    Set<Control> painted=Collections.newSetFromMap(new IdentityHashMap<>());
    if(pass%2==0){CustomColors.paint(part,"view",custom,part,painted);salutem(startup,extras,visit,publish,property,part,panel,toolbar,label,panelLabel,scale,emptyTabs);}
    else{salutem(startup,extras,visit,publish,property,part,panel,toolbar,label,panelLabel,scale,emptyTabs);CustomColors.paint(part,"view",custom,part,painted);}
    // Depois de cada rotina isolada, não só no fim da passagem.
    salutem(startup,extras,visit,publish,property,part,panel,toolbar,label,panelLabel,scale,emptyTabs);
    if(!all(chosen,custom)||!custom.equals(emptyTabs.getSelectionBackground())){stable=false;System.out.println("  passagem "+pass+": toolbar="+toolbar.getBackground().getRGB()+" clabel="+label.getBackground().getRGB());}
   }
   check(stable,"ToolBar, CLabel, painel e abas vazias mantêm RGB 20,150,90 após extras, visit, publishColors e propertyColors (6 passagens, ordens alternadas)");
   // Texto da paleta (230,237,243) sobre 20,150,90 tem contraste 3,2:1: vira preto (5,5:1), também nas rotinas Salutem.
   check(label.getForeground().getRGB().equals(new RGB(0,0,0)),"texto do CLabel legível sobre a cor escolhida (preto, contraste >= 4,5:1) depois das rotinas Salutem");
   check(CustomColors.readable(fg,ancestor)==fg&&CustomColors.readable(fg,custom).getRGB().equals(new RGB(0,0,0))&&CustomColors.readable(palette,palette).getRGB().equals(new RGB(255,255,255)),"contraste: cor do tema mantida quando legível (6,8:1), senão preto ou branco");
   check(table.getBackground().equals(custom)&&!tableBefore.equals(custom.getRGB()),"Table (conteúdo nativo só de leitura) recebe a cor do bloco");
   check(text.getBackground().getRGB().equals(textBefore)&&text.getText().equals("Conteudo preservado"),"Text editável mantém o fundo e o texto");

   // Motor CSS: o listener de Skin dele é registrado antes do nosso (o workbench sobe antes dos IStartup).
   Color cssGrey=new Color(display,72,72,76);
   Listener css=e->{if(e.widget instanceof Control)((Control)e.widget).setBackground(cssGrey);};
   display.addListener(SWT.Skin,css);display.addListener(SWT.Skin,CustomColors.SKIN);
   try{
    part.reskin(SWT.ALL);
    Composite created=new Composite(panel,SWT.NONE);
    Composite nested=new Composite(panel,SWT.NONE);nested.setData(CustomColors.OWNER,BasicFactoryImpl.eINSTANCE.createPart());
    Composite nestedChild=new Composite(nested,SWT.NONE);
    // Visão aberta agora (bloco com cor escolhida, ainda sem marca).
    stored[0]=stored[0]+"\nview2|Visão 2|C8B400";
    Composite opened=new Composite(stack,SWT.NONE);
    org.eclipse.e4.ui.model.application.ui.basic.MPart view2=BasicFactoryImpl.eINSTANCE.createPart();view2.setElementId("view2");view2.setWidget(opened);opened.setData(CustomColors.OWNER,view2);
    Composite openedChild=new Composite(opened,SWT.NONE);
    while(display.readAndDispatch()){}
    check(all(chosen,custom),"reskin pelo CSS: a cor escolhida volta logo depois do listener do CSS, antes da pintura");
    check(opened.getBackground().getRGB().equals(new RGB(200,180,0))&&openedChild.getBackground().getRGB().equals(new RGB(200,180,0)),"visão aberta pela primeira vez recebe a cor na criação, sem esperar a passagem agendada");
    opened.dispose();stored[0]="view|Visão|14965A\nstack|Pilha|7828A0";
    check(created.getBackground().equals(custom)&&custom.equals(CustomColors.background(created)),"controle criado depois dentro do bloco recebe a cor na estilização inicial");
    check(nested.getBackground().equals(cssGrey)&&nestedChild.getBackground().equals(cssGrey),"outra parte (MPart) criada dentro do bloco não é pintada");
    // Ativação de parte pelo serviço de estilo do workbench: o CSS pinta direto, sem evento Skin.
    org.eclipse.e4.ui.services.IStylingEngine fakeCss=new org.eclipse.e4.ui.services.IStylingEngine(){
     void grey(Object w){if(w instanceof Control){((Control)w).setBackground(cssGrey);if(w instanceof Composite)for(Control c:((Composite)w).getChildren())grey(c);}}
     public void setClassname(Object w,String c){grey(w);}
     public void setId(Object w,String id){grey(w);}
     public void style(Object w){grey(w);}
     public org.w3c.dom.css.CSSStyleDeclaration getStyle(Object w){return null;}
     public void setClassnameAndId(Object w,String c,String id){grey(w);}
    };
    org.eclipse.e4.ui.services.IStylingEngine styling=new CustomColors.Styling(fakeCss);
    styling.setClassname(stack,"MPartStack active");
    check(all(chosen,custom)&&stack.getBackground().equals(ancestor)&&table.getBackground().equals(custom)&&text.getBackground().equals(cssGrey),"ativação pelo serviço de estilo: cor escolhida volta na mesma chamada (Table inclusive), campo editável fica com o CSS");
    styling.setClassnameAndId(part,"MPart","x");
    check(all(chosen,custom),"setClassnameAndId: mesma garantia");
    // Passagem rápida (filtros de ativação/foco) como reserva.
    toolbar.setBackground(cssGrey);label.setBackground(cssGrey);panel.setBackground(cssGrey);
    CustomColors.refresh();
    check(all(chosen,custom),"ativação (estilo aplicado sem Skin): passagem rápida devolve a cor escolhida");
    created.dispose();nested.dispose();
   }finally{display.removeListener(SWT.Skin,css);display.removeListener(SWT.Skin,CustomColors.SKIN);cssGrey.dispose();}

   // Contêiner sem regra de fundo no CSS (como a área do editor): só a restauração o devolve.
   Composite plain=new Composite(part,SWT.NONE);
   CustomColors.paint(part,"view",custom,part,Collections.newSetFromMap(new IdentityHashMap<>()));
   check(plain.getBackground().equals(custom),"contêiner sem regra de CSS recebe a cor");

   // Conteúdo desenhado por outros mecanismos: árvore nativa (Win32), texto só de leitura, painel que força
   // o fundo nos filhos (seções de Propriedades), título com degradê e paleta Draw2D (GEF).
   Color themeText=new Color(display,170,170,170);
   Tree tree=new Tree(part,SWT.NONE);new TreeColumn(tree,SWT.NONE);tree.setHeaderVisible(true);new TreeItem(tree,SWT.NONE).setText("Data Adapters");tree.setForeground(themeText);
   Text readOnly=new Text(part,SWT.READ_ONLY|SWT.MULTI);readOnly.setText("console");
   Composite forced=new Composite(part,SWT.NONE);forced.setBackgroundMode(SWT.INHERIT_FORCE);Text field=new Text(forced,SWT.BORDER);field.setText("0 px");field.setForeground(themeText);
   CLabel titled=new CLabel(part,SWT.NONE);Color top=new Color(display,250,250,250),bottom=new Color(display,220,220,220);titled.setBackground(new Color[]{top,bottom},new int[]{100},true);
   FigureCanvas canvas=new FigureCanvas(part);canvas.setContents(new paletteFigure());
   Color bar=new Color(display,60,60,60);
   org.eclipse.ui.forms.widgets.Section section=new org.eclipse.ui.forms.widgets.Section(part,org.eclipse.ui.forms.widgets.Section.TITLE_BAR);section.setText("Location");
   section.setTitleBarBackground(bar);section.setTitleBarGradientBackground(bar);
   Button check=new Button(part,SWT.CHECK);check.setText("Transparent");check.setBackground(bar);
   CustomColors.paint(part,"view",custom,part,Collections.newSetFromMap(new IdentityHashMap<>()));
   check(custom.equals(section.getTitleBarBackground())&&custom.equals(section.getTitleBarGradientBackground())&&check.getBackground().equals(custom),"seção de Propriedades (Eclipse Forms): barra de título com a cor escolhida; caixa de seleção sem o quadrado do tema");
   check(tree.getBackground().equals(custom)&&custom.equals(tree.getHeaderBackground())&&tree.getForeground().getRGB().equals(new RGB(0,0,0)),"árvore nativa: fundo, cabeçalho e texto legível (cinza 1,6:1 vira preto)");
   check(readOnly.getBackground().equals(custom)&&readOnly.getText().equals("console"),"texto só de leitura recebe a cor e mantém o conteúdo");
   check(field.getBackground().equals(custom)&&field.getForeground().getRGB().equals(new RGB(0,0,0))&&CustomColors.background(field)==null&&field.getText().equals("0 px"),"campo editável que herda o fundo forçado: só o texto fica legível, sem marcar o campo");
   check(gradient(titled)==null&&titled.getBackground().equals(custom),"CLabel com degradê (título de Propriedades) recebe a cor");
   check(canvas.getContents() instanceof PaletteSurface&&paletteFill(canvas,display).equals(custom.getRGB()),"paleta Draw2D: a figura pinta a cor escolhida no lugar do branco fixo");
   extras.invoke(startup,tree);
   check(tree.getItem(0).getBackground(0).equals(custom)&&custom.equals(tree.getHeaderBackground())&&tree.getItem(0).getForeground(0).getRGB().equals(new RGB(0,0,0)),"rotina Salutem da árvore usa a consulta: itens e cabeçalho com a cor escolhida, texto legível");
   // Editor de relatório (outra parte) com figuras Draw2D dentro da área pintada: fica de fora.
   Composite editor=new Composite(stack,SWT.NONE);editor.setData(CustomColors.OWNER,BasicFactoryImpl.eINSTANCE.createPart());
   FigureCanvas designer=new FigureCanvas(editor);designer.setContents(new paletteFigure());RGB designerBefore=designer.getBackground().getRGB();
   CustomColors.paint(stack,"stack",ancestor,stack,Collections.newSetFromMap(new IdentityHashMap<>()));
   check(!(designer.getContents() instanceof PaletteSurface)&&designer.getBackground().getRGB().equals(designerBefore)&&CustomColors.background(designer)==null,"limite MPart: figuras Draw2D do editor de relatório não são tocadas");
   // Fundo do designer (CODEX-20261119-07#4): alvo próprio, só o fundo do canvas em volta da página.
   FigureCanvas surface=new FigureCanvas(editor);org.eclipse.draw2d.Figure layers=new org.eclipse.draw2d.Figure();ReportPageFigure page=new ReportPageFigure();layers.add(page);surface.setContents(layers);
   Color pageText=new Color(display,40,40,40);page.setForegroundColor(pageText);
   RGB surfaceText=surface.getForeground().getRGB();
   check(CustomColors.designer(surface)&&!CustomColors.designer(designer)&&!CustomColors.designer(canvas),"designer reconhecido pela figura da página; paleta e outros canvas Draw2D não");
   CustomColors.reapply(surface);
   check(!surface.getBackground().getRGB().equals(new RGB(0x33,0x55,0xAA))&&CustomColors.background(surface)==null,"sem escolha do designer, o canvas fica com o tema");
   stored[0]=stored[0]+"\n"+CustomColors.DESIGNER+"|Fundo do designer|3355AA";
   CustomColors.reapply(surface);
   check(surface.getBackground().getRGB().equals(new RGB(0x33,0x55,0xAA))&&surface.getForeground().getRGB().equals(surfaceText)&&!(surface.getContents() instanceof PaletteSurface),"fundo do designer: só o fundo do canvas muda (texto do canvas e figuras sem envelope)");
   check(page.getBackgroundColor().getRGB().equals(new RGB(255,255,255))&&page.getForegroundColor().equals(pageText)&&layers.getChildren().size()==1,"página e elementos do relatório sem alteração");
   CustomColors.paint(stack,"stack",ancestor,stack,Collections.newSetFromMap(new IdentityHashMap<>()));
   check(surface.getBackground().getRGB().equals(new RGB(0x33,0x55,0xAA))&&CustomColors.DESIGNER.equals(surface.getData("salutem.customColor")),"cor da área do editor não substitui a do designer (limite MPart)");
   Color cssCanvas=new Color(display,47,47,47);surface.setBackground(cssCanvas);
   Listener cssDesigner=e->{if(e.widget==surface)surface.setBackground(cssCanvas);};
   display.addListener(SWT.Skin,cssDesigner);display.addListener(SWT.Skin,CustomColors.SKIN);
   try{surface.reskin(SWT.NONE);while(display.readAndDispatch()){}}finally{display.removeListener(SWT.Skin,cssDesigner);display.removeListener(SWT.Skin,CustomColors.SKIN);}
   check(surface.getBackground().getRGB().equals(new RGB(0x33,0x55,0xAA)),"reskin pelo CSS: o fundo do designer volta antes da pintura");
   CustomColors.refresh();
   check(tree.getBackground().equals(custom)&&readOnly.getBackground().equals(custom)&&custom.equals(CustomColors.background(tree)),"limite MPart: a cor da pilha ancestral não entra na árvore nem no texto da visão");

   // Remoção: a paleta volta a valer na mesma hora, mesmo antes da passagem de restauração.
   stored[0]="stack|Pilha|7828A0";
   extras.invoke(startup,toolbar);extras.invoke(startup,label);
   check(toolbar.getBackground().equals(palette)&&label.getBackground().equals(palette),"cor removida: rotina Salutem devolve o fundo da paleta sem esperar a restauração");
   Set<Control> painted=Collections.newSetFromMap(new IdentityHashMap<>());
   CustomColors.paint(stack,"stack",ancestor,stack,painted);CustomColors.restore(shell,painted);
   boolean cleared=true;for(Control c:chosen)cleared&=CustomColors.background(c)==null;
   check(cleared&&ancestor.equals(CustomColors.background(stack)),"restauração limpa o estado da visão removida e mantém o da pilha");
   check(!plain.getBackground().equals(custom)&&!plain.getBackground().equals(ancestor),"contêiner sem regra de CSS perde a cor removida (fundo padrão)");
   Color listText=display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);
   check(!tree.getBackground().equals(custom)&&!custom.equals(tree.getHeaderBackground())&&tree.getData("salutem.customForeground")==null&&tree.getForeground().equals(listText)&&!tree.getItem(0).getBackground(0).equals(custom)&&tree.getItem(0).getForeground(0).equals(listText),"cor removida: árvore volta ao fundo, cabeçalho e texto padrão, itens inclusive");
   check(field.getData("salutem.customForeground")==null&&field.getForeground().equals(listText),"cor removida: campo herdado volta ao texto padrão");
   check(gradient(titled)!=null,"cor removida: CLabel recupera o degradê");
   check(paletteFill(canvas,display).equals(new RGB(255,255,255)),"cor removida: paleta Draw2D volta às cores próprias");
   check(bar.equals(section.getTitleBarBackground())&&bar.equals(section.getTitleBarGradientBackground())&&!check.getBackground().equals(custom),"cor removida: seção recupera a barra de título e a caixa de seleção perde a cor");
   check(!surface.getBackground().getRGB().equals(new RGB(0x33,0x55,0xAA))&&surface.getData("salutem.customColor")==null&&page.getBackgroundColor().getRGB().equals(new RGB(255,255,255)),"cor do designer removida: o canvas volta ao tema e a página continua igual");
   section.dispose();check.dispose();editor.dispose();cssCanvas.dispose();pageText.dispose();
   for(Color c:new Color[]{themeText,top,bottom,bar})c.dispose();
   setPalette(startup,otherPalette,fg);
   visit.invoke(startup,part);
   check(toolbar.getBackground().equals(otherPalette)&&label.getBackground().equals(otherPalette)&&panelLabel.getBackground().equals(otherPalette),"após remover, troca de paleta aplica o fundo da nova paleta");
   stored[0]="";
   check(CustomColors.background(stack)==null,"restaurar tudo: nenhuma escolha ativa");

   // Seletor: hexadecimal e diálogo.
   check(CustomColors.parse(ColorPickerDialog.expand("#abc")).equals(new RGB(170,187,204))&&CustomColors.parse("GGGGGG")==null,"hexadecimal curto expandido e inválido recusado");
   display.timerExec(800,()->{for(Shell candidate:display.getShells())if(candidate!=shell&&!candidate.isDisposed())candidate.close();});
   check(new ColorPickerDialog(shell,new RGB(20,150,90)).open()==null,"diálogo abre e fecha sem aplicar cor");
  }finally{
   shell.dispose();for(Color c:new Color[]{custom,ancestor,fg,palette,otherPalette})c.dispose();display.dispose();
  }
  System.out.println("RESULT: "+failures+" falha(s)");
  if(failures>0)System.exit(2);
 }

 /** Draw2D figure named like the GEF palette figures (the class name contains "palette"): opaque white. */
 public static class paletteFigure extends org.eclipse.draw2d.Figure {
  paletteFigure(){setOpaque(true);setBackgroundColor(org.eclipse.draw2d.ColorConstants.white);}
 }
 /** Stand-in for the report page figure of the visual editor (same simple name as in Jaspersoft Studio). */
 public static class ReportPageFigure extends org.eclipse.draw2d.Figure {
  ReportPageFigure(){setBackgroundColor(org.eclipse.draw2d.ColorConstants.white);}
 }
 /** Colour the canvas contents paint at one point, through the same Draw2D painting as on screen. */
 static RGB paletteFill(FigureCanvas canvas,Display display){
  org.eclipse.draw2d.IFigure root=canvas.getContents();root.setBounds(new org.eclipse.draw2d.geometry.Rectangle(0,0,40,20));root.validate();
  Image image=new Image(display,40,20);GC gc=new GC(image);org.eclipse.draw2d.SWTGraphics graphics=new org.eclipse.draw2d.SWTGraphics(gc);
  try{root.paint(graphics);}finally{graphics.dispose();gc.dispose();}
  ImageData data=image.getImageData();RGB rgb=data.palette.getRGB(data.getPixel(10,10));image.dispose();return rgb;
 }
 static Object gradient(CLabel label)throws Exception{Field field=CLabel.class.getDeclaredField("gradientColors");field.setAccessible(true);return field.get(label);}
 static boolean all(Control[] controls,Color color){for(Control c:controls)if(!c.getBackground().equals(color))return false;return true;}
 static Method method(Object startup,String name)throws Exception{Method m=startup.getClass().getDeclaredMethod(name,Control.class);m.setAccessible(true);return m;}
 static void setPalette(Object startup,Color background,Color foreground)throws Exception{
  for(String name:new String[]{"fg","bg","border","accent","muted"}){Field field=startup.getClass().getDeclaredField(name);field.setAccessible(true);field.set(startup,name.equals("fg")?foreground:background);}
 }
 /** Every Salutem routine that paints backgrounds of these controls. */
 static void salutem(Object startup,Method extras,Method visit,Method publish,Method property,Composite part,Composite panel,Control... controls)throws Exception{
  for(Control c:controls)extras.invoke(startup,c);
  visit.invoke(startup,part);publish.invoke(startup,panel);property.invoke(startup,panel);property.invoke(startup,controls[1]);
 }
}
