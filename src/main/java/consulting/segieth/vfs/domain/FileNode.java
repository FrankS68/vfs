package consulting.segieth.vfs.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.validation.constraints.Size;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class FileNode extends AbstractEntity<Long> {
	
	public class NoDirectoryException extends RuntimeException{
		public NoDirectoryException(String address) {
			super(String.format("Address %s is no directory.", address));
		}
	}

	public class DoesntExistException extends RuntimeException{
		public DoesntExistException(String address) {
			super(String.format("Address %s doesn't exist.", address));
		}
	}

    public static final int DESCRIPTION_MAX_LENGTH = 255;

    protected static HashMap<String, FileNode> fileNodes = new HashMap<String, FileNode>();
    public static HashMap<String, FileNode> getFileNodes() {
		return fileNodes;
	}

	@Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "fsystem_id")
    private Long id;

    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX_LENGTH)
    @Size(max = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "synch_date", nullable = false)
    private LocalDate synchDate;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "system", nullable = false)
    private String system;

    @Column(name = "type", nullable = false)
    private String type;

    @Size(max = 255)
    @Column(name = "address", nullable = false)
    private String address;
    
    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_date")
    private LocalDate createdDate;

    @Column(name = "modified_date")
    private LocalDate modifiedDate;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "file_node_tag",
        joinColumns = @JoinColumn(name = "file_node_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>();

    public List<Tag> getTags() { return tags; }
    public void addTag(Tag tag) { if (!tags.contains(tag)) tags.add(tag); }
    public void removeTag(Tag tag) { tags.remove(tag); }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }

    public LocalDate getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(LocalDate modifiedDate) { this.modifiedDate = modifiedDate; }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public boolean hasContentHash() {
        return contentHash != null && !contentHash.isEmpty();
    }

    /*
    private Path path;

    public Path getPath() {
		return path;
	}
     */
	public FileNode() {
	}

	public FileNode(String s,String a) {
    	this(s,a,a);
	}

    public FileNode(String s,String a,String n) {
    	system = s;
    	address = a;
    	name = n;
    	fileNodes.put(a, this);
	}

	@Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public LocalDate getSynchDate() {
        return synchDate;
    }

    public void setSynchDate(LocalDate synchDate) {
        this.synchDate = synchDate;
    }

}
