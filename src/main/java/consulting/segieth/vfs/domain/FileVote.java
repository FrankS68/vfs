package consulting.segieth.vfs.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "file_vote", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"file_node_id", "user_id"})
})
public class FileVote {

    public enum Vote { KEEP, TRASH, UNDECIDED }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "file_node_id", nullable = false)
    private FileNode fileNode;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Vote vote = Vote.UNDECIDED;

    @Column(name = "voted_at")
    private LocalDateTime votedAt;

    public FileVote() {}

    public FileVote(FileNode fileNode, String userId, String userName, Vote vote) {
        this.fileNode = fileNode;
        this.userId = userId;
        this.userName = userName;
        this.vote = vote;
        this.votedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public FileNode getFileNode() { return fileNode; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName != null ? userName : userId; }
    public Vote getVote() { return vote; }
    public void setVote(Vote vote) { this.vote = vote; this.votedAt = LocalDateTime.now(); }
    public LocalDateTime getVotedAt() { return votedAt; }
}
