package consulting.segieth.vfs.domain;

import java.util.HashMap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

@Entity
@Table(name = "fsystem")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class FileSystem extends FileNode {

    protected static HashMap<String, FileSystem> fileSystems = new HashMap<String, FileSystem>();

    @Column(name = "trash_path")
    private String trashPath;

    public String getTrashPath() {
        return trashPath;
    }

    public void setTrashPath(String trashPath) {
        this.trashPath = trashPath;
    }
    
    public static HashMap<String, FileSystem> getFileSystems() {
		return fileSystems;
	}

	public FileSystem() {
	}

	public FileSystem(String s,String a) {
    	this(s,a,a);
	}


    public FileSystem(String s,String a,String n) {
    	super(s,a,n);
    	fileSystems.put(a, this);
	}

}
