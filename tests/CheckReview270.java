package com.salutem.order;
import java.lang.reflect.*;import java.util.*;import org.eclipse.swt.*;import org.eclipse.swt.widgets.*;import org.eclipse.jface.viewers.*;import org.eclipse.core.runtime.preferences.InstanceScope;import com.salutem.theme.ThemePalette;
public class CheckReview270 {
 public static class Provider {boolean skip;public void setSkipLazyLoad(boolean skip){this.skip=skip;}}
 static class PageBase {private final TreeViewer treeViewer;PageBase(TreeViewer viewer){treeViewer=viewer;}}
 static class Page extends PageBase {Page(TreeViewer viewer){super(viewer);}}
 public static void main(String[] args)throws Exception{
  Display d=new Display();Shell shell=new Shell(d);shell.setSize(600,300);try{
   Object theme=Class.forName("com.salutem.theme.Startup").getConstructor().newInstance();Method apply=theme.getClass().getDeclaredMethod("applyPalette",Display.class,ThemePalette.class);apply.setAccessible(true);apply.invoke(theme,d,ThemePalette.find("com.salutem.jasper.theme.deepblue"));
   InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").put("themeid","com.salutem.jasper.theme.deepblue");Field visible=theme.getClass().getDeclaredField("visibleOnly");visible.setAccessible(true);visible.setBoolean(theme,true);
   Menu menu=new Menu(shell,SWT.POP_UP);MenuItem item=new MenuItem(menu,SWT.PUSH);item.setText("Abrir Sub-Relatório");Method style=theme.getClass().getDeclaredMethod("menu",Menu.class);style.setAccessible(true);
   d.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme",Boolean.FALSE);style.invoke(theme,menu);
   Field dark=Display.class.getDeclaredField("useDarkModeExplorerTheme");dark.setAccessible(true);if(!dark.getBoolean(d))throw new AssertionError("Menu invisível ignorado antes da abertura");
   d.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme",Boolean.FALSE);menu.notifyListeners(SWT.Show,new Event());if(!dark.getBoolean(d))throw new AssertionError("Show antes de isVisible não recuperou modo escuro");
   Object root=new Object(),folder="Pasta",file="Arquivo.jrxml";Map<Object,java.util.List<Object>> nodes=new IdentityHashMap<>();nodes.put(root,java.util.List.of(folder));nodes.put(folder,java.util.List.of(file));
   TreeViewer viewer=new TreeViewer(shell,SWT.FULL_SELECTION|SWT.BORDER);viewer.getTree().setBounds(10,10,500,200);viewer.setContentProvider(new ITreeContentProvider(){public Object[] getElements(Object x){return getChildren(x);}public Object[] getChildren(Object x){return nodes.getOrDefault(x,java.util.List.of()).toArray();}public boolean hasChildren(Object x){return nodes.containsKey(x);}public Object getParent(Object x){for(var e:nodes.entrySet())if(e.getValue().contains(x))return e.getKey();return null;}});viewer.setLabelProvider(new LabelProvider());viewer.setInput(root);viewer.expandAll();viewer.setSelection(new StructuredSelection(file));
   Object[] expanded=viewer.getExpandedElements();ConnectionUi.attachPublishing(new Page(viewer));if(!(viewer.getComparator() instanceof RepositoryOrder) || viewer.getStructuredSelection().getFirstElement()!=file || !Arrays.equals(expanded,viewer.getExpandedElements()))throw new AssertionError("Publicação perdeu seleção ou pasta");
   Provider provider=new Provider();NativeServerSearch.refreshForReveal(viewer,provider,()->{if(!provider.skip)throw new AssertionError("Refresh sem proteção de carregamento");});if(provider.skip)throw new AssertionError("Não restaurou carregamento");try{NativeServerSearch.refreshForReveal(viewer,provider,()->{throw new IllegalStateException("Sintético");});throw new AssertionError("Falha suprimida");}catch(IllegalStateException expected){}if(provider.skip)throw new AssertionError("Falha deixou carregamento bloqueado");
   viewer.collapseAll();boolean[] closed={false};NativeServerSearch.revealAndClose(viewer,file,()->{if(viewer.getStructuredSelection().getFirstElement()!=file)throw new AssertionError("Fechou antes de localizar");closed[0]=true;});
   if(!closed[0] || !viewer.getExpandedState(folder) || viewer.getTree().getData("salutem.searchTarget")!=file)throw new AssertionError("Não revelou/expandiu/fechou");
   Method extras=theme.getClass().getDeclaredMethod("extras",Control.class);extras.setAccessible(true);extras.invoke(theme,viewer.getTree());if(viewer.getTree().getListeners(SWT.EraseItem).length!=1)throw new AssertionError("Destaque forte ausente");extras.invoke(theme,viewer.getTree());if(viewer.getTree().getListeners(SWT.EraseItem).length!=1)throw new AssertionError("Pintura duplicada");
   System.out.println("PASS: Show de menu antes de isVisible; comparador em página herdada preserva pasta/arquivo; localizar revela e expande antes de fechar; destaque instalado uma vez.");
  }finally{shell.dispose();d.dispose();}
 }
}
