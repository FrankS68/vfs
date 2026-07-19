package consulting.segieth.vfs.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ImageRepository extends JpaRepository<ImageFile, Long>, JpaSpecificationExecutor<ImageFile> {

    Slice<ImageFile> findAllBy(Pageable pageable);
    List<ImageFile> findAll();

    ImageFile findByAddress(String address);
    ImageFile findBySystemAndAddress(String system,String address);

    @Query("SELECT i FROM ImageFile i WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(i.address) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Slice<ImageFile> findByQuery(@org.springframework.data.repository.query.Param("query") String query, Pageable pageable);

    @Query("SELECT i FROM ImageFile i JOIN i.tags t WHERE t.id = :tagId")
    Slice<ImageFile> findByTagId(@org.springframework.data.repository.query.Param("tagId") Long tagId, Pageable pageable);

    @Query("SELECT COUNT(i) > 0 FROM ImageFile i WHERE i.address LIKE CONCAT(:folderAddress, '%')")
    boolean hasImagesUnder(@org.springframework.data.repository.query.Param("folderAddress") String folderAddress);

    @Query("SELECT i FROM ImageFile i WHERE i.address LIKE CONCAT(:folderAddress, '%')")
    Slice<ImageFile> findByFolder(@org.springframework.data.repository.query.Param("folderAddress") String folderAddress, Pageable pageable);

}
