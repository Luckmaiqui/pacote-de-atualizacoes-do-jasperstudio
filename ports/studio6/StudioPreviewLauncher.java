import org.eclipse.core.runtime.adaptor.EclipseStarter;

/** Direct Eclipse bootstrap for an isolated preview. Avoids old launchers'
 * file-URI handling without changing the Studio application or any bundles. */
public final class StudioPreviewLauncher {
 public static void main(String[] args){
  int code=1;
  try{Object result=EclipseStarter.run(args,null);if(result instanceof Integer)code=((Integer)result).intValue();else throw new IllegalStateException("Unexpected application exit result: "+result);}
  catch(Throwable error){error.printStackTrace();}
  System.exit(code);
 }
}
