package consulting.segieth.vfs.ui.view;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import consulting.segieth.vfs.domain.FileVote.Vote;
import consulting.segieth.vfs.domain.FileVote;
import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.service.DuplicateService;
import consulting.segieth.vfs.service.TriageService;
import de.witchcafe.auth.AppUserInfo;
import de.witchcafe.auth.CurrentUser;
import jakarta.annotation.security.PermitAll;

@Route("triage")
@PageTitle("Triage")
@Menu(order = 4, icon = "vaadin:flag", title = "Triage")
@PermitAll
public class TriageView extends VerticalLayout implements BeforeEnterObserver {

    private final TriageService triageService;
    private final DuplicateService duplicateService;
    private final CurrentUser currentUser;

    private List<ImageFile> images;
    private int currentIndex = 0;
    private String folderAddress;
    private Long tagId;
    private boolean isAdmin;

    // UI-Elemente
    private final Image mainImage;
    private final Span fileNameLabel;
    private final Span progressLabel;
    private final ProgressBar progressBar;
    private final Span voteSummaryLabel;
    private final Div otherVotesDiv;
    private final Button keepBtn;
    private final Button trashBtn;
    private final Button undecidedBtn;
    private final Button prevBtn;
    private final Button nextBtn;
    private final Button adminBtn;
    private final Span seriesHint;

    public TriageView(TriageService triageService, DuplicateService duplicateService, CurrentUser currentUser) {
        this.triageService = triageService;
        this.duplicateService = duplicateService;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        // Header
        H3 heading = new H3("Triage");
        Span folderLabel = new Span();
        folderLabel.getStyle().set("color", "gray").set("font-size", "0.85em");

        // Bild
        mainImage = new Image();
        mainImage.setMaxWidth("100%");
        mainImage.setMaxHeight("60vh");
        mainImage.getStyle().set("object-fit", "contain");

        Div imageWrapper = new Div(mainImage);
        imageWrapper.getStyle()
                .set("text-align", "center")
                .set("background", "#111")
                .set("border-radius", "8px")
                .set("padding", "12px")
                .set("min-height", "300px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center");
        imageWrapper.setWidthFull();

        // Dateiname + Serien-Hinweis
        fileNameLabel = new Span();
        fileNameLabel.getStyle().set("font-weight", "bold");

        seriesHint = new Span();
        seriesHint.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "0.8em")
                .set("margin-left", "8px");
        seriesHint.setVisible(false);

        HorizontalLayout nameRow = new HorizontalLayout(fileNameLabel, seriesHint);
        nameRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        // Fortschritt
        progressLabel = new Span();
        progressBar = new ProgressBar();
        progressBar.setWidthFull();

        // Votes anderer User
        otherVotesDiv = new Div();
        otherVotesDiv.getStyle().set("font-size", "0.85em").set("color", "#666");

        voteSummaryLabel = new Span();
        voteSummaryLabel.getStyle().set("font-size", "0.85em");

        // Buttons
        prevBtn = new Button(VaadinIcon.ARROW_LEFT.create(), e -> navigate(-1));
        prevBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        keepBtn = new Button("👍 Behalten", e -> castVote(Vote.KEEP));
        keepBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        keepBtn.getStyle().set("min-width", "140px");

        undecidedBtn = new Button("↩ Zurücksetzen", e -> castVote(Vote.UNDECIDED));
        undecidedBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        trashBtn = new Button("🗑 Trash", e -> castVote(Vote.TRASH));
        trashBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        trashBtn.getStyle().set("min-width", "140px");

        nextBtn = new Button(VaadinIcon.ARROW_RIGHT.create(), e -> navigate(1));
        nextBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        adminBtn = new Button("Admin: Trash ausführen", e -> showAdminDialog());
        adminBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        adminBtn.setVisible(false);

        // Serien-Button
        Button seriesBtn = new Button("📽 Serien anzeigen", e -> showSeriesDialog());
        seriesBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout voteBar = new HorizontalLayout(prevBtn, keepBtn, undecidedBtn, trashBtn, nextBtn);
        voteBar.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        voteBar.setJustifyContentMode(JustifyContentMode.CENTER);

        HorizontalLayout adminBar = new HorizontalLayout(seriesBtn, adminBtn);
        adminBar.setJustifyContentMode(JustifyContentMode.END);
        adminBar.setWidthFull();

        add(heading, folderLabel, progressBar, progressLabel,
            imageWrapper, nameRow, voteSummaryLabel, otherVotesDiv,
            voteBar, adminBar);

