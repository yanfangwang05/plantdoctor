package cn.caas.plantdoctor.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded images under the configured directory.
 *
 * <p>Files are renamed to a random identifier so that user-supplied names never
 * reach the filesystem, and only image extensions are accepted.
 */
public final class FileStorageUtil {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "bmp", "webp");

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private FileStorageUtil() {
    }

    /**
     * @return the generated file name, which is also the public path segment
     *         ({@code /uploads/<name>})
     */
    public static String store(MultipartFile file, String uploadDir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Uploaded file exceeds 20 MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported image type '" + extension + "'; allowed: " + ALLOWED_EXTENSIONS);
        }

        String generated = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Path directory = Path.of(uploadDir);
            Files.createDirectories(directory);
            Path target = directory.resolve(generated);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return generated;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to store the uploaded image", e);
        }
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
