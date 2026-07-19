package consulting.segieth.vfs.service;

import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileNodeRepository;
import consulting.segieth.vfs.domain.FileSystem;
import consulting.segieth.vfs.domain.FileSystemRepository;
import consulting.segieth.vfs.domain.ImageFile;

@Service
public class TreeService {

    private final FileNodeRepository fileNodeRepository;
    private final FileSystemRepository fileSystemRepository;
    private final FileSystemService fileSystemService;
    private final DuplicateService duplicateService;
    private final ImageService imageService;

    // Cache der Hashes die Duplikate haben — wird beim ersten Zugriff geladen
    private Set<String> duplicateHashes = null;

    public TreeService(FileNodeRepository fileNodeRepository,
                       FileSystemRepository fileSystemRepository,
                       FileSystemService fileSystemService,
                       DuplicateService duplicateService,
                       ImageService imageService) {
        this.fileNodeRepository = fileNodeRepository;
        this.fileSystemRepository = fileSystemRepository;
        this.fileSystemService = fileSystemService;
        this.duplicateService = duplicateService;
        this.imageService = imageService;
    }

    /** Cache invalidieren — nach moveToTrash aufrufen */
    public void invalidateDuplicateCache() {
        duplicateHashes = null;
    }

    private Set<String> getDuplicateHashes() {
        if (duplicateHashes == null) {
            duplicateHashes = duplicateService.findDuplicateGroups().keySet();
        }
        return duplicateHashes;
    }

    /** Root-Knoten: alle registrierten FileSystems */
    public List<VFSNode> getRoots() {
        return fileSystemRepository.findAllByType("system").stream()
                .map(this::toNode)
                .collect(Collectors.toList());
    }

    /** Direkte Kinder eines Knotens aus der DB, ggf. on-demand scannen */
    public List<VFSNode> getChildren(VFSNode parent) {
        FileNode node = parent.getFileNode();
        String address = normalizeAddress(node.getAddress());

        int count = fileNodeRepository.countDirectChildren(node.getSystem(), address);

        // Noch nicht gescannt — on-demand scannen (synchron, da wir das Ergebnis sofort brauchen)
        if (count == 0 && !node.getType().equals("file") && !(node instanceof ImageFile)) {
            try {
                fileSystemService.scanSync(node);
            } catch (NoSuchFileException e) {
                System.err.println("Verzeichnis nicht gefunden: " + node.getAddress());
            }
        }

        List<FileNode> children = fileNodeRepository.findDirectChildren(node.getSystem(), address);
        return children.stream()
                .sorted(java.util.Comparator
                        .comparing((FileNode n) -> n.getType().equals("directory") ? 0 : 1)
                        .thenComparing(n -> n.getName().toLowerCase()))
                .map(this::toNode)
                .collect(Collectors.toList());
    }

    /** Hat ein Knoten (potentiell) Kinder? */
    public boolean hasChildren(VFSNode node) {
        FileNode fn = node.getFileNode();
        if (fn.getType().equals("file")) return false;
        if (fn instanceof ImageFile) return false;
        // Verzeichnis: entweder schon Kinder in DB, oder noch nicht gescannt
        int count = fileNodeRepository.countDirectChildren(fn.getSystem(), normalizeAddress(fn.getAddress()));
        return count > 0 || fn.getType().equals("directory") || fn.getType().equals("system");
    }

    private VFSNode toNode(FileNode fn) {
        VFSNode node = fn instanceof ImageFile ? new ImageNode((ImageFile) fn) : new VFSNode(fn);
        // Direkt Duplikat
        if (fn.getContentHash() != null && getDuplicateHashes().contains(fn.getContentHash())) {
            node.setHasDuplicate(true);
        }
        // Oder Nachfahre hat Duplikat
        else if (!fn.getType().equals("file") && !(fn instanceof ImageFile)) {
            String addr = normalizeAddress(fn.getAddress());
            if (fileNodeRepository.hasDescendantWithDuplicate(fn.getSystem(), addr)) {
                node.setHasDuplicate(true);
            }
        }
        // Hat Bilder darunter (nur für Verzeichnisse)
        if (fn.getType().equals("directory") || fn.getType().equals("system")) {
            String addr = normalizeAddress(fn.getAddress());
            if (imageService.hasImagesUnder(addr)) {
                node.setHasImages(true);
            }
        }
        return node;
    }

    /** Stellt sicher dass der Pfad mit dem richtigen Separator endet */
    private String normalizeAddress(String address) {
        if (address == null) return "";
        if (address.endsWith("\\") || address.endsWith("/")) return address;
        // Windows oder Unix?
        return address.contains("\\") ? address + "\\" : address + "/";
    }
}
