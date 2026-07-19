package consulting.segieth.vfs.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.BiConsumer;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileNodeRepository;

@Service
public class HashService {

    private final FileNodeRepository repository;

    public HashService(FileNodeRepository repository) {
        this.repository = repository;
    }

    /** Berechnet SHA-256 Hash einer Datei. */
    public String computeHash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("Hash-Berechnung fehlgeschlagen für: " + path, e);
        }
    }

    /** Berechnet Hashes für alle FileNodes die noch keinen haben. */
    @Async
    public void computeMissingHashes(BiConsumer<Long, Long> progressCallback) {
        long total = repository.count();
        long done = 0;
        int page = 0;
        Slice<FileNode> slice;
        do {
            slice = repository.findWithoutHash(PageRequest.of(page++, 50));
            for (FileNode node : slice.getContent()) {
                try {
                    Path path = Paths.get(node.getAddress());
                    if (Files.exists(path) && Files.isRegularFile(path)) {
                        String hash = computeHash(path);
                        node.setContentHash(hash);
                        repository.save(node);
                    }
                } catch (Exception e) {
                    System.err.println("Hash fehlgeschlagen für: " + node.getAddress() + " - " + e.getMessage());
                }
                done++;
                try {
                    progressCallback.accept(done, total);
                } catch (Exception e) {
                    // UI-Session weg, trotzdem weitermachen
                }
            }
        } while (slice.hasNext());
        System.out.println("Hash-Berechnung abgeschlossen.");
    }

}
