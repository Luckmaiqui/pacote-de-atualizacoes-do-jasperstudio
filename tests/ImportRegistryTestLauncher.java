import org.eclipse.core.runtime.adaptor.EclipseStarter;

/** Dedicated test JVM: return the application result without retaining OSGi jobs. */
public final class ImportRegistryTestLauncher {
 public static void main(String[] args) {
  System.setProperty("osgi.noShutdown","true");
  System.setProperty("osgi.compatibility.bootdelegation","true");
  int code=1;
  try{Object result=EclipseStarter.run(args,null);if(result instanceof Integer)code=(Integer)result;}
  catch(Throwable error){error.printStackTrace();}
  System.exit(code);
 }
}
