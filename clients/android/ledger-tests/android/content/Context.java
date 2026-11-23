package android.content; public class Context {public final String root;public java.io.File getFilesDir(){return new java.io.File(root);}public Context(String root){this.root=root;}}
