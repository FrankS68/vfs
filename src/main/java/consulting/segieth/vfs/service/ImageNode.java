package consulting.segieth.vfs.service;

import consulting.segieth.vfs.domain.ImageFile;

public class ImageNode extends VFSNode<ImageFile> {

	public ImageNode(ImageFile fs) {
		super(fs);
	}

	public String getInfo() {
		return fs.getDescription();
	}
}
