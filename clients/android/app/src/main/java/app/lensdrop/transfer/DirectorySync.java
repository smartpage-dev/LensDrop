package app.lensdrop.transfer;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/** Persist the staging-directory rename before recording the staged file in SQLite.
 * Uses only public Android APIs. Errors are propagated to the existing retry path;
 * inability to sync must never be treated as a successful durable staging operation.
 */
final class DirectorySync {
    private DirectorySync() {}

    static void sync(File directory) throws IOException, ErrnoException {
        FileDescriptor fd = Os.open(directory.getAbsolutePath(), OsConstants.O_RDONLY, 0);
        try {
            // Validate the opened object, not just the path before opening it.
            if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) {
                throw new IOException("Staging sync target is not a directory");
            }
            Os.fsync(fd);
        } finally {
            Os.close(fd);
        }
    }
}
