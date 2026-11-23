import java.lang.reflect.Proxy;import java.net.URI;import org.eclipse.core.resources.IFile;import org.eclipse.ui.*;import com.salutem.order.SubreportInputs;
public class SubreportInputsTest{
 public static void main(String[]args){
  URI location=URI.create("file:/C:/workspace/Principal.jrxml");
  IFile file=(IFile)Proxy.newProxyInstance(IFile.class.getClassLoader(),new Class[]{IFile.class},(p,m,a)->m.getName().equals("getLocationURI")?location:null);
  IFileEditorInput input=(IFileEditorInput)Proxy.newProxyInstance(IFileEditorInput.class.getClassLoader(),new Class[]{IFileEditorInput.class},(p,m,a)->m.getName().equals("getFile")?file:null);
  if(input.getAdapter(IFile.class)!=null)throw new AssertionError("Fixture deve reproduzir entrada sem adaptação");
  if(!location.equals(SubreportInputs.localUri(input)))throw new AssertionError("IFileEditorInput não resolvido pelo getFile nativo");
  IURIEditorInput remote=(IURIEditorInput)Proxy.newProxyInstance(IURIEditorInput.class.getClassLoader(),new Class[]{IURIEditorInput.class},(p,m,a)->m.getName().equals("getURI")?URI.create("https://example.invalid/report"):null);
  if(SubreportInputs.localUri(remote)!=null || SubreportInputs.localUri(null)!=null)throw new AssertionError("Entrada remota interceptada como arquivo local");
  System.out.println("PASS: entrada nativa IFileEditorInput sem adapter resolvida; entrada remota permanece no fluxo original.");
 }
}
