package consulting.segieth.vfs.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "tag", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "parent_id", "owner_user_id"})
})
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private Tag parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tag> children = new ArrayList<>();

    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    private List<FileNode> fileNodes = new ArrayList<>();

    public Tag() {}

    public Tag(String name, Tag parent, String ownerUserId, boolean isPublic) {
        this.name = name;
        this.parent = parent;
        this.ownerUserId = ownerUserId;
        this.isPublic = isPublic;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Tag getParent() { return parent; }
    public void setParent(Tag parent) { this.parent = parent; }
    public List<Tag> getChildren() { return children; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public List<FileNode> getFileNodes() { return fileNodes; }

    /** Vollständiger Pfad z.B. "Urlaub/Kreta" */
    public String getFullPath() {
        if (parent == null) return name;
        return parent.getFullPath() + "/" + name;
    }

    @Override
    public String toString() { return getFullPath(); }
}
