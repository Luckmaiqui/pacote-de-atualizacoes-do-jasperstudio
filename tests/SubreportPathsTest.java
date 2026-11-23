import java.nio.file.*;import com.salutem.order.SubreportPaths;
public class SubreportPathsTest{public static void main(String[]args)throws Exception{
 Path root=Files.createTempDirectory(Path.of("build"),"subreport-").toAbsolutePath(),main=root.resolve("Principal.jrxml"),child=root.resolve("percentual_cirurgias.jrxml");Files.writeString(main,"<jasperReport/>");Files.writeString(child,"<jasperReport/>");
 Path resolved=SubreportPaths.resolve(main,"\"percentual_cirurgias.jasper\"");if(!resolved.equals(child) || !Files.isRegularFile(resolved))throw new AssertionError("Fonte relativo não resolvido");
 if(!SubreportPaths.resolve(main,"\"sub/Exemplo.jasper\"").equals(root.resolve("sub/Exemplo.jrxml")))throw new AssertionError("Subpasta perdida");
 if(SubreportPaths.sourceName("$P{SUBREPORT_DIR} + \"Exemplo.jasper\"")!=null || SubreportPaths.sourceName("\"repo:/Exemplo\"")!=null || SubreportPaths.sourceName("new java.io.File(\"x\")")!=null)throw new AssertionError("Executou ou alterou expressão dinâmica/remota");
 if(!"Exemplo.jrxml".equals(SubreportPaths.sourceName("\"Exemplo.jrxml\"")))throw new AssertionError("JRXML direto");
 System.out.println("PASS: percentual_cirurgias.jasper resolve ao JRXML correspondente, subpastas preservadas, expressões dinâmicas/remotas delegadas sem execução.");
}}
