package consulting.segieth.vfs.service;

import consulting.segieth.vfs.domain.Tag;

/**
 * VFSNode-ähnlicher Wrapper für Tags im FileSystem-Tree.
 * Kein Subtyp von VFSNode da Tag kein FileNode ist.
 */
public class TagVFSNode {

    public enum NodeType { ROOT, TAG, FILE }

    private final NodeType nodeType;
    private final Tag tag;        // null wenn ROOT
    private final Object payload; // FileNode wenn FILE

    /** Root-Knoten "🏷 Tags" */
    public static TagVFSNode root() {
        return new TagVFSNode(NodeType.ROOT, null, null);
    }

    public static TagVFSNode ofTag(Tag tag) {
        return new TagVFSNode(NodeType.TAG, tag, null);
    }

    public static TagVFSNode ofFile(consulting.segieth.vfs.domain.FileNode node, Tag tag) {
        return new TagVFSNode(NodeType.FILE, tag, node);
    }

    private TagVFSNode(NodeType nodeType, Tag tag, Object payload) {
        this.nodeType = nodeType;
        this.tag = tag;
        this.payload = payload;
    }

    public NodeType getNodeType() { return nodeType; }
    public Tag getTag() { return tag; }
    public consulting.segieth.vfs.domain.FileNode getFileNode() {
        return payload instanceof consulting.segieth.vfs.domain.FileNode fn ? fn : null;
    }

    public String getName() {
        return switch (nodeType) {
            case ROOT -> "🏷 Tags";
            case TAG  -> (tag.isPublic() ? "🌐 " : "🔒 ") + tag.getName();
            case FILE -> getFileNode() != null ? getFileNode().getName() : "";
        };
    }

    public String getInfo() {
        return switch (nodeType) {
            case ROOT -> "";
            case TAG  -> tag.getFullPath();
            case FILE -> getFileNode() != null ? getFileNode().getAddress() : "";
        };
    }
}
