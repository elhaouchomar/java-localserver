package handler;

import java.nio.file.Path;

/** Shared path-safety utilities for all request handlers. */
class PathUtils {
    private PathUtils() {}

    /** Returns true if {@code file} stays inside {@code root} (no path traversal). */
    static boolean isSafe(Path file, Path root) {
        return file.normalize().startsWith(root.normalize());
    }
}
