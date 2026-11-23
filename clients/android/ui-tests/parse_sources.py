#!/usr/bin/env python3
"""Parse production Java grammar with the JDK compiler, without Android type checking."""
from pathlib import Path
import tempfile,subprocess
A=Path(__file__).resolve().parents[1]
code='''import java.io.*;import java.util.*;import javax.tools.*;import com.sun.source.util.JavacTask;
public class ParseSources{public static void main(String[] args)throws Exception{
JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("JDK required");
DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
try(StandardJavaFileManager manager=compiler.getStandardFileManager(errors,null,null)){
Iterable<? extends JavaFileObject> units=manager.getJavaFileObjectsFromStrings(Arrays.asList(args));
JavacTask task=(JavacTask)compiler.getTask(null,manager,errors,Arrays.asList("--release","17","-proc:none"),null,units);
int n=0;for(var unit:task.parse()){n++;System.out.println("PARSED "+new File(unit.getSourceFile().toUri()).getName());}
for(var d:errors.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException(d.toString());
System.out.println("PASSED "+n+" production Java syntax parses; NOT Android API/type checking, lint or a native build.");}}}'''
with tempfile.TemporaryDirectory(prefix='lensdrop-java-parse-') as td:
 p=Path(td);f=p/'ParseSources.java';f.write_text(code)
 subprocess.run(['javac','--release','17','-d',str(p),str(f)],check=True,timeout=30)
 subprocess.run(['java','-cp',str(p),'ParseSources',*map(str,sorted((A/'app/src/main/java').rglob('*.java')))],check=True,timeout=40)
