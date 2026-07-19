package consulting.segieth.vfs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileVote;
import consulting.segieth.vfs.domain.FileVote.Vote;
import consulting.segieth.vfs.domain.FileVoteRepository;
import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.domain.ImageRepository;
import de.witchcafe.auth.AppUserInfo;

@Service
public class TriageService {

    private static final Pattern SERIES_PATTERN =
        Pattern.compile("^(.*?)(\\d+)(\\.[^.]+)$");

    private final FileVoteRepository voteRepository;
    private final ImageRepository imageRepository;

    public TriageService(FileVoteRepository voteRepository, ImageRepository imageRepository) {
        this.voteRepository = voteRepository;
        this.imageRepository = imageRepository;
    }

    /** Alle Bilder in einem Ordner (direkt, nicht rekursiv) */
    public List<ImageFile> getImagesInFolder(String folderAddress) {
        String normalized = folderAddress.endsWith("\\") || folderAddress.endsWith("/")
                ? folderAddress : folderAddress + "\\";
        // Alle Images die mit dem Ordnerpfad beginnen aber kein weiteres Trennzeichen haben
        List<ImageFile> all = new ArrayList<>();
        int page = 0;
        Slice<ImageFile> slice;
        do {
            slice = imageRepository.findByFolder(normalized, PageRequest.of(page++, 100));
            for (ImageFile img : slice.getContent()) {
                String rest = img.getAddress().substring(normalized.length());
                if (!rest.contains("\\") && !rest.contains("/")) {
                    all.add(img);
                }
            }
        } while (slice.hasNext());
        // Alphabetisch sortieren
        all.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return all;
    }

    /** Alle Bilder unter einem Tag */
    public List<ImageFile> getImagesForTag(Long tagId) {
        List<ImageFile> all = new ArrayList<>();
        int page = 0;
        Slice<ImageFile> slice;
        do {
            slice = imageRepository.findByTagId(tagId, PageRequest.of(page++, 100));
            all.addAll(slice.getContent());
        } while (slice.hasNext());
        all.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return all;
    }

    /** Erkennt Serienbilder — Gruppen mit gleichem Prefix und aufsteigenden Nummern */
    public Map<String, List<ImageFile>> detectSeries(List<ImageFile> images) {
        Map<String, List<ImageFile>> groups = new LinkedHashMap<>();
        for (ImageFile img : images) {
            Matcher m = SERIES_PATTERN.matcher(img.getName());
            if (m.matches()) {
                String key = m.group(1) + m.group(3); // prefix + extension
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(img);
            }
        }
        // Nur Gruppen mit mehr als einem Bild zurückgeben
        return groups.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /** Vote eines Users für eine Datei setzen oder aktualisieren */
    @Transactional
    public FileVote vote(FileNode node, AppUserInfo user, Vote vote) {
        String userId = user.getUserId().toString();
        String userName = user.getPreferredUsername();
        Optional<FileVote> existing = voteRepository.findByFileNodeAndUserId(node, userId);
        FileVote fileVote = existing.orElse(new FileVote(node, userId, userName, vote));
        fileVote.setVote(vote);
        return voteRepository.save(fileVote);
    }

    /** Vote eines Users für eine Datei abrufen */
    public Vote getVote(FileNode node, AppUserInfo user) {
        String userId = user.getUserId().toString();
        return voteRepository.findByFileNodeAndUserId(node, userId)
                .map(FileVote::getVote)
                .orElse(Vote.UNDECIDED);
    }

    /** Alle Votes für eine Datei (für die Anzeige anderer User-Votes) */
    public List<FileVote> getVotes(FileNode node) {
        return voteRepository.findByFileNode(node);
    }

    /** Zusammenfassung: wie viele User haben wie gevotet */
    public Map<Vote, Long> getVoteSummary(FileNode node) {
        Map<Vote, Long> summary = new LinkedHashMap<>();
        for (Vote v : Vote.values()) summary.put(v, 0L);
        for (Object[] row : voteRepository.countVotesByNode(node)) {
            summary.put((Vote) row[0], (Long) row[1]);
        }
        return summary;
    }

    /** Alle Votes für einen Ordner — für Admin-Entscheidung */
    public Map<FileNode, List<FileVote>> getVotesByFolder(String folderAddress) {
        String normalized = folderAddress.endsWith("\\") || folderAddress.endsWith("/")
                ? folderAddress : folderAddress + "\\";
        List<FileVote> votes = voteRepository.findByFolder(normalized);
        return votes.stream().collect(Collectors.groupingBy(FileVote::getFileNode));
    }

    /** Admin: Trash-Votes ausführen via DuplicateService/MoveService */
    public List<FileNode> getAdminTrashCandidates(String folderAddress) {
        Map<FileNode, List<FileVote>> byNode = getVotesByFolder(folderAddress);
        List<FileNode> candidates = new ArrayList<>();
        for (Map.Entry<FileNode, List<FileVote>> entry : byNode.entrySet()) {
            long trashVotes = entry.getValue().stream()
                    .filter(v -> v.getVote() == Vote.TRASH).count();
            long keepVotes = entry.getValue().stream()
                    .filter(v -> v.getVote() == Vote.KEEP).count();
            // Kandidat wenn mehr TRASH als KEEP votes
            if (trashVotes > keepVotes) candidates.add(entry.getKey());
        }
        return candidates;
    }
}
