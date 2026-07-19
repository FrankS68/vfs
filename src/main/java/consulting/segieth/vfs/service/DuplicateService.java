package consulting.segieth.vfs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileNodeRepository;
import consulting.segieth.vfs.domain.FileSystem;
import consulting.segieth.vfs.domain.FileSystemRepository;

@Service
public class DuplicateService {

    private final FileNodeRepository fileNodeRepository;
    private final FileSystemRepository fileSystemRepository;

    public DuplicateService(FileNodeRepository fileNodeRepository, FileSystemRepository fileSystemRepository) {
        this.fileNodeRepository = fileNodeRepository;
        this.fileSystemRepository = fileSystemRepository;
    }

    /** Gibt Duplikat-Gruppen zurück: Hash → Liste von FileNodes */
    public Map<String, List<FileNode>> findDuplicateGroups() {
        List<FileNode> duplicates = fileNodeRepository.findDuplicates();
        Map<String, List<FileNode>> groups = new LinkedHashMap<>();
        for (FileNode node : duplicates) {
            groups.computeIfAbsent(node.getContentHash(), k -> new ArrayList<>()).add(node);
        }
        return groups;
    }

    /** Löscht einen FileNode aus der DB und verschiebt die echte Datei in den Trash. */
    public void moveToTrash(FileNode node) throws IOException {
        Path source = Paths.get(node.getAddress());

        // Trash-Pfad aus dem zugehörigen FileSystem ermitteln
        String trashPath = findTrashPath(node);
        if (trashPath != null && Files.exists(source)) {
            Path trashDir = Paths.get(trashPath);
            Files.createDirectories(trashDir);
            Path target = trashDir.resolve(source.getFileName());
            // Bei Namenskollision im Trash umbenennen
            int i = 1;
            while (Files.exists(target)) {
                String name = source.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String base = dot >= 0 ? name.substring(0, dot) : name;
                String ext  = dot >= 0 ? name.substring(dot) : "";
                target = trashDir.resolve(base + "_" + i++ + ext);
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }

        fileNodeRepository.delete(node);
    }

    /** Löscht einen FileNode nur aus der DB, ohne die Datei anzufassen. */
    public void removeFromDb(FileNode node) {
        fileNodeRepository.delete(node);
    }

    public FileNode findById(Long id) {
        return fileNodeRepository.findById(id).orElse(null);
    }

    private String findTrashPath(FileNode node) {
        // Suche das FileSystem das am besten zum Pfad des Nodes passt
        List<FileSystem> systems = fileSystemRepository.findAll();
        FileSystem best = null;
        int bestLen = 0;
        for (FileSystem fs : systems) {
            if (node.getAddress().startsWith(fs.getAddress()) && fs.getAddress().length() > bestLen) {
                best = fs;
                bestLen = fs.getAddress().length();
            }
        }
        return best != null ? best.getTrashPath() : null;
    }

}
