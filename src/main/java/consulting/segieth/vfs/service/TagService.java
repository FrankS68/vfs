package consulting.segieth.vfs.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileNodeRepository;
import consulting.segieth.vfs.domain.Tag;
import consulting.segieth.vfs.domain.TagRepository;
import de.witchcafe.auth.AppUserInfo;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final FileNodeRepository fileNodeRepository;

    public TagService(TagRepository tagRepository, FileNodeRepository fileNodeRepository) {
        this.tagRepository = tagRepository;
        this.fileNodeRepository = fileNodeRepository;
    }

    /** Root-Tags für einen User (eigene + öffentliche) */
    public List<Tag> getRootTags(AppUserInfo user) {
        return tagRepository.findRootTagsForUser(user.getUserId().toString());
    }

    /** Kinder eines Tags für einen User */
    public List<Tag> getChildren(Tag parent, AppUserInfo user) {
        return tagRepository.findChildrenForUser(parent, user.getUserId().toString());
    }

    /** Alle Tags für einen User — für Autocomplete/Picker */
    public List<Tag> getAllTags(AppUserInfo user) {
        return tagRepository.findAllForUser(user.getUserId().toString());
    }

    /** Neuen Tag anlegen */
    @Transactional
    public Tag createTag(String name, Tag parent, AppUserInfo user, boolean isPublic) {
        String userId = user.getUserId().toString();
        Optional<Tag> existing = parent == null
                ? tagRepository.findRootTagByNameAndOwnerUserId(name, userId)
                : tagRepository.findByNameAndParentAndOwnerUserId(name, parent, userId);
        if (existing.isPresent()) return existing.get();
        Tag tag = new Tag(name, parent, userId, isPublic);
        return tagRepository.save(tag);
    }

    /** Tag umbenennen */
    @Transactional
    public Tag renameTag(Tag tag, String newName) {
        tag.setName(newName);
        return tagRepository.save(tag);
    }

    /** Tag löschen (entfernt auch alle FileNode-Verknüpfungen) */
    @Transactional
    public void deleteTag(Tag tag) {
        tagRepository.delete(tag);
    }

    /** Tag als öffentlich/privat markieren */
    @Transactional
    public Tag setPublic(Tag tag, boolean isPublic) {
        tag.setPublic(isPublic);
        return tagRepository.save(tag);
    }

    /** Tag einer Datei hinzufügen */
    @Transactional
    public void addTag(FileNode node, Tag tag) {
        // Lazy-Liste laden
        FileNode managed = fileNodeRepository.findById(node.getId()).orElse(node);
        managed.addTag(tag);
        fileNodeRepository.save(managed);
    }

    /** Tag von einer Datei entfernen */
    @Transactional
    public void removeTag(FileNode node, Tag tag) {
        FileNode managed = fileNodeRepository.findById(node.getId()).orElse(node);
        managed.removeTag(tag);
        fileNodeRepository.save(managed);
    }

    /** Tags einer Datei laden */
    public List<Tag> getTagsForNode(FileNode node) {
        FileNode managed = fileNodeRepository.findById(node.getId()).orElse(node);
        return managed.getTags();
    }

    /** Dateien unter einem Tag — via Repository Query statt lazy Collection */
    public List<FileNode> getFilesForTag(Tag tag) {
        return fileNodeRepository.findByTag(tag);
    }

    /** Hat ein Tag Kinder oder getaggte Dateien? */
    public boolean hasChildren(Tag tag, AppUserInfo user) {
        if (!getChildren(tag, user).isEmpty()) return true;
        return fileNodeRepository.countByTag(tag) > 0;
    }
}
