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
import org.springframework.transaction.annotation.Transactional;

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

    /** Anzahl Dateien unterhalb eines Pfades, die noch keinen Hash haben. */
    public long countMissingHashes(String addressPrefix) {
        return fileNodeRepository.countMissingHashByAddress(addressPrefix);
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

    private static class MergePlan {
        List<FileNode> sourceNodes;
        java.util.Set<String> targetHashes;
    }

    private MergePlan planMerge(String sourcePrefix, String targetPrefix) {
        List<FileNode> allNodes = fileNodeRepository.findAll();

        MergePlan plan = new MergePlan();
        // Alle Hashes die im Ziel bereits existieren
        plan.targetHashes = allNodes.stream()
                .filter(fn -> fn.getAddress().startsWith(targetPrefix))
                .filter(fn -> fn.getContentHash() != null)
                .map(FileNode::getContentHash)
                .collect(java.util.stream.Collectors.toSet());

        plan.sourceNodes = allNodes.stream()
                .filter(fn -> fn.getAddress().startsWith(sourcePrefix))
                .filter(fn -> !fn.getType().equals("directory") && !fn.getType().equals("system"))
                .collect(java.util.stream.Collectors.toList());

        return plan;
    }

    /** Liefert die Dateien aus sourcePrefix, die per Hash NICHT in targetPrefix vorkommen
     * (also die "nur hier"-Dateien aus previewMerge — dieselbe Menge die dort verschoben würde). */
    public List<FileNode> findUnique(String sourcePrefix, String targetPrefix) {
        MergePlan plan = planMerge(sourcePrefix, targetPrefix);
        List<FileNode> unique = new ArrayList<>();
        for (FileNode source : plan.sourceNodes) {
            boolean existsInTarget = source.getContentHash() != null
                    && plan.targetHashes.contains(source.getContentHash());
            if (!existsInTarget) unique.add(source);
        }
        return unique;
    }

    /** Simuliert mergeIntoFolder ohne Dateien oder DB anzufassen.
     * Abgleich erfolgt ausschließlich über den Content-Hash — Dateien ohne Hash gelten
     * als "nicht geprüft" und werden nie automatisch als Duplikat gelöscht, nur verschoben.
     * @return int[] { würde verschoben, würde getrashed, davon ohne Hash (ungeprüft, in "würde verschoben" enthalten) } */
    public int[] previewMerge(String sourcePrefix, String targetPrefix) {
        MergePlan plan = planMerge(sourcePrefix, targetPrefix);
        int wouldMove = 0, wouldTrash = 0, noHash = 0;
        for (FileNode source : plan.sourceNodes) {
            boolean existsInTarget = source.getContentHash() != null
                    && plan.targetHashes.contains(source.getContentHash());
            if (existsInTarget) {
                wouldTrash++;
            } else {
                wouldMove++;
                if (source.getContentHash() == null) noHash++;
            }
        }
        return new int[]{ wouldMove, wouldTrash, noHash };
    }

    /** Mergt zwei Verzeichnisse: Dateien die im Ziel fehlen werden verschoben, Duplikate getrashed.
     * Abgleich erfolgt ausschließlich über den Content-Hash (kein Namens-Fallback), damit nie
     * eine Datei nur wegen gleichen Namens fälschlich als Duplikat gelöscht wird.
     * @return int[] { verschoben, getrashed, fehlgeschlagen } */
    @Transactional
    public int[] mergeIntoFolder(String sourcePrefix, String targetPrefix) {
        MergePlan plan = planMerge(sourcePrefix, targetPrefix);
        List<FileNode> sourceNodes = plan.sourceNodes;
        java.util.Set<String> targetHashes = plan.targetHashes;

        int moved = 0, trashed = 0, failed = 0;

        for (FileNode source : sourceNodes) {
            try {
                boolean existsInTarget = source.getContentHash() != null
                        && targetHashes.contains(source.getContentHash());

                if (existsInTarget) {
                    // Duplikat — in Trash
                    moveToTrash(source);
                    trashed++;
                } else {
                    // Fehlt im Ziel — verschieben
                    java.nio.file.Path sourcePath = java.nio.file.Paths.get(source.getAddress());
                    java.nio.file.Path targetPath = java.nio.file.Paths.get(targetPrefix).resolve(sourcePath.getFileName());
                    // Namenskollision im Ziel vermeiden
                    int i = 1;
                    while (java.nio.file.Files.exists(targetPath)) {
                        String name = sourcePath.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        String base = dot >= 0 ? name.substring(0, dot) : name;
                        String ext  = dot >= 0 ? name.substring(dot) : "";
                        targetPath = java.nio.file.Paths.get(targetPrefix).resolve(base + "_" + i++ + ext);
                    }
                    if (java.nio.file.Files.exists(sourcePath)) {
                        java.nio.file.Files.move(sourcePath, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    }
                    source.setAddress(targetPath.normalize().toString());
                    fileNodeRepository.save(source);
                    // Hash dem Ziel-Set hinzufügen
                    if (source.getContentHash() != null) targetHashes.add(source.getContentHash());
                    moved++;
                }
            } catch (Exception e) {
                System.err.println("Merge fehlgeschlagen für: " + source.getAddress() + " - " + e.getMessage());
                failed++;
            }
        }
        return new int[]{ moved, trashed, failed };
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
