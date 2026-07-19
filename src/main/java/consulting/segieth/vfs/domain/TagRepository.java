package consulting.segieth.vfs.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** Root-Tags eines Users + alle öffentlichen Root-Tags */
    @Query("SELECT t FROM Tag t WHERE t.parent IS NULL AND (t.ownerUserId = :userId OR t.isPublic = true)")
    List<Tag> findRootTagsForUser(@Param("userId") String userId);

    /** Kinder eines Tags die ein User sehen darf */
    @Query("SELECT t FROM Tag t WHERE t.parent = :parent AND (t.ownerUserId = :userId OR t.isPublic = true)")
    List<Tag> findChildrenForUser(@Param("parent") Tag parent, @Param("userId") String userId);

    /** Alle Tags eines Users + öffentliche */
    @Query("SELECT t FROM Tag t WHERE t.ownerUserId = :userId OR t.isPublic = true ORDER BY t.name")
    List<Tag> findAllForUser(@Param("userId") String userId);

    Optional<Tag> findByNameAndParentAndOwnerUserId(String name, Tag parent, String ownerUserId);

    @Query("SELECT t FROM Tag t WHERE t.name = :name AND t.parent IS NULL AND t.ownerUserId = :ownerUserId")
    Optional<Tag> findRootTagByNameAndOwnerUserId(@Param("name") String name, @Param("ownerUserId") String ownerUserId);
}