        // Tastatur-Shortcuts
        addShortcut(Key.ARROW_RIGHT, () -> navigate(1));
        addShortcut(Key.ARROW_LEFT, () -> navigate(-1));
        addShortcut(Key.KEY_K, () -> castVote(Vote.KEEP));
        addShortcut(Key.KEY_T, () -> castVote(Vote.TRASH));
        addShortcut(Key.KEY_U, () -> castVote(Vote.UNDECIDED));
    }

    private void addShortcut(Key key, Runnable action) {
        getElement().addEventListener("keydown", e -> action.run())
                .setFilter("event.key === '" + key.getKeys().get(0) + "'");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters().getParameters();
        if (params.containsKey("folder")) {
            folderAddress = java.net.URLDecoder.decode(
                    params.get("folder").get(0), java.nio.charset.StandardCharsets.UTF_8);
            tagId = null;
        } else if (params.containsKey("tag")) {
            tagId = Long.parseLong(params.get("tag").get(0));
            folderAddress = null;
        }
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        if (folderAddress == null && tagId == null) return;

        AppUserInfo user = currentUser.require();
        isAdmin = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        adminBtn.setVisible(isAdmin);

        images = tagId != null
                ? triageService.getImagesForTag(tagId)
                : triageService.getImagesInFolder(folderAddress);
        currentIndex = 0;

        if (images.isEmpty()) {
            Notification.show("Keine Bilder in diesem Ordner.", 3000, Notification.Position.TOP_CENTER);
            return;
        }

        updateView();
    }

    /** Navigiert zur TriageView für einen Ordner */
    public static void openForFolder(com.vaadin.flow.component.UI ui, String folderAddress) {
        ui.navigate("triage", QueryParameters.of("folder",
                java.net.URLEncoder.encode(folderAddress, java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void navigate(int delta) {
        if (images == null || images.isEmpty()) return;
        currentIndex = Math.max(0, Math.min(images.size() - 1, currentIndex + delta));
        updateView();
    }

    private void castVote(Vote vote) {
        if (images == null || images.isEmpty()) return;
        ImageFile current = images.get(currentIndex);
        AppUserInfo user = currentUser.require();
        triageService.vote(current, user, vote);
        updateVoteDisplay(current);

        // Automatisch zum nächsten wenn nicht letztes Bild
        if (vote != Vote.UNDECIDED && currentIndex < images.size() - 1) {
            navigate(1);
        }
    }

    private void updateView() {
        if (images == null || images.isEmpty()) return;
        ImageFile current = images.get(currentIndex);

        // Fortschritt
        progressBar.setValue((double) (currentIndex + 1) / images.size());
        progressLabel.setText((currentIndex + 1) + " von " + images.size());

        // Bild
        if (current.hasThumbnail()) {
            StreamResource res = new StreamResource(current.getName(),
                    () -> new ByteArrayInputStream(current.getThumbnail()));
            mainImage.setSrc(res);
        } else {
            mainImage.setSrc("icons/icon.png");
        }

        // Original asynchron laden
        var ui = getUI().orElse(null);
        if (ui != null) {
            Thread thread = new Thread(() -> {
                try {
                    java.io.File file = current.getPath().toFile();
                    if (file.exists()) {
                        StreamResource orig = new StreamResource(current.getName(),
                                () -> {
                                    try { return new java.io.FileInputStream(file); }
                                    catch (Exception ex) { return new ByteArrayInputStream(new byte[0]); }
                                });
                        ui.access(() -> mainImage.setSrc(orig));
                    }
                } catch (Exception ignored) {}
            });
            thread.start();
        }

        fileNameLabel.setText(current.getName());

        // Serien-Hinweis
        Map<String, List<ImageFile>> series = triageService.detectSeries(images);
        boolean inSeries = series.values().stream().anyMatch(g -> g.contains(current));
        seriesHint.setVisible(inSeries);
        if (inSeries) {
            String seriesName = series.entrySet().stream()
                    .filter(e -> e.getValue().contains(current))
                    .map(e -> "Serie: " + e.getKey() + " (" + e.getValue().size() + " Bilder)")
                    .findFirst().orElse("");
            seriesHint.setText(seriesName);
        }

        updateVoteDisplay(current);
        prevBtn.setEnabled(currentIndex > 0);
        nextBtn.setEnabled(currentIndex < images.size() - 1);
    }

    private void updateVoteDisplay(ImageFile current) {
        AppUserInfo user = currentUser.require();
        Vote myVote = triageService.getVote(current, user);
        String myUserId = user.getUserId().toString();

        // Eigenen Vote hervorheben
        keepBtn.getStyle().remove("box-shadow");
        trashBtn.getStyle().remove("box-shadow");
        undecidedBtn.getStyle().remove("box-shadow");
        switch (myVote) {
            case KEEP -> keepBtn.getStyle().set("box-shadow", "0 0 0 3px var(--lumo-success-color)");
            case TRASH -> trashBtn.getStyle().set("box-shadow", "0 0 0 3px var(--lumo-error-color)");
            default -> {}
        }

        // Votes anderer User
        List<FileVote> votes = triageService.getVotes(current);
        Map<Vote, Long> summary = triageService.getVoteSummary(current);
        long keeps = summary.getOrDefault(Vote.KEEP, 0L);
        long trashes = summary.getOrDefault(Vote.TRASH, 0L);
        voteSummaryLabel.setText("👍 " + keeps + "  🗑 " + trashes);

        otherVotesDiv.removeAll();
        for (FileVote v : votes) {
            if (v.getUserId().equals(myUserId)) continue;
            String icon = v.getVote() == Vote.KEEP ? "👍" : v.getVote() == Vote.TRASH ? "🗑" : "↩";
            Span s = new Span(icon + " " + v.getUserName());
            s.getStyle().set("margin-right", "12px");
            otherVotesDiv.add(s);
        }
    }

    private void showSeriesDialog() {
        if (images == null) return;
        Map<String, List<ImageFile>> series = triageService.detectSeries(images);
        if (series.isEmpty()) {
            Notification.show("Keine Serienbilder erkannt.", 2000, Notification.Position.TOP_CENTER);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setWidth("600px");
        dialog.setDraggable(true);

        VerticalLayout content = new VerticalLayout();
        content.add(new H3("Erkannte Serienbilder"));

        for (Map.Entry<String, List<ImageFile>> entry : series.entrySet()) {
            Span title = new Span(entry.getKey() + " — " + entry.getValue().size() + " Bilder");
            title.getStyle().set("font-weight", "bold");

            Button voteAllTrash = new Button("Alle → Trash", e -> {
                AppUserInfo user = currentUser.require();
                entry.getValue().forEach(img -> triageService.vote(img, user, Vote.TRASH));
                dialog.close();
                updateView();
                Notification.show("Serie als Trash markiert.", 2000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
            voteAllTrash.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            Button voteAllKeep = new Button("Alle → Behalten", e -> {
                AppUserInfo user = currentUser.require();
                entry.getValue().forEach(img -> triageService.vote(img, user, Vote.KEEP));
                dialog.close();
                updateView();
                Notification.show("Serie als Behalten markiert.", 2000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
            voteAllKeep.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);

            // Vorschau der ersten 3 Bilder
            HorizontalLayout previews = new HorizontalLayout();
            entry.getValue().stream().limit(3).forEach(img -> {
                if (img.hasThumbnail()) {
                    StreamResource res = new StreamResource(img.getName(),
                            () -> new ByteArrayInputStream(img.getThumbnail()));
                    Image thumb = new Image(res, img.getName());
                    thumb.setWidth("80px");
                    thumb.setHeight("60px");
                    thumb.getStyle().set("object-fit", "cover").set("border-radius", "4px");
                    previews.add(thumb);
                }
            });
            if (entry.getValue().size() > 3) {
                previews.add(new Span("+" + (entry.getValue().size() - 3) + " weitere"));
            }

            HorizontalLayout actions = new HorizontalLayout(voteAllKeep, voteAllTrash);
            content.add(title, previews, actions);
            content.add(new com.vaadin.flow.component.html.Hr());
        }

        Button closeBtn = new Button("Schließen", e -> dialog.close());
        content.add(closeBtn);
        dialog.add(content);
        dialog.open();
    }

    private void showAdminDialog() {
        if (folderAddress == null) return;
        List<consulting.segieth.vfs.domain.FileNode> candidates =
                triageService.getAdminTrashCandidates(folderAddress);

        Dialog dialog = new Dialog();
        dialog.setWidth("600px");
        dialog.setDraggable(true);

        VerticalLayout content = new VerticalLayout();
        content.add(new H3("Admin: Trash ausführen"));

        if (candidates.isEmpty()) {
            content.add(new Span("Keine Kandidaten — kein Konsens für Trash."));
        } else {
            content.add(new Span(candidates.size() + " Dateien haben mehr TRASH als KEEP Votes:"));
            for (consulting.segieth.vfs.domain.FileNode node : candidates) {
                content.add(new Span("🗑 " + node.getName() + " (" + node.getAddress() + ")"));
            }

            Button executeBtn = new Button("Jetzt in Trash verschieben", e -> {
                int success = 0, failed = 0;
                for (consulting.segieth.vfs.domain.FileNode node : candidates) {
                    try {
                        duplicateService.moveToTrash(node);
                        success++;
                    } catch (Exception ex) {
                        failed++;
                        System.err.println("Triage Trash fehlgeschlagen: " + ex.getMessage());
                    }
                }
                dialog.close();
                images = triageService.getImagesInFolder(folderAddress);
                currentIndex = Math.min(currentIndex, Math.max(0, images.size() - 1));
                updateView();
                String msg = success + " Dateien in Trash verschoben.";
                if (failed > 0) msg += " " + failed + " fehlgeschlagen.";
                Notification.show(msg, 4000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(failed > 0
                                ? NotificationVariant.LUMO_WARNING
                                : NotificationVariant.LUMO_SUCCESS);
            });
            executeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
            content.add(executeBtn);
        }

        content.add(new Button("Abbrechen", e -> dialog.close()));
        dialog.add(content);
        dialog.open();
    }
}
