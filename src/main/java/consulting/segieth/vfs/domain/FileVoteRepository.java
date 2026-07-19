package consulting.segieth.vfs.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileVoteRepository extends JpaRepository<FileVote, Long> {

    Optional<FileVote> findByFileNodeAndUserId(FileNode fileNode, String userId);

    List<FileVote> findByFileNode(FileNode fileNode);

    @Query("SELECT v FROM FileVote v WHERE v.fileNode.address LIKE CONCAT(:folderAddress, '%')")
    List<FileVote> findByFolder(@Param("folderAddress") String folderAddress);

    @Query("SELECT v.vote, COUNT(v) FROM FileVote v WHERE v.fileNode = :node GROUP BY v.vote")
    List<Object[]> countVotesByNode(@Param("node") FileNode node);
}
