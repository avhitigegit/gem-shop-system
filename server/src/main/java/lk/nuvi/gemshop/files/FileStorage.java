package lk.nuvi.gemshop.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lk.nuvi.gemshop.common.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Where the file bytes are kept: a folder on the server ({@code app.files.dir}; a Docker volume, copied to S3 every
 * night – P6-04). Keys look like {@code 2026/11/<uuid>.jpg}. If photos grow a lot they can move to S3 by replacing
 * this class.
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(AppProperties app) {
        this.root = Path.of(app.files().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the files folder " + root, e);
        }
    }

    public void put(String key, byte[] bytes) {
        Path p = path(key);
        try {
            Files.createDirectories(p.getParent());
            Path tmp = Files.createTempFile(p.getParent(), ".upload-", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save file " + key, e);
        }
    }

    public InputStream open(String key) throws IOException {
        return Files.newInputStream(path(key));
    }

    public byte[] read(String key) {
        try {
            return Files.readAllBytes(path(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read file " + key, e);
        }
    }

    public boolean exists(String key) {
        return Files.isRegularFile(path(key));
    }

    public void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete file " + key, e);
        }
    }

    /** Keys never leave the folder ("../" is refused). */
    private Path path(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Bad file key");
        }
        return p;
    }
}
