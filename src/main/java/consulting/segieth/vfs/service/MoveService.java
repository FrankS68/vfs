package consulting.segieth.vfs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileNodeRepository;

@Service
public class MoveService {

    private final FileNodeRepository fileNodeRepository;

    public MoveService(FileNodeRepository fileNodeRepository) {
        this.fileNodeRepository = fileNodeRepository;
    }

    /**
     * Verschiebt eine Datei oder ein Verzeichnis in ein Zielverzeichnis.
     * Aktualisiert danach alle betroffenen DB-Einträge.
     */
    @Transactional
    public void move(FileNode source, FileNode targetDir) throws IOException {
        Path sourcePath = Paths.get(source.getAddress());
        Path targetPath = Paths.get(targetDir.getAddress()).resolve(sourcePath.getFileName());

        if (Files.exists(targetPath)) {
            throw new IOException("Ziel existiert bereits: " + targetPath);
        }

        // Echte Datei / Verzeichnis verschieben
        Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);

        // DB aktualisieren
        if (source.getType().equals("directory")) {
            moveDirectoryInDb(source, sourcePath, targetPath);
        } else {
            moveFileInDb(source, targetPath);
        }
    }

    private void moveFileInDb(FileNode source, Path newPath) {
        source.setAddress(newPath.normalize().toString());
        source.setName(newPath.getFileName().toString());
        fileNodeRepository.save(source);
    }

    private void moveDirectoryInDb(FileNode source, Path oldPath, Path newPath) {
        // Den Verzeichnis-Eintrag selbst aktualisieren
        String oldPrefix = oldPath.normalize().toString();
        String newPrefix = newPath.normalize().toString();
        source.setAddress(newPrefix);
        source.setName(newPath.getFileName().toString());
        fileNodeRepository.save(source);

        // Alle Kinder-Einträge (rekursiv) aktualisieren
        List<FileNode> all = fileNodeRepository.findAll();
        for (FileNode node : all) {
            if (node.getAddress().startsWith(oldPrefix + "\\") ||
                node.getAddress().startsWith(oldPrefix + "/")) {
                String updatedAddress = newPrefix + node.getAddress().substring(oldPrefix.length());
                node.setAddress(updatedAddress);
                fileNodeRepository.save(node);
            }
        }
    }
}
