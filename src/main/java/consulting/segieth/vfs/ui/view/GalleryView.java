package consulting.segieth.vfs.ui.view;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.domain.TagRepository;
import consulting.segieth.vfs.service.DuplicateService;
import consulting.segieth.vfs.service.ImageService;
import consulting.segieth.vfs.service.MoveService;
import consulting.segieth.vfs.service.TagService;
import consulting.segieth.vfs.service.TreeService;
import de.witchcafe.auth.AppUserInfo;
import de.witchcafe.auth.CurrentUser;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("gallery")
@PageTitle("Gallery")
@AnonymousAllowed // lesen ohne Anmeldung, ändernde Aktionen nur angemeldet
public class GalleryView extends VerticalLayout implements BeforeEnterObserver {

    private static final int PAGE_SIZE = 20;

    private final ImageService service;
    private final TagService tagService;
    private final CurrentUser currentUser;
    /** Ohne Anmeldung nur ansehen: Taggen und Verschieben werden nicht angezeigt */
    private final boolean canEdit;
    private final MoveService moveService;
    private final TreeService treeService;
    private final TagRepository tagRepository;

    private final FlexLayout imageContainer;
    private final TextField searchField;
    private final Span folderBanner;
    private final HorizontalLayout selectionBar;
    private final Span selectionLabel;

    private int currentPage = 0;
    private boolean loading = false;
    private boolean hasMore = true;
    private String currentQuery = "";
    private String folderFilter = null;
    private Long tagFilter = null;
    private List<Long> idsFilter = null;

    // Selektion
    private final Set<ImageFile> selected = new LinkedHashSet<>();
    // Alle bisher geladenen Bilder in Reihenfolge
    private final List<ImageFile> loadedImages = new ArrayList<>();

