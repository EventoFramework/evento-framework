package org.evento.common.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The FileUtils class provides utility methods for handling files and directories.
 */
public class FileUtils {

    /**
     * Walks through the given directory and applies the provided function to the stream of paths.
     *
     * @param path  The path to the directory to be walked.
     * @param apply The function to be applied to the stream of paths.
     * @return The result of applying the provided function to the stream of paths.
     * @throws RuntimeException if an IO error occurs.
     */
    public static <T> T autoCloseWalk(Path path, Walker<T> apply) throws Exception {
        try(var stream = Files.walk(path)) {
            return apply.apply(stream);
        }
    }

    /**
     * The Walker interface represents a function that can be applied to a stream of paths.
     * It is used in conjunction with the FileUtils class to walk through a directory and apply the function to each path in the stream.
     *
     * @param <T> The type of the result produced by the apply function.
     */
    public interface Walker<T>{
        /**
         * Applies a function to a stream of paths.
         *
         * @param s The stream of paths to apply the function to.
         * @return The result of applying the function to the stream of paths.
         * @throws Exception if an error occurs during the application of the function.
         */
        T apply(Stream<Path> s) throws Exception;
    }
}
