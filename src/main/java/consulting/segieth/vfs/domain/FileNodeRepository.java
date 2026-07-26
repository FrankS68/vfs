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

    /** Google Docs/Sheets/Slides/Forms & Co sind nur Cloud-Verknüpfungen ohne echten lokalen
     * Dateiinhalt (Google Drive legt dafür bloß einen Platzhalter an) — Hash-Berechnung schlägt
     * dafür immer fehl und ist inhaltlich auch sinnlos, deshalb von der Hash-Pflicht ausgenommen. */
    @Query("SELECT f FROM FileNode f WHERE f.contentHash IS NULL AND f.type != 'directory' " +
           "AND f.address NOT LIKE '%.gdoc' AND f.address NOT LIKE '%.gsheet' " +
           "AND f.address NOT LIKE '%.gslides' AND f.address NOT LIKE '%.gform' " +
           "AND f.address NOT LIKE '%.gdraw' AND f.address NOT LIKE '%.gsite' " +
           "AND f.address NOT LIKE '%.gtable' AND f.address NOT LIKE '%.gmap'")
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

    /** Anzahl Dateien (keine Verzeichnisse) unterhalb eines Pfades ohne berechneten Hash.
     * Google-Cloud-Verknüpfungen (.gdoc etc., siehe findWithoutHash) zählen nicht mit. */
    @Query("SELECT COUNT(f) FROM FileNode f WHERE f.address LIKE CONCAT(:addressPrefix, '%') " +
           "AND f.contentHash IS NULL AND f.type <> 'directory' AND f.type <> 'system' " +
           "AND f.address NOT LIKE '%.gdoc' AND f.address NOT LIKE '%.gsheet' " +
           "AND f.address NOT LIKE '%.gslides' AND f.address NOT LIKE '%.gform' " +
           "AND f.address NOT LIKE '%.gdraw' AND f.address NOT LIKE '%.gsite' " +
           "AND f.address NOT LIKE '%.gtable' AND f.address NOT LIKE '%.gmap'")
    long countMissingHashByAddress(@Param("addressPrefix") String addressPrefix);

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
