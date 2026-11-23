package app.lensdrop.transfer;
/** JVM test fixture: real POSIX directory force, not Android Os API validation. */
final class DirectorySync{static void sync(java.io.File f)throws java.io.IOException{try(var c=java.nio.channels.FileChannel.open(f.toPath(),java.nio.file.StandardOpenOption.READ)){c.force(true);}}}
