package consulting.segieth.vfs.service;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileNodeRepository;

@Component
public class StartupCleanupRunner implements ApplicationRunner {

    private final FileNodeRepository fileNodeRepository;

    public StartupCleanupRunner(FileNodeRepository fileNodeRepository) {
        this.fileNodeRepository = fileNodeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        removeDuplicateDbEntries();
        fillMissingFileMetadata();
    }

    /**
     * Entfernt DB-Einträge mit identischer address+system — behält jeweils den
     * Eintrag mit der niedrigsten ID.
     */
    private void removeDuplicateDbEntries() {
        List<FileNode> all = fileNodeRepository.findAll();

        // Gruppieren nach system+address
        java.util.Map<String, List<FileNode>> grouped = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        n -> n.getSystem() + "|" + n.getAddress()));

        int removed = 0;
        for (List<FileNode> group : grouped.values()) {
            if (group.size() > 1) {
                // Nach ID sortieren, ersten behalten, Rest löschen
                group.sort(java.util.Comparator.comparing(FileNode::getId));
                List<FileNode> toDelete = group.subList(1, group.size());
                fileNodeRepository.deleteAll(toDelete);
                removed += toDelete.size();
            }
        }

        if (removed > 0) {
            System.out.println("StartupCleanup: " + removed + " doppelte DB-Einträge entfernt.");
        } else {
            System.out.println("StartupCleanup: Keine Duplikate gefunden.");
        }
    }

    /**
     * Füllt fehlende Dateiattribute (Größe, Erstelldatum, Änderungsdatum)
     * für bereits vorhandene FileNodes nach.
     */
    private void fillMissingFileMetadata() {
        List<consulting.segieth.vfs.domain.FileNode> all = fileNodeRepository.findAll();
        int updated = 0;
        for (consulting.segieth.vfs.domain.FileNode node : all) {
            if (!node.getType().equals("file") && !node.getType().equals("image")) continue;
            if (node.getFileSize() != null) continue; // bereits vorhanden
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(node.getAddress());
                if (!java.nio.file.Files.exists(path) || !java.nio.file.Files.isRegularFile(path)) continue;
                java.nio.file.attribute.BasicFileAttributes attrs =
                    java.nio.file.Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
                node.setFileSize(attrs.size());
                node.setCreatedDate(attrs.creationTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                node.setModifiedDate(attrs.lastModifiedTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                fileNodeRepository.save(node);
                updated++;
            } catch (Exception e) {
                // Datei nicht erreichbar — überspringen
            }
        }
        if (updated > 0) {
            System.out.println("StartupCleanup: " + updated + " Datei-Metadaten nachgetragen.");
        } else {
            System.out.println("StartupCleanup: Alle Metadaten bereits vorhanden.");
        }
    }
}
