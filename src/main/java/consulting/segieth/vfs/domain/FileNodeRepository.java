package consulting.segieth.vfs.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileNodeRepository extends JpaRepository<FileNode, Long> {

    Slice<FileNode> findAllBy(Pageable pageable);

    FileNode findByAddress(String address);

    FileNode findBySystemAndAddress(String system, String address);

    @Query("SELECT f FROM FileNode f JOIN f.tags t WHERE t = :tag")
    List<FileNode> findByTag(@Param("tag") consulting.segieth.vfs.domain.Tag tag);

    @Query("SELECT COUNT(f) FROM FileNode f JOIN f.tags t WHERE t = :tag")
    long countByTag(@Param("tag") consulting.segieth.vfs.domain.Tag tag);

    @Query("SELECT f FROM FileNode f WHERE f.contentHash IS NULL AND f.type != 'directory'")
    Slice<FileNode> findWithoutHash(Pageable pageable);

    @Query("SELECT f FROM FileNode f WHERE f.contentHash IN (" +
           "  SELECT f2.contentHash FROM FileNode f2 " +
           "  WHERE f2.contentHash IS NOT NULL " +
           "  GROUP BY f2.contentHash HAVING COUNT(f2) > 1" +
           ") ORDER BY f.contentHash, f.system, f.address")
    List<FileNode> findDuplicates();

    /**
     * Direkte Kinder eines Verzeichnisses: gleicher system-Wert, address beginnt
     * mit parentAddress + Separator, aber enthält danach keinen weiteren Separator.
     * Funktioniert für Windows-Pfade (Backslash) und Unix-Pfade (Slash).
     */
    @Query("SELECT f FROM FileNode f WHERE f.system = :system " +
           "AND f.address LIKE CONCAT(:parentAddress, '%') " +
           "AND f.address <> :parentAddress " +
           "AND (LOCATE('\\', f.address, LENGTH(:parentAddress) + 2) = 0 " +
           "     OR LOCATE('/', f.address, LENGTH(:parentAddress) + 2) = 0)")
    List<FileNode> findDirectChildren(@Param("system") String system,
                                      @Param("parentAddress") String parentAddress);

    @Query("SELECT COUNT(f) FROM FileNode f WHERE f.system = :system " +
           "AND f.address LIKE CONCAT(:parentAddress, '%') " +
           "AND f.address <> :parentAddress " +
           "AND (LOCATE('\\', f.address, LENGTH(:parentAddress) + 2) = 0 " +
           "     OR LOCATE('/', f.address, LENGTH(:parentAddress) + 2) = 0)")
    int countDirectChildren(@Param("system") String system,
                            @Param("parentAddress") String parentAddress);

    /** Alle Dateien (nicht Verzeichnisse) unterhalb eines Pfades */
    @Query("SELECT COUNT(f) FROM FileNode f WHERE f.system = :system " +
           "AND f.address LIKE CONCAT(:parentAddress, '%') " +
           "AND f.type <> 'directory' AND f.type <> 'system'")
    long countFilesUnder(@Param("system") String system,
                         @Param("parentAddress") String parentAddress);

    /** Direkte Dateikinder (nicht Verzeichnisse) */
    @Query("SELECT COUNT(f) FROM FileNode f WHERE f.system = :system " +
           "AND f.address LIKE CONCAT(:parentAddress, '%') " +
           "AND f.address <> :parentAddress " +
           "AND f.type <> 'directory' AND f.type <> 'system' " +
           "AND (LOCATE('\\', f.address, LENGTH(:parentAddress) + 2) = 0 " +
           "     OR LOCATE('/', f.address, LENGTH(:parentAddress) + 2) = 0)")
    long countDirectFiles(@Param("system") String system,
                          @Param("parentAddress") String parentAddress);

    /** Alle Root-FileSystems (type = 'system') */
    List<FileNode> findAllByType(String type);

    /** Prüft ob unterhalb eines Pfades irgendein Knoten mit Duplikat-Hash existiert */
    @Query("SELECT COUNT(f) > 0 FROM FileNode f WHERE f.system = :system " +
           "AND f.address LIKE CONCAT(:parentAddress, '%') " +
           "AND f.contentHash IN (" +
           "  SELECT f2.contentHash FROM FileNode f2 " +
           "  WHERE f2.contentHash IS NOT NULL " +
           "  GROUP BY f2.contentHash HAVING COUNT(f2) > 1)")
    boolean hasDescendantWithDuplicate(@Param("system") String system,
                                       @Param("parentAddress") String parentAddress);

}