    public static void openForFolder(UI ui, String folderAddress) {
        ui.navigate("gallery", QueryParameters.of("folder",
                java.net.URLEncoder.encode(folderAddress, java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** Öffnet die Gallery mit einer festen Liste von Bild-IDs (z.B. "nur hier"/"nur dort" beim Ordner-Vergleich). */
    public static void openForIds(UI ui, List<Long> ids, String label) {
        String idsParam = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        ui.navigate("gallery", QueryParameters.simple(java.util.Map.of(
                "ids", idsParam,
                "label", java.net.URLEncoder.encode(label, java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters().getParameters();
        if (params.containsKey("ids") && !params.get("ids").isEmpty()) {
            idsFilter = java.util.Arrays.stream(params.get("ids").get(0).split(","))
                    .filter(s -> !s.isBlank())
                    .map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toList());
            folderFilter = null;
            tagFilter = null;
            String label = params.containsKey("label") && !params.get("label").isEmpty()
                    ? java.net.URLDecoder.decode(params.get("label").get(0), java.nio.charset.StandardCharsets.UTF_8)
                    : "Auswahl";
            folderBanner.setText(label + " (" + idsFilter.size() + " Dateien)");
            folderBanner.setVisible(true);
        } else if (params.containsKey("folder") && !params.get("folder").isEmpty()) {
            folderFilter = java.net.URLDecoder.decode(
                    params.get("folder").get(0), java.nio.charset.StandardCharsets.UTF_8);
            tagFilter = null;
            idsFilter = null;
            folderBanner.setText("Ordner: " + folderFilter);
            folderBanner.setVisible(true);
        } else if (params.containsKey("tag") && !params.get("tag").isEmpty()
                && isVisibleTag(Long.parseLong(params.get("tag").get(0)))) {
            tagFilter = Long.parseLong(params.get("tag").get(0));
            folderFilter = null;
            idsFilter = null;
            String tagName = tagRepository.findById(tagFilter)
                    .map(t -> t.getFullPath())
                    .orElse("#" + tagFilter);
            folderBanner.setText("Tag: " + tagName);
            folderBanner.setVisible(true);
        } else {
            folderFilter = null;
            tagFilter = null;
            idsFilter = null;
            folderBanner.setVisible(false);
        }
        startSearch();
    }

    public GalleryView(ImageService service, TagService tagService, CurrentUser currentUser,
                       MoveService moveService, TreeService treeService, TagRepository tagRepository) {
        this.service = service;
        this.tagService = tagService;
        this.currentUser = currentUser;
        this.canEdit = currentUser.get().isPresent();
        this.moveService = moveService;
        this.treeService = treeService;
        this.tagRepository = tagRepository;

        // Folder-Banner
        folderBanner = new Span();
        folderBanner.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("padding", "4px 12px")
                .set("border-radius", "4px")
                .set("font-size", "0.85em");
        folderBanner.setVisible(false);
        Button clearFolderBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL), e -> {
            folderFilter = null;
            idsFilter = null;
            folderBanner.setVisible(false);
            startSearch();
        });
        clearFolderBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout bannerRow = new HorizontalLayout(folderBanner, clearFolderBtn);
        bannerRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        bannerRow.setVisible(false);
        folderBanner.addAttachListener(ev -> bannerRow.setVisible(folderBanner.isVisible()));

        // Suchfeld
        searchField = new TextField();
        searchField.setPlaceholder("Bilder suchen... (Name, Pfad, Beschreibung)");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setClearButtonVisible(true);
        searchField.setWidthFull();

        Button searchButton = new Button("Suchen", event -> startSearch());
        searchButton.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        HorizontalLayout searchBar = new HorizontalLayout(searchField, searchButton);
        searchBar.setVerticalComponentAlignment(Alignment.END, searchButton);
        searchBar.setWidthFull();

        // Selektions-Aktionsleiste (anfangs versteckt)
        selectionLabel = new Span("0 ausgewählt");
        selectionLabel.getStyle().set("font-weight", "bold");

        Button tagSelectedBtn = new Button("🏷 Taggen", e -> tagSelected());
        tagSelectedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        Button moveSelectedBtn = new Button("📁 Verschieben", e -> moveSelected());
        moveSelectedBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        tagSelectedBtn.setVisible(canEdit);
        moveSelectedBtn.setVisible(canEdit);

        Button clearSelectionBtn = new Button("✕ Auswahl aufheben", e -> clearSelection());
        clearSelectionBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        selectionBar = new HorizontalLayout(selectionLabel, tagSelectedBtn, moveSelectedBtn, clearSelectionBtn);
        selectionBar.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        selectionBar.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("padding", "8px 12px")
                .set("border-radius", "6px");
        selectionBar.setVisible(false);

        // Bild-Container
        imageContainer = new FlexLayout();
        imageContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        imageContainer.setJustifyContentMode(JustifyContentMode.START);
        imageContainer.getStyle().set("gap", "15px").set("padding", "10px");
        imageContainer.setWidthFull();

        Scroller scroller = new Scroller(imageContainer);
        scroller.setSizeFull();
        scroller.getElement().executeJs(
            "this.addEventListener('scroll', () => {" +
            "  if (this.scrollTop + this.clientHeight >= this.scrollHeight - 100) {" +
            "    $0.$server.loadNextPage();" +
            "  }" +
            "});", getElement());

        add(searchBar, bannerRow, selectionBar, scroller);
        setSizeFull();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        startSearch();
    }

    private void startSearch() {
        currentQuery = searchField.getValue();
        currentPage = 0;
        hasMore = true;
        selected.clear();
        loadedImages.clear();
        updateSelectionBar();
        imageContainer.removeAll();
        loadNextPage();
    }

    @ClientCallable
    public void loadNextPage() {
        if (loading || !hasMore) return;
        loading = true;
        UI ui = UI.getCurrent();
        String query = currentQuery;
        String folder = folderFilter;
        List<Long> ids = idsFilter;
        int page = currentPage;

        Thread thread = new Thread(() -> {
            if (ids != null) {
                List<ImageFile> images = page == 0 ? service.findByIds(ids) : List.of();
                ui.access(() -> {
                    images.forEach(image -> {
                        loadedImages.add(image);
                        imageContainer.add(createImageCard(image));
                    });
                    hasMore = false;
                    currentPage++;
                    loading = false;
                });
                return;
            }
            Slice<ImageFile> slice = tagFilter != null
                    ? service.findByTag(tagFilter, PageRequest.of(page, PAGE_SIZE))
                    : folder != null
                    ? service.findByFolder(folder, PageRequest.of(page, PAGE_SIZE))
                    : service.findImages(query, PageRequest.of(page, PAGE_SIZE));
            ui.access(() -> {
                slice.getContent().forEach(image -> {
                    loadedImages.add(image);
                    imageContainer.add(createImageCard(image));
                });
                hasMore = slice.hasNext();
                currentPage++;
                loading = false;
            });
        });
        thread.start();
    }

    private Component createImageCard(ImageFile image) {
        // Wrapper mit relativer Position für überlagerte Elemente
        Div card = new Div();
        card.getStyle()
                .set("width", "200px")
                .set("position", "relative")
                .set("border", "1px solid #e2e2e2")
                .set("border-radius", "8px")
                .set("overflow", "hidden")
                .set("cursor", "pointer")
                .set("transition", "box-shadow 0.15s");

        // Checkbox oben links — erscheint beim Hover oder wenn bereits selektiert
        Checkbox selectCb = new Checkbox();
        selectCb.setValue(selected.contains(image));
        selectCb.getStyle()
                .set("position", "absolute")
                .set("top", "6px")
                .set("left", "6px")
                .set("z-index", "2")
                .set("background", "rgba(255,255,255,0.85)")
                .set("border-radius", "4px")
                .set("padding", "2px");
        selectCb.addValueChangeListener(e -> {
            if (e.getValue()) selected.add(image);
            else selected.remove(image);
            updateSelectionBar();
            // Karte hervorheben
            if (e.getValue()) {
                card.getStyle().set("box-shadow", "0 0 0 3px var(--lumo-primary-color)");
            } else {
                card.getStyle().remove("box-shadow");
            }
        });

        // Tag-Button oben rechts
        Button tagBtn = new Button(new Icon(VaadinIcon.TAG));
        tagBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        tagBtn.getStyle()
                .set("position", "absolute")
                .set("top", "4px")
                .set("right", "4px")
                .set("z-index", "2")
                .set("background", "rgba(255,255,255,0.85)")
                .set("border-radius", "4px");
        tagBtn.addClickListener(e -> {
            e.getSource().getUI().ifPresent(ui -> {
                AppUserInfo user = currentUser.require();
                List<consulting.segieth.vfs.domain.FileNode> targets = selected.contains(image) && selected.size() > 1
                        ? new ArrayList<>(selected)
                        : List.of(image);
                new TagDialog(targets, tagService, user, () -> {}).open();
            });
        });

        // Bild
        Image img;
        if (image.hasThumbnail()) {
            StreamResource resource = new StreamResource(image.getName(),
                    () -> new java.io.ByteArrayInputStream(image.getThumbnail()));
            img = new Image(resource, image.getName());
        } else {
            img = new Image("icons/icon.png", image.getName());
        }
        img.setWidth("200px");
        img.setHeight("150px");
        img.getStyle().set("object-fit", "cover").set("display", "block");

        Span label = new Span(image.getName());
        label.getStyle()
                .set("font-size", "0.8em")
                .set("padding", "5px")
                .set("display", "block")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");

        // Klick auf Bild öffnet Dialog (nicht Checkbox oder Tag-Button)
        img.addClickListener(e -> showImageDialog(image, getCurrentImages()));
        label.addClickListener(e -> showImageDialog(image, getCurrentImages()));

        tagBtn.setVisible(canEdit);
        card.add(selectCb, tagBtn, img, label);
        return card;
    }

    /** Private Tags nur für ihren Besitzer — sonst wird der Tag-Filter aus der URL ignoriert */
    private boolean isVisibleTag(Long tagId) {
        String userId = currentUser.get().map(u -> u.getUserId().toString()).orElse(null);
        return tagRepository.findById(tagId)
                .map(t -> t.isPublic() || (userId != null && userId.equals(t.getOwnerUserId())))
                .orElse(false);
    }

    private void updateSelectionBar() {
        selectionBar.setVisible(!selected.isEmpty());
        selectionLabel.setText(selected.size() + " ausgewählt");
    }

    private void clearSelection() {
        selected.clear();
        updateSelectionBar();
        // Alle Checkboxen zurücksetzen — einfachste Lösung: neu laden
        imageContainer.removeAll();
        currentPage = 0;
        hasMore = true;
        loadNextPage();
    }

    private void tagSelected() {
        if (selected.isEmpty()) return;
        AppUserInfo user = currentUser.require();
        List<consulting.segieth.vfs.domain.FileNode> nodes = new ArrayList<>(selected);
        new TagDialog(nodes, tagService, user, () ->
            Notification.show(selected.size() + " Dateien getaggt.",
                    2000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS))
            .open();
    }

    private void moveSelected() {
        if (selected.isEmpty()) return;
        new TreePickerDialog(treeService, targetDir -> {
            int success = 0, failed = 0;
            for (ImageFile img : selected) {
                try {
                    moveService.move(img, targetDir);
                    success++;
                } catch (Exception ex) {
                    failed++;
                    System.err.println("Verschieben fehlgeschlagen: " + ex.getMessage());
                }
            }
            clearSelection();
            String msg = success + " Dateien verschoben nach " + targetDir.getName();
            if (failed > 0) msg += ", " + failed + " fehlgeschlagen";
            Notification n = Notification.show(msg, 3000, Notification.Position.BOTTOM_START);
            n.addThemeVariants(failed > 0 ? NotificationVariant.LUMO_WARNING : NotificationVariant.LUMO_SUCCESS);
        }).open();
    }

    private List<ImageFile> getCurrentImages() {
        return new ArrayList<>(loadedImages);
    }

    private void showImageDialog(ImageFile image, List<ImageFile> images) {
        int idx = images.indexOf(image);
        showImageDialogAt(images, idx < 0 ? 0 : idx);
    }

    private void showImageDialogAt(List<ImageFile> images, int startIndex) {
        // Wrapper damit Lambda den Index ändern kann
        int[] currentIdx = {startIndex};

        Dialog dialog = new Dialog();
        dialog.setWidth("90vw");
        dialog.setHeight("90vh");
        dialog.setDraggable(true);
        dialog.setResizable(true);

        // Bild
        Image fullImg = new Image();
        fullImg.setMaxWidth("100%");
        fullImg.setMaxHeight("calc(90vh - 140px)");
        fullImg.getStyle().set("object-fit", "contain").set("display", "block").set("margin", "auto");

        Span titleSpan = new Span();
        titleSpan.getStyle().set("font-weight", "bold").set("font-size", "1.1em");

        Span counterSpan = new Span();
        counterSpan.getStyle().set("color", "gray").set("font-size", "0.85em");

        Span pathInfo = new Span();
        pathInfo.getStyle().set("font-size", "0.8em").set("color", "gray");

        HorizontalLayout tagRow = new HorizontalLayout();
        tagRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        tagRow.getStyle().set("flex-wrap", "wrap").set("gap", "6px");

        Button closeBtn = new Button(new Icon(VaadinIcon.CLOSE), e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button prevBtn = new Button(new Icon(VaadinIcon.ARROW_LEFT));
        prevBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        prevBtn.addClickShortcut(com.vaadin.flow.component.Key.ARROW_LEFT);

        Button nextBtn = new Button(new Icon(VaadinIcon.ARROW_RIGHT));
        nextBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        nextBtn.addClickShortcut(com.vaadin.flow.component.Key.ARROW_RIGHT);

        HorizontalLayout header = new HorizontalLayout(prevBtn, titleSpan, counterSpan, nextBtn, closeBtn);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.expand(titleSpan);

        Div imgWrapper = new Div(fullImg);
        imgWrapper.setWidthFull();
        imgWrapper.getStyle().set("text-align", "center");

        VerticalLayout content = new VerticalLayout(header, imgWrapper, pathInfo, tagRow);
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(false);
        content.expand(imgWrapper);
        dialog.add(content);

        // Bild laden
        Runnable loadImage = () -> {
            ImageFile current = images.get(currentIdx[0]);
            titleSpan.setText(current.getName());
            counterSpan.setText((currentIdx[0] + 1) + " / " + images.size());
            pathInfo.setText(current.getAddress());
            prevBtn.setEnabled(currentIdx[0] > 0);
            nextBtn.setEnabled(currentIdx[0] < images.size() - 1);

            // Thumbnail sofort
            if (current.hasThumbnail()) {
                StreamResource res = new StreamResource(current.getName(),
                        () -> new ByteArrayInputStream(current.getThumbnail()));
                fullImg.setSrc(res);
            } else {
                fullImg.setSrc("icons/icon.png");
            }

            // Original asynchron
            UI ui = UI.getCurrent();
            Thread t = new Thread(() -> {
                try {
                    java.io.File file = current.getPath().toFile();
                    if (file.exists() && file.canRead()) {
                        StreamResource orig = new StreamResource(current.getName(),
                                () -> { try { return new java.io.FileInputStream(file); }
                                        catch (Exception ex) { return new ByteArrayInputStream(new byte[0]); } });
                        ui.access(() -> fullImg.setSrc(orig));
                    }
                } catch (Exception ignored) {}
            });
            t.start();

            // Tags
            tagRow.removeAll();
            AppUserInfo user = currentUser.get().orElse(null);
            tagService.getVisibleTagsForNode(current, user).forEach(tag -> {
                Span chip = new Span(tag.getFullPath());
                chip.getStyle()
                        .set("background", "var(--lumo-primary-color-10pct)")
                        .set("color", "var(--lumo-primary-text-color)")
                        .set("padding", "2px 8px")
                        .set("border-radius", "12px")
                        .set("font-size", "0.8em");
                tagRow.add(chip);
            });
            ImageFile finalCurrent = current;
            Button editTagsBtn = new Button("🏷 Tags bearbeiten", ev ->
                new TagDialog(List.<consulting.segieth.vfs.domain.FileNode>of(finalCurrent), tagService, user, () -> {}).open());
            editTagsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            if (canEdit) tagRow.add(editTagsBtn);

            // Navigation zu nächster Seite wenn am Ende
            if (currentIdx[0] == images.size() - 1 && hasMore) {
                loadNextPage();
            }
        };

        prevBtn.addClickListener(e -> {
            if (currentIdx[0] > 0) { currentIdx[0]--; loadImage.run(); }
        });
        nextBtn.addClickListener(e -> {
            if (currentIdx[0] < images.size() - 1) { currentIdx[0]++; loadImage.run(); }
        });

        loadImage.run();
        dialog.open();
    }
}
