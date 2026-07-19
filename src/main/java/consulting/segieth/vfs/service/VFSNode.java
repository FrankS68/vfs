package consulting.segieth.vfs.service;

import java.util.HashMap;

import consulting.segieth.vfs.domain.FileNode;

public class VFSNode<T extends FileNode>{
	private static HashMap<FileNode, VFSNode> vfsNodesByFileSystem = new HashMap<FileNode, VFSNode>();
	
	public static VFSNode getByFileNode(FileNode fs) {
		return vfsNodesByFileSystem.get(fs);
	}
	
	protected T fs;
	private boolean hasDuplicate = false;
	private boolean hasImages = false;

	public VFSNode(T fs) {
		this.fs = fs;
		vfsNodesByFileSystem.put(fs, this);
	}

	public boolean isHasDuplicate() { return hasDuplicate; }
	public void setHasDuplicate(boolean hasDuplicate) { this.hasDuplicate = hasDuplicate; }

	public boolean isHasImages() { return hasImages; }
	public void setHasImages(boolean hasImages) { this.hasImages = hasImages; }
	
	public String getName() {
		return fs.getName().isEmpty() ? fs.getSystem()+"|" + fs.getAddress() : fs.getName();
	}
	
	public String getInfo() {
		return "#"+fs.getId();
	}
	
	public T getFileNode() {
		return fs;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;
		if (!(o instanceof VFSNode)) return false;
		VFSNode<?> other = (VFSNode<?>) o;
		if (fs == null || other.fs == null) return fs == other.fs;
		if (fs.getId() == null || other.fs.getId() == null) return false;
		return fs.getId().equals(other.fs.getId());
	}

	@Override
	public int hashCode() {
		if (fs == null || fs.getId() == null) return System.identityHashCode(this);
		return fs.getId().hashCode();
	}
}