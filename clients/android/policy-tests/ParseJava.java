import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
public class ParseJava{
 public static void main(String[] paths)throws Exception{
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diags=new DiagnosticCollector<>();
  try(StandardJavaFileManager files=compiler.getStandardFileManager(diags,null,null)){
   JavacTask task=(JavacTask)compiler.getTask(null,files,diags,List.of("-proc:none","--source","17"),null,files.getJavaFileObjects(paths));
   for(var tree:task.parse())System.out.println("PARSED "+tree.getSourceFile().getName());
   boolean bad=false;for(var d:diags.getDiagnostics()){System.out.println(d);if(d.getKind()==Diagnostic.Kind.ERROR)bad=true;}
   if(bad)System.exit(1);System.out.println("Java syntax parse passed only. Android types, resources, manifest and packaging NOT compiled.");
  }
 }
}
