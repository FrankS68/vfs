package consulting.segieth.vfs.ui.view;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.domain.Tag;
import consulting.segieth.vfs.service.DuplicateService;
import consulting.segieth.vfs.service.FileSystemService;
import consulting.segieth.vfs.service.HashService;
import consulting.segieth.vfs.service.ImageService;
import consulting.segieth.vfs.service.MoveService;
import consulting.segieth.vfs.service.ProcessLogger;
import consulting.segieth.vfs.service.ProcessLogger.ProcessType;
import consulting.segieth.vfs.service.TagService;
import consulting.segieth.vfs.service.TagVFSNode;
import consulting.segieth.vfs.service.TreeService;
import consulting.segieth.vfs.service.TreeStateService;
import consulting.segieth.vfs.service.VFSNode;
import de.witchcafe.auth.AppUserInfo;
import de.witchcafe.auth.CurrentUser;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("")
@PageTitle("FileSystem Tree")
@Menu(order = 1, icon = "vaadin:folder-open", title = "FileSystem Tree")
@PreserveOnRefresh
@AnonymousAllowed // lesen ohne Anmeldung, ändernde Aktionen nur angemeldet
public class FileSystemTreeView extends FileSystemView {

    private final TreeService treeService;
    private final DuplicateService duplicateService;
    private final MoveService moveService;
    private final TagService tagService;
    private final CurrentUser currentUser;
    /** Ohne Anmeldung nur lesen: ändernde Aktionen werden nicht angezeigt */
    private final boolean canEdit;
    private final ProcessLogger processLogger;
    private final HashService hashService;
    private final HorizontalLayout multiActionBar;
    private final Span multiSelectionLabel;
    TreeGrid<VFSNode> fileSystemTree;
    private VFSNode selectedNode = null; // beliebiger selektierter Knoten für Scan

    // Virtueller Tag-Root — einmalig erzeugt als Sentinel-Node
    private static final VFSNode TAG_ROOT = new VFSNode(null) {
        @Override public String getName() { return "\uD83C\uDFF7 Tags"; }
        @Override public String getInfo() { return ""; }
        @Override public FileNode getFileNode() { return null; }
        @Override public boolean equals(Object o) { return this == o; }
        @Override public int hashCode() { return System.identityHashCode(this); }
    };

    public FileSystemTreeView(FileSystemService fsService, TreeService treeService,
                               DuplicateService duplicateService, MoveService moveService,
                               TagService tagService, CurrentUser currentUser,
                               ProcessLogger processLogger,
                               ImageService imageService, HashService hashService) {
        super(fsService);
        this.treeService = treeService;
        this.duplicateService = duplicateService;
        this.moveService = moveService;
        this.tagService = tagService;
        this.currentUser = currentUser;
        this.canEdit = currentUser.get().isPresent();
        this.processLogger = processLogger;
        this.hashService = hashService;

        Button refreshBtn = new Button("Tree neu laden", new Icon(VaadinIcon.REFRESH));
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshBtn.getElement().setAttribute("title", "Tree aus der Datenbank neu laden (kein Dateisystem-Zugriff)");
        refreshBtn.addClickListener(e -> refreshAll());

        Button scanBtn = new Button("Verzeichnis scannen", new Icon(VaadinIcon.SEARCH));
        scanBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        scanBtn.getElement().setAttribute("title", "Ausgewähltes Verzeichnis vom Dateisystem einlesen und DB aktualisieren");
        scanBtn.addClickListener(e -> scanSelected());

        Button newFsBtn = new Button(new Icon(VaadinIcon.PLUS));
        newFsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        newFsBtn.getElement().setAttribute("title", "Neues Dateisystem anlegen");
        newFsBtn.addClickListener(e ->
            new FileSystemDialog(fsService, null, () -> {
                fileSystemTree.deselectAll();
                fileSystemTree.getDataProvider().refreshAll();
            }).open());

        Button editFsBtn = new Button(new Icon(VaadinIcon.EDIT));
        editFsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        editFsBtn.getElement().setAttribute("title", "Ausgewähltes Dateisystem bearbeiten");
        editFsBtn.addClickListener(e -> {
            if (selectedFileSystem != null && "system".equals(selectedFileSystem.getType())) {
                new FileSystemDialog(fsService, selectedFileSystem, () -> {
                    fileSystemTree.deselectAll();
                    selectedFileSystem = null;
                    selectedNode = null;
                    fileSystemTree.getDataProvider().refreshAll();
                }).open();
            } else {
                Notification.show("Bitte ein Root-Dateisystem auswählen", 2000, Notification.Position.BOTTOM_START);
            }
        });

        Button deleteFsBtn = new Button(new Icon(VaadinIcon.TRASH));
        deleteFsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        deleteFsBtn.getElement().setAttribute("title", "Ausgewähltes Dateisystem löschen");
        deleteFsBtn.addClickListener(e -> {
            if (selectedFileSystem != null && "system".equals(selectedFileSystem.getType())) {
                fsService.delete(selectedFileSystem);
                fileSystemTree.getDataProvider().refreshAll();
                Notification.show("Dateisystem gelöscht", 2000, Notification.Position.BOTTOM_START);
            } else {
                Notification.show("Bitte ein Root-Dateisystem auswählen", 2000, Notification.Position.BOTTOM_START);
            }
        });

        // Thumbnails + Hashes
        Button thumbnailBtn = new Button(new Icon(VaadinIcon.PICTURE));
        thumbnailBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        thumbnailBtn.getElement().setAttribute("title", "Thumbnails generieren");
        thumbnailBtn.addClickListener(e -> {
            thumbnailBtn.setEnabled(false);
            UI ui = UI.getCurrent();
            Notification.show("Thumbnails werden generiert...", 2000, Notification.Position.TOP_END);
            Thread thread = new Thread(() -> {
                imageService.generateMissingThumbnails((done, total) -> {});
                ui.access(() -> {
                    thumbnailBtn.setEnabled(true);
                    Notification.show("Thumbnails fertig!", 3000, Notification.Position.TOP_END)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
            });
            thread.start();
        });

        Button hashBtn = new Button(new Icon(VaadinIcon.HASH));
        hashBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        hashBtn.getElement().setAttribute("title", "Hashes berechnen (für Duplikat-Erkennung)");
        hashBtn.addClickListener(e -> {
            hashBtn.setEnabled(false);
            UI ui = UI.getCurrent();
            Notification.show("Hashes werden berechnet...", 2000, Notification.Position.TOP_END);
            Thread thread = new Thread(() -> {
                hashService.computeMissingHashes((done, total) -> {});
                ui.access(() -> {
                    hashBtn.setEnabled(true);
                    treeService.invalidateDuplicateCache();
                    fileSystemTree.getDataProvider().refreshAll();
                    Notification.show("Hashes fertig!", 3000, Notification.Position.TOP_END)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
            });
            thread.start();
        });

        HorizontalLayout toolbar = new HorizontalLayout(refreshBtn);
        if (canEdit) {
            toolbar.add(scanBtn,
                    new com.vaadin.flow.component.html.Span(" | "),
                    newFsBtn, editFsBtn, deleteFsBtn,
                    new com.vaadin.flow.component.html.Span(" | "),
                    thumbnailBtn, hashBtn);
        }
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        toolbar.setPadding(false);

        // Multi-Aktionsleiste
        multiSelectionLabel = new Span();
        multiSelectionLabel.getStyle().set("font-weight", "bold");

        Button tagMultiBtn = new Button("🏷 Taggen", e -> {
            Set<VFSNode> sel = fileSystemTree.getSelectedItems();
            if (sel.isEmpty()) return;
            AppUserInfo user = currentUser.require();
            List<FileNode> nodes = new ArrayList<>();
            for (VFSNode n : sel) {
                if (n instanceof TagNode tn) {
                    nodes.addAll(tagService.getFilesForTag(tn.getTag()));
                } else if (n.getFileNode() != null) {
                    nodes.add(n.getFileNode());
                }
            }
            if (!nodes.isEmpty()) new TagDialog(nodes, tagService, user, () -> {}).open();
        });
        tagMultiBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        Button moveMultiBtn = new Button("📁 Verschieben", e -> {
            Set<VFSNode> sel = fileSystemTree.getSelectedItems();
            if (sel.isEmpty()) return;
            new TreePickerDialog(treeService, targetDir -> {
                int ok = 0, fail = 0;
                for (VFSNode n : sel) {
                    if (n.getFileNode() == null) continue;
                    try { moveService.move(n.getFileNode(), targetDir); ok++; }
                    catch (Exception ex) { fail++; }
                }
                fileSystemTree.getDataProvider().refreshAll();
                String msg = ok + " verschoben" + (fail > 0 ? ", " + fail + " fehlgeschlagen" : "");
                Notification.show(msg, 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(fail > 0 ? NotificationVariant.LUMO_WARNING : NotificationVariant.LUMO_SUCCESS);
            }).open();
        });
        moveMultiBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        tagMultiBtn.setVisible(canEdit);
        moveMultiBtn.setVisible(canEdit);

        Button clearMultiBtn = new Button("✕", e -> fileSystemTree.deselectAll());
        clearMultiBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        multiActionBar = new HorizontalLayout(multiSelectionLabel, tagMultiBtn, moveMultiBtn, clearMultiBtn);
        multiActionBar.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        multiActionBar.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("padding", "6px 12px")
                .set("border-radius", "6px");
        multiActionBar.setVisible(false);

        fileSystemTree = new TreeGrid<>();
        fileSystemTree.setDataProvider(new AbstractBackEndHierarchicalDataProvider<VFSNode, Void>() {

            @Override
            public int getChildCount(HierarchicalQuery<VFSNode, Void> query) {
                VFSNode parent = query.getParentOptional().orElse(null);
                if (parent == null) return treeService.getRoots().size() + 1; // +1 für TAG_ROOT
                if (parent == TAG_ROOT) return tagService.getRootTags(currentUser.get().orElse(null)).size();
                if (parent instanceof TagNode tn) return tagService.getChildren(tn.getTag(), currentUser.get().orElse(null)).size() + tagService.getFilesForTag(tn.getTag()).size();
                return (int) treeService.getChildren(parent).stream()
                        .skip(query.getOffset()).limit(query.getLimit()).count();
            }

            @Override
            public boolean hasChildren(VFSNode node) {
                if (node == TAG_ROOT) return true;
                if (node instanceof TagNode tn) {
                    return tagService.hasChildren(tn.getTag(), currentUser.get().orElse(null));
                }
                if (node.getFileNode() == null) return false;
                return treeService.hasChildren(node);
            }

            @Override
            protected Stream<VFSNode> fetchChildrenFromBackEnd(HierarchicalQuery<VFSNode, Void> query) {
                VFSNode parent = query.getParentOptional().orElse(null);

                if (parent == null) {
                    // Root: FileSystems + TAG_ROOT
                    List<VFSNode> roots = new ArrayList<>(treeService.getRoots());
                    roots.add(TAG_ROOT);
                    return roots.stream().skip(query.getOffset()).limit(query.getLimit());
                }

                if (parent == TAG_ROOT) {
                    // Kinder von TAG_ROOT: Root-Tags als TagNode (ohne Anmeldung nur öffentliche)
                    AppUserInfo user = currentUser.get().orElse(null);
                    return tagService.getRootTags(user).stream()
                            .map(t -> (VFSNode) new TagNode(t))
                            .skip(query.getOffset()).limit(query.getLimit());
                }

                if (parent instanceof TagNode tn) {
                    // Kinder eines Tags: Sub-Tags + getaggte Dateien
                    AppUserInfo user = currentUser.get().orElse(null);
                    List<VFSNode> children = new ArrayList<>();
                    tagService.getChildren(tn.getTag(), user).stream()
                            .map(t -> (VFSNode) new TagNode(t)).forEach(children::add);
                    tagService.getFilesForTag(tn.getTag()).stream()
                            .map(fn -> (VFSNode) new VFSNode<>(fn)).forEach(children::add);
                    return children.stream().skip(query.getOffset()).limit(query.getLimit());
                }

                return treeService.getChildren(parent).stream()
                        .skip(query.getOffset()).limit(query.getLimit());
            }
        });

        // Name-Spalte
        fileSystemTree.addHierarchyColumn(VFSNode::getName).setHeader("Name").setFlexGrow(3);
        fileSystemTree.addColumn(node -> {
            if (node == TAG_ROOT || node instanceof TagNode) return "tag";
            if (node.getFileNode() == null) return "";
            return node.getFileNode().getType();
        }).setHeader("Typ").setFlexGrow(1);
        fileSystemTree.addColumn(VFSNode::getInfo).setHeader("Info").setFlexGrow(2);

        // Duplikat-Spalte
        fileSystemTree.addComponentColumn(node -> {
            if (node == TAG_ROOT || node instanceof TagNode) return new Span();
            if (node.getFileNode() == null) return new Span();
            if (!node.isHasDuplicate()) return new Span();
            Icon icon = VaadinIcon.COPY.create();
            icon.setColor("var(--lumo-error-color)");
            icon.setSize("1em");
            icon.getStyle().set("cursor", "pointer");
            icon.getElement().setAttribute("title", "Duplikat — klicken für Details");
            icon.addClickListener(e -> {
                if (node.getFileNode().getContentHash() != null) {
                    showDuplicateDialog(node.getFileNode());
                } else {
                    showDuplicatesForFolder(node.getFileNode().getSystem(), node.getFileNode().getAddress());
                }
            });
            return icon;
        }).setHeader("").setWidth("50px").setFlexGrow(0);

        // Aktionsspalte
        fileSystemTree.addComponentColumn(node -> {
            HorizontalLayout actions = new HorizontalLayout();
            actions.setPadding(false);
            actions.setSpacing(false);

            if (node == TAG_ROOT) return actions;

            // Tag-Manager Aktionen für TagNode
            if (node instanceof TagNode tn) {
                // Gallery für getaggte Bilder
                Button galleryBtn = new Button(VaadinIcon.PICTURE.create());
                galleryBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                galleryBtn.getElement().setAttribute("title", "Gallery für diesen Tag");
                galleryBtn.addClickListener(e ->
                    e.getSource().getUI().ifPresent(ui ->
                        ui.navigate("gallery", com.vaadin.flow.router.QueryParameters.of(
                                "tag", String.valueOf(tn.getTag().getId())))));
                actions.add(galleryBtn);

                // Triage für getaggte Bilder
                Button triageBtn = new Button(VaadinIcon.FLAG.create());
                triageBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                triageBtn.getElement().setAttribute("title", "Triage für diesen Tag");
                triageBtn.addClickListener(e ->
                    e.getSource().getUI().ifPresent(ui ->
                        ui.navigate("triage", com.vaadin.flow.router.QueryParameters.of(
                                "tag", String.valueOf(tn.getTag().getId())))));
                if (canEdit) actions.add(triageBtn);

                // Duplikate unter diesem Tag
                Button dupBtn = new Button(VaadinIcon.COPY.create());
                dupBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                dupBtn.getElement().setAttribute("title", "Duplikate unter diesem Tag");
                dupBtn.addClickListener(e -> showDuplicatesForTag(tn));
                actions.add(dupBtn);
                if (!canEdit) return actions;

                Button newSubTagBtn = new Button(VaadinIcon.PLUS.create());
                newSubTagBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                newSubTagBtn.getElement().setAttribute("title", "Unter-Tag erstellen");
                newSubTagBtn.addClickListener(e -> {
                    AppUserInfo user = currentUser.require();
                    com.vaadin.flow.component.textfield.TextField nameField =
                            new com.vaadin.flow.component.textfield.TextField("Name des neuen Tags");
                    com.vaadin.flow.component.checkbox.Checkbox publicCb =
                            new com.vaadin.flow.component.checkbox.Checkbox("Öffentlich");
                    Dialog d = new Dialog();
                    Button ok = new Button("Erstellen", ev -> {
                        tagService.createTag(nameField.getValue().trim(), tn.getTag(), user, publicCb.getValue());
                        d.close();
                        fileSystemTree.getDataProvider().refreshItem(node, true);
                    });
                    ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                    d.add(new VerticalLayout(nameField, publicCb, ok));
                    d.open();
                });
                actions.add(newSubTagBtn);

                // Öffentlich/Privat Toggle
                boolean isPublic = tn.getTag().isPublic();
                Button visibilityBtn = new Button(isPublic ? VaadinIcon.GLOBE.create() : VaadinIcon.LOCK.create());
                visibilityBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                visibilityBtn.getElement().setAttribute("title", isPublic ? "Auf privat setzen" : "Veröffentlichen");
                visibilityBtn.addClickListener(e -> {
                    tagService.setPublic(tn.getTag(), !tn.getTag().isPublic());
                    fileSystemTree.getDataProvider().refreshAll();
                });
                actions.add(visibilityBtn);

                Button deleteTagBtn = new Button(VaadinIcon.TRASH.create());
                deleteTagBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                deleteTagBtn.getElement().setAttribute("title", "Tag löschen");
                deleteTagBtn.addClickListener(e -> {
                    tagService.deleteTag(tn.getTag());
                    fileSystemTree.getDataProvider().refreshAll();
                });
                actions.add(deleteTagBtn);
                return actions;
            }

            if (node.getFileNode() == null) return actions;

            String type = node.getFileNode().getType();
            boolean isDir = type.equals("directory") || type.equals("system");

            if (node.isHasImages() && isDir) {
                Button galleryBtn = new Button(VaadinIcon.PICTURE.create());
                galleryBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                galleryBtn.getElement().setAttribute("title", "Gallery für diesen Ordner");
                galleryBtn.addClickListener(e ->
                    GalleryView.openForFolder(e.getSource().getUI().orElseThrow(), node.getFileNode().getAddress()));
                actions.add(galleryBtn);

                Button triageBtn = new Button(VaadinIcon.FLAG.create());
                triageBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                triageBtn.getElement().setAttribute("title", "Triage für diesen Ordner");
                triageBtn.addClickListener(e ->
                    TriageView.openForFolder(e.getSource().getUI().orElseThrow(), node.getFileNode().getAddress()));
                if (canEdit) actions.add(triageBtn);
            }

            if (canEdit && !type.equals("system")) {
                Button tagBtn = new Button(VaadinIcon.TAG.create());
                tagBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                tagBtn.getElement().setAttribute("title", "Tags bearbeiten");
                tagBtn.addClickListener(e -> {
                    AppUserInfo user = currentUser.require();
                    new TagDialog(List.of(node.getFileNode()), tagService, user, () -> {}).open();
                });
                actions.add(tagBtn);
            }

            return actions;
        }).setHeader("Aktionen").setWidth("120px").setFlexGrow(0);

        fileSystemTree.setSizeFull();

        // Aufklapp-State speichern
        fileSystemTree.addExpandListener(event -> event.getItems().forEach(node -> {
            if (node == TAG_ROOT) TreeStateService.expand("__TAG_ROOT__");
            else if (node instanceof TagNode tn) TreeStateService.expand("__TAG__" + tn.getTag().getId());
            else if (node.getFileNode() != null) TreeStateService.expand(node.getFileNode().getAddress());
        }));
        fileSystemTree.addCollapseListener(event -> event.getItems().forEach(node -> {
            if (node == TAG_ROOT) TreeStateService.collapse("__TAG_ROOT__");
            else if (node instanceof TagNode tn) TreeStateService.collapse("__TAG__" + tn.getTag().getId());
            else if (node.getFileNode() != null) TreeStateService.collapse(node.getFileNode().getAddress());
        }));

        fileSystemTree.setSelectionMode(SelectionMode.MULTI);
        fileSystemTree.addSelectionListener(event -> {
            Set<VFSNode> sel = event.getAllSelectedItems();
            multiActionBar.setVisible(!sel.isEmpty());
            multiSelectionLabel.setText(sel.size() + " ausgewählt");
            if (sel.size() == 1) {
                sel.stream().findFirst().ifPresent(node -> {
                    selectedNode = node;
                    if (node.getFileNode() instanceof consulting.segieth.vfs.domain.FileSystem fs
                            && "system".equals(fs.getType())) {
                        selectedFileSystem = fs;
                    } else {
                        selectedFileSystem = null;
                    }
                });
            } else {
                selectedNode = null;
            }
        });

        // Kontext-Menü
        GridContextMenu<VFSNode> contextMenu = fileSystemTree.addContextMenu();
        contextMenu.addItem("Alle Kinder auswählen", event ->
            event.getItem().ifPresent(node -> {
                List<VFSNode> children;
                if (node instanceof TagNode tn) {
                    children = tagService.getFilesForTag(tn.getTag()).stream()
                            .map(fn -> (VFSNode) new VFSNode<>(fn))
                            .collect(java.util.stream.Collectors.toList());
                } else if (node.getFileNode() != null) {
                    children = treeService.getChildren(node);
                } else return;
                children.forEach(child -> fileSystemTree.select(child));
                Notification.show(children.size() + " Einträge ausgewählt",
                        2000, Notification.Position.BOTTOM_START);
            }));

        contextMenu.addItem("Alle Kinder abwählen", event ->
            event.getItem().ifPresent(node -> {
                List<VFSNode> children;
                if (node instanceof TagNode tn) {
                    children = tagService.getFilesForTag(tn.getTag()).stream()
                            .map(fn -> (VFSNode) new VFSNode<>(fn))
                            .collect(java.util.stream.Collectors.toList());
                } else if (node.getFileNode() != null) {
                    children = treeService.getChildren(node);
                } else return;
                children.forEach(child -> fileSystemTree.deselect(child));
                Notification.show(children.size() + " Einträge abgewählt",
                        2000, Notification.Position.BOTTOM_START);
            }));

        var scanItem = contextMenu.addItem("Vom Dateisystem scannen", event -> event.getItem().ifPresent(node -> {
            if (node == TAG_ROOT || node instanceof TagNode || node.getFileNode() == null) return;
            if (!(node.getFileNode() instanceof ImageFile)) {
                UI ui = UI.getCurrent();
                String loc = node.getFileNode().getAddress();
                ProcessLogger.ProcessHandle handle = processLogger.start(ui, ProcessType.SCAN, loc);
                Thread thread = new Thread(() -> {
                    try {
                        fsService.scanSync(node.getFileNode());
                        ui.access(() -> {
                            fileSystemTree.getDataProvider().refreshItem(node, true);
                            handle.success("Scan abgeschlossen");
                        });
                    } catch (NoSuchFileException e) {
                        handle.error("Verzeichnis nicht gefunden");
                    } catch (Exception e) {
                        handle.error(e.getMessage());
                    }
                });
                thread.start();
            }
        }));

        contextMenu.addItem("Duplikate anzeigen", event ->
            event.getItem().filter(n -> n != TAG_ROOT && !(n instanceof TagNode) && n.isHasDuplicate())
                 .ifPresent(n -> {
                     if (n.getFileNode().getContentHash() != null) {
                         showDuplicateDialog(n.getFileNode());
                     } else {
                         showDuplicatesForFolder(n.getFileNode().getSystem(), n.getFileNode().getAddress());
                     }
                 }));

        var tagItem = contextMenu.addItem("Tags bearbeiten", event ->
            event.getItem().filter(n -> n != TAG_ROOT && !(n instanceof TagNode) && n.getFileNode() != null)
                 .ifPresent(n -> {
                    AppUserInfo user = currentUser.require();
                    new TagDialog(List.of(n.getFileNode()), tagService, user, () -> {}).open();
                }));

        var moveItem = contextMenu.addItem("Verschieben nach...", event ->
            event.getItem().filter(n -> n != TAG_ROOT && !(n instanceof TagNode) && n.getFileNode() != null)
                 .ifPresent(n -> {
                TreePickerDialog picker = new TreePickerDialog(treeService, targetDir -> {
                    UI ui = UI.getCurrent();
                    ProcessLogger.ProcessHandle handle = processLogger.start(ui, ProcessType.MOVE,
                            n.getFileNode().getAddress() + " → " + targetDir.getAddress());
                    Thread thread = new Thread(() -> {
                        try {
                            moveService.move(n.getFileNode(), targetDir);
                            treeService.invalidateDuplicateCache();
                            ui.access(() -> {
                                fileSystemTree.getDataProvider().refreshAll();
                                handle.success(n.getName() + " verschoben");
                            });
                        } catch (Exception ex) {
                            handle.error(ex.getMessage());
                        }
                    });
                    thread.start();
                });
                picker.open();
            }));

        scanItem.setVisible(canEdit);
        tagItem.setVisible(canEdit);
        moveItem.setVisible(canEdit);

        setSizeFull();
        addClassNames(LumoUtility.BoxSizing.BORDER, LumoUtility.Display.FLEX,
                LumoUtility.FlexDirection.COLUMN, LumoUtility.Padding.MEDIUM, LumoUtility.Gap.SMALL);
        add(toolbar, multiActionBar, fileSystemTree);
    }

    /** VFSNode-Subtyp für einen Tag im Tree */
    static class TagNode extends VFSNode<FileNode> {
        private final Tag tag;
        TagNode(Tag tag) {
            super(null);
            this.tag = tag;
        }
        public Tag getTag() { return tag; }
        @Override public String getName() { return (tag.isPublic() ? "🌐 " : "🔒 ") + tag.getName(); }
        @Override public String getInfo() { return tag.getFullPath(); }
        @Override public FileNode getFileNode() { return null; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TagNode other)) return false;
            return tag.getId() != null && tag.getId().equals(other.tag.getId());
        }

        @Override
        public int hashCode() {
            return tag.getId() != null ? tag.getId().hashCode() : System.identityHashCode(this);
        }
    }

    /** Zeigt eine Bestätigung mit erklärendem Text an, führt die Aktion nur bei Zustimmung aus. */
    private void confirmAndRun(String message, Runnable action) {
        Dialog confirm = new Dialog();
        confirm.setCloseOnEsc(true);
        confirm.setCloseOnOutsideClick(true);

        Span msgSpan = new Span(message);
        msgSpan.getStyle().set("max-width", "400px").set("display", "block");

        Button yesBtn = new Button("Ja, ausführen", e -> {
            confirm.close();
            action.run();
        });
        yesBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        Button noBtn = new Button("Abbrechen", e -> confirm.close());
        noBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout btnRow = new HorizontalLayout(noBtn, yesBtn);
        btnRow.setJustifyContentMode(JustifyContentMode.END);
        btnRow.setWidthFull();

        VerticalLayout layout = new VerticalLayout(msgSpan, btnRow);
        layout.setPadding(false);
        confirm.add(layout);
        confirm.open();
    }

    /** Zeigt einen blockierenden Hinweis, dass erst alle Dateien einen Hash brauchen, bevor
     * verglichen/gemerged/getrashed werden kann — mit direkter Möglichkeit die Hashes zu berechnen. */
    private void showHashRequiredNotice(long missingCount, String where) {
        Dialog notice = new Dialog();
        notice.setWidth("440px");

        Span msg = new Span(missingCount + " Datei(en) in " + where + " haben noch keinen berechneten Hash. "
                + "Ordner-Vergleich, Merge und Trash sind erst möglich, wenn wirklich alle Dateien einen Hash "
                + "haben — sonst kann nicht zuverlässig erkannt werden, welche Dateien tatsächlich identisch sind.");
        msg.getStyle().set("display", "block");

        Button computeBtn = new Button("Hashes jetzt berechnen", VaadinIcon.HASH.create());
        computeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        computeBtn.addClickListener(e -> {
            notice.close();
            runHashComputation();
        });
        computeBtn.setVisible(canEdit);
        Button closeBtn = new Button("Schließen", e -> notice.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout btnRow = new HorizontalLayout(closeBtn, computeBtn);
        btnRow.setJustifyContentMode(JustifyContentMode.END);
        btnRow.setWidthFull();

        VerticalLayout layout = new VerticalLayout(msg, btnRow);
        layout.setPadding(false);
        notice.add(layout);
        notice.open();
    }

    /** Berechnet fehlende Hashes im Hintergrund (systemweit) und aktualisiert den Tree danach. */
    private void runHashComputation() {
        UI ui = UI.getCurrent();
        Notification.show("Hashes werden berechnet...", 2000, Notification.Position.TOP_END);
        Thread thread = new Thread(() -> {
            hashService.computeMissingHashes((done, total) -> {});
            ui.access(() -> {
                treeService.invalidateDuplicateCache();
                fileSystemTree.getDataProvider().refreshAll();
                Notification.show("Hashes fertig!", 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
        });
        thread.start();
    }

    private void showDuplicatesForFolder(String system, String folderAddress) {
        String prefix = folderAddress.endsWith("\\") || folderAddress.endsWith("/")
                ? folderAddress : folderAddress + "\\";

        long missingHere = duplicateService.countMissingHashes(prefix);
        if (missingHere > 0) {
            showHashRequiredNotice(missingHere, "diesem Ordner");
            return;
        }

        Map<String, List<FileNode>> allGroups = duplicateService.findDuplicateGroups();
        Map<String, List<FileNode>> relevantGroups = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<FileNode>> entry : allGroups.entrySet()) {
            boolean hasFileInFolder = entry.getValue().stream()
                    .anyMatch(fn -> fn.getAddress().startsWith(prefix));
            if (hasFileInFolder) relevantGroups.put(entry.getKey(), entry.getValue());
        }

        if (relevantGroups.isEmpty()) {
            Notification.show("Keine Duplikate unter diesem Ordner",
                    3000, Notification.Position.TOP_CENTER);
            return;
        }

        // Erkenne ob alle Duplikate aus einem anderen Verzeichnis stammen
        // (= ganzes Verzeichnis wurde kopiert)
        java.util.Map<String, Long> otherDirCount = new java.util.LinkedHashMap<>();
        for (List<FileNode> group : relevantGroups.values()) {
            for (FileNode fn : group) {
                if (!fn.getAddress().startsWith(prefix)) {
                    // Elternverzeichnis ermitteln
                    String parent = fn.getAddress().contains("\\")
                            ? fn.getAddress().substring(0, fn.getAddress().lastIndexOf("\\") + 1)
                            : fn.getAddress().substring(0, fn.getAddress().lastIndexOf("/") + 1);
                    otherDirCount.merge(parent, 1L, Long::sum);
                }
            }
        }

        Dialog dialog = new Dialog();
        dialog.setWidth("750px");
        dialog.setMaxHeight("80vh");
        dialog.setDraggable(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        Span title = new Span("Duplikate unter '" + folderAddress + "' — " + relevantGroups.size() + " Gruppen");
        title.getStyle().set("font-weight", "bold").set("font-size", "1.1em");
        Button closeBtn = new Button(new Icon(VaadinIcon.CLOSE), e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        header.add(title, closeBtn);

        VerticalLayout content = new VerticalLayout(header);
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

        // Zeige Verzeichnis-Duplikat-Hinweis wenn erkannt
        if (!otherDirCount.isEmpty()) {
            VerticalLayout hint = new VerticalLayout();
            hint.getStyle()
                    .set("background", "var(--lumo-primary-color-10pct)")
                    .set("border-radius", "8px")
                    .set("padding", "12px");
            hint.setSpacing(true);
            Span hintTitle = new Span("⚠️ Ganzer Ordner scheint dupliziert zu sein");
            hintTitle.getStyle().set("font-weight", "bold");
            Span hintExplain = new Span(
                    "Ein Teil der Dateien in diesem Ordner existiert unverändert in einem anderen Ordner. " +
                    "Vermutlich wurde der Ordner irgendwann kopiert. Du kannst die beiden Ordner zusammenführen: " +
                    "der eine Ordner wird geleert (Dateien werden nach drüben verschoben bzw. als Duplikat gelöscht), " +
                    "der andere bleibt als vollständige Kopie übrig.");
            hintExplain.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");
            VerticalLayout titleBlock = new VerticalLayout(hintTitle, hintExplain);
            titleBlock.setPadding(false);
            titleBlock.setSpacing(false);
            hint.add(titleBlock);

            otherDirCount.entrySet().stream()
                    .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> {
                String otherDir = e.getKey();
                long count = e.getValue();

                VerticalLayout card = new VerticalLayout();
                card.getStyle()
                        .set("background", "var(--lumo-base-color)")
                        .set("border", "1px solid var(--lumo-contrast-20pct)")
                        .set("border-radius", "6px")
                        .set("padding", "10px");
                card.setPadding(false);
                card.setSpacing(false);

                Span matchLbl = new Span("Anderer Ordner:");
                matchLbl.getStyle().set("font-size", "0.85em");
                Span otherDirLbl = new Span(otherDir);
                otherDirLbl.getStyle().set("font-size", "0.85em").set("font-weight", "bold")
                        .set("word-break", "break-all");
                card.add(matchLbl, otherDirLbl);

                long missingOther = duplicateService.countMissingHashes(otherDir);
                if (missingOther > 0) {
                    Span blockMsg = new Span(missingOther + " Datei(en) im anderen Ordner haben noch keinen "
                            + "berechneten Hash. Vergleich, Merge und Trash sind hier erst möglich, wenn alle "
                            + "Dateien einen Hash haben.");
                    blockMsg.getStyle().set("font-size", "0.85em")
                            .set("color", "var(--lumo-error-text-color)")
                            .set("display", "block")
                            .set("margin-top", "8px");
                    card.add(blockMsg);

                    Button computeBtn = new Button("Hashes jetzt berechnen", VaadinIcon.HASH.create());
                    computeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                    computeBtn.setWidthFull();
                    computeBtn.getStyle().set("margin-top", "6px");
                    computeBtn.addClickListener(ev -> {
                        dialog.close();
                        runHashComputation();
                    });
                    if (canEdit) card.add(computeBtn);

                    hint.add(card);
                    return;
                }

                int[] intoOther = duplicateService.previewMerge(prefix, otherDir);
                int[] fromOther = duplicateService.previewMerge(otherDir, prefix);
                long hereTotal = intoOther[0] + intoOther[1];
                long otherTotal = fromOther[0] + fromOther[1];
                Span breakdown = new Span(
                        "Hier: " + hereTotal + " Dateien (davon " + intoOther[1] + " auch dort vorhanden). "
                        + "Dort: " + otherTotal + " Dateien (davon " + fromOther[1] + " auch hier vorhanden, "
                        + fromOther[0] + " nur dort).");
                breakdown.getStyle().set("font-size", "0.8em")
                        .set("color", "var(--lumo-secondary-text-color)")
                        .set("display", "block")
                        .set("margin-top", "4px");
                card.add(breakdown);

                List<Long> onlyHereImageIds = duplicateService.findUnique(prefix, otherDir).stream()
                        .filter(fn -> fn instanceof ImageFile)
                        .map(FileNode::getId)
                        .collect(java.util.stream.Collectors.toList());
                List<Long> onlyThereImageIds = duplicateService.findUnique(otherDir, prefix).stream()
                        .filter(fn -> fn instanceof ImageFile)
                        .map(FileNode::getId)
                        .collect(java.util.stream.Collectors.toList());

                if (!onlyHereImageIds.isEmpty() || !onlyThereImageIds.isEmpty()) {
                    HorizontalLayout galleryRow = new HorizontalLayout();
                    galleryRow.setWidthFull();
                    galleryRow.getStyle().set("margin-top", "6px").set("gap", "4px");
                    if (!onlyHereImageIds.isEmpty()) {
                        Button onlyHereBtn = new Button("🖼 Nur hier ansehen (" + onlyHereImageIds.size() + ")",
                                ev -> GalleryView.openForIds(UI.getCurrent(), onlyHereImageIds, "Nur hier: " + prefix));
                        onlyHereBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        galleryRow.add(onlyHereBtn);
                        galleryRow.expand(onlyHereBtn);
                    }
                    if (!onlyThereImageIds.isEmpty()) {
                        Button onlyThereBtn = new Button("🖼 Nur dort ansehen (" + onlyThereImageIds.size() + ")",
                                ev -> GalleryView.openForIds(UI.getCurrent(), onlyThereImageIds, "Nur dort: " + otherDir));
                        onlyThereBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        galleryRow.add(onlyThereBtn);
                        galleryRow.expand(onlyThereBtn);
                    }
                    card.add(galleryRow);
                }

                Button mergeIntoOtherBtn = new Button();
                mergeIntoOtherBtn.setIcon(VaadinIcon.ARROW_FORWARD.create());
                mergeIntoOtherBtn.setText("Diesen Ordner leeren — " + intoOther[0] + " Dateien nach dort verschieben, "
                        + intoOther[1] + " hier löschen");
                mergeIntoOtherBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                mergeIntoOtherBtn.setWidthFull();
                mergeIntoOtherBtn.addClickListener(ev -> confirmAndRun(
                        intoOther[0] + " Dateien werden nach '" + otherDir + "' verschoben, "
                                + intoOther[1] + " Dateien aus diesem Ordner werden gelöscht (Trash), da sie dort "
                                + "per Hash bereits vorhanden sind. Fortfahren?",
                        () -> {
                            int[] result = duplicateService.mergeIntoFolder(prefix, otherDir);
                            treeService.invalidateDuplicateCache();
                            fileSystemTree.getDataProvider().refreshAll();
                            dialog.close();
                            String msg = result[0] + " verschoben, " + result[1] + " getrashed";
                            if (result[2] > 0) msg += ", " + result[2] + " fehlgeschlagen";
                            Notification.show(msg, 4000, Notification.Position.TOP_CENTER)
                                    .addThemeVariants(result[2] > 0
                                            ? NotificationVariant.LUMO_WARNING
                                            : NotificationVariant.LUMO_SUCCESS);
                        }));

                Button mergeFromOtherBtn = new Button();
                mergeFromOtherBtn.setIcon(VaadinIcon.ARROW_BACKWARD.create());
                mergeFromOtherBtn.setText("Anderen Ordner leeren — " + fromOther[0] + " Dateien hierher verschieben, "
                        + fromOther[1] + " dort löschen");
                mergeFromOtherBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                mergeFromOtherBtn.setWidthFull();
                mergeFromOtherBtn.addClickListener(ev -> confirmAndRun(
                        fromOther[0] + " Dateien werden aus '" + otherDir + "' hierher verschoben, "
                                + fromOther[1] + " Dateien dort werden gelöscht (Trash), da sie hier "
                                + "per Hash bereits vorhanden sind. Fortfahren?",
                        () -> {
                            int[] result = duplicateService.mergeIntoFolder(otherDir, prefix);
                            treeService.invalidateDuplicateCache();
                            fileSystemTree.getDataProvider().refreshAll();
                            dialog.close();
                            String msg = result[0] + " verschoben, " + result[1] + " getrashed";
                            if (result[2] > 0) msg += ", " + result[2] + " fehlgeschlagen";
                            Notification.show(msg, 4000, Notification.Position.TOP_CENTER)
                                    .addThemeVariants(result[2] > 0
                                            ? NotificationVariant.LUMO_WARNING
                                            : NotificationVariant.LUMO_SUCCESS);
                        }));

                VerticalLayout mergeButtons = new VerticalLayout(mergeIntoOtherBtn, mergeFromOtherBtn);
                mergeButtons.setPadding(false);
                mergeButtons.setSpacing(false);
                mergeButtons.getStyle().set("gap", "4px").set("margin-top", "8px");
                if (canEdit) card.add(mergeButtons);

                Button trashDirBtn = new Button("Alle " + count + " Dateien aus diesem Ordner löschen (Trash)",
                        VaadinIcon.TRASH.create());
                trashDirBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                trashDirBtn.setWidthFull();
                trashDirBtn.addClickListener(ev -> confirmAndRun(
                        "Alle " + count + " übereinstimmenden Dateien aus diesem Ordner werden gelöscht (Trash), "
                                + "unabhängig davon ob sie noch woanders gebraucht werden. Fortfahren?",
                        () -> {
                            int ok = 0, fail = 0;
                            for (List<FileNode> group : relevantGroups.values()) {
                                for (FileNode fn : group) {
                                    if (fn.getAddress().startsWith(prefix)) {
                                        try { duplicateService.moveToTrash(fn); ok++; }
                                        catch (Exception ex) { fail++; }
                                    }
                                }
                            }
                            treeService.invalidateDuplicateCache();
                            fileSystemTree.getDataProvider().refreshAll();
                            dialog.close();
                            String msg = ok + " Dateien in Trash verschoben";
                            if (fail > 0) msg += ", " + fail + " fehlgeschlagen";
                            Notification.show(msg, 3000, Notification.Position.TOP_CENTER)
                                    .addThemeVariants(fail > 0
                                            ? NotificationVariant.LUMO_WARNING
                                            : NotificationVariant.LUMO_SUCCESS);
                        }));

                Button trashOtherBtn = new Button("Alle " + count + " Dateien aus dem anderen Ordner löschen (Trash)",
                        VaadinIcon.TRASH.create());
                trashOtherBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                trashOtherBtn.setWidthFull();
                trashOtherBtn.addClickListener(ev -> confirmAndRun(
                        "Alle " + count + " übereinstimmenden Dateien aus '" + otherDir + "' werden gelöscht (Trash), "
                                + "unabhängig davon ob sie noch woanders gebraucht werden. Fortfahren?",
                        () -> {
                            int ok = 0, fail = 0;
                            for (List<FileNode> group : relevantGroups.values()) {
                                for (FileNode fn : group) {
                                    if (fn.getAddress().startsWith(otherDir)) {
                                        try { duplicateService.moveToTrash(fn); ok++; }
                                        catch (Exception ex) { fail++; }
                                    }
                                }
                            }
                            treeService.invalidateDuplicateCache();
                            fileSystemTree.getDataProvider().refreshAll();
                            dialog.close();
                            String msg = ok + " Dateien in Trash verschoben";
                            if (fail > 0) msg += ", " + fail + " fehlgeschlagen";
                            Notification.show(msg, 3000, Notification.Position.TOP_CENTER)
                                    .addThemeVariants(fail > 0
                                            ? NotificationVariant.LUMO_WARNING
                                            : NotificationVariant.LUMO_SUCCESS);
                        }));

                VerticalLayout advancedContent = new VerticalLayout(trashDirBtn, trashOtherBtn);
                advancedContent.setPadding(false);
                advancedContent.getStyle().set("gap", "4px");

                Details advanced = new Details("Erweitert: nur löschen, ohne zu verschieben", advancedContent);
                advanced.addThemeVariants(com.vaadin.flow.component.details.DetailsVariant.SMALL);
                advanced.getStyle().set("margin-top", "6px");
                if (canEdit) card.add(advanced);

                hint.add(card);
            });
            content.add(hint);
        }

        // Einzelne Gruppen (max. 20 anzeigen)
        int shown = 0;
        for (List<FileNode> group : relevantGroups.values()) {
            if (shown++ >= 20) {
                content.add(new Span("... und " + (relevantGroups.size() - 20) + " weitere Gruppen"));
                break;
            }
            List<FileNode> remaining = new ArrayList<>(group);
            for (FileNode node : group) {
                content.add(buildDialogRow(node, remaining, content, dialog));
            }
            content.add(new com.vaadin.flow.component.html.Hr());
        }

        com.vaadin.flow.component.orderedlayout.Scroller scroller =
                new com.vaadin.flow.component.orderedlayout.Scroller(content);
        scroller.setWidthFull();
        dialog.add(scroller);
        dialog.open();
    }

    private void showDuplicatesForTag(TagNode tn) {
        List<FileNode> taggedFiles = tagService.getFilesForTag(tn.getTag());
        Map<String, List<FileNode>> allGroups = duplicateService.findDuplicateGroups();

        // Nur Gruppen wo mindestens eine Datei unter diesem Tag ist
        Map<String, List<FileNode>> relevantGroups = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<FileNode>> entry : allGroups.entrySet()) {
            boolean hasTaggedFile = entry.getValue().stream()
                    .anyMatch(fn -> taggedFiles.stream().anyMatch(tf -> tf.getId().equals(fn.getId())));
            if (hasTaggedFile) relevantGroups.put(entry.getKey(), entry.getValue());
        }

        if (relevantGroups.isEmpty()) {
            Notification.show("Keine Duplikate unter Tag '" + tn.getTag().getName() + "'",
                    3000, Notification.Position.TOP_CENTER);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setWidth("700px");
        dialog.setDraggable(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        Span title = new Span("Duplikate unter Tag '" + tn.getTag().getName() + "' — " + relevantGroups.size() + " Gruppen");
        title.getStyle().set("font-weight", "bold").set("font-size", "1.1em");
        Button closeBtn = new Button(new Icon(VaadinIcon.CLOSE), e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        header.add(title, closeBtn);

        VerticalLayout content = new VerticalLayout(header);
        content.setPadding(false);
        content.setSpacing(true);

        for (List<FileNode> group : relevantGroups.values()) {
            List<FileNode> remaining = new ArrayList<>(group);
            for (FileNode node : group) {
                content.add(buildDialogRow(node, remaining, content, dialog));
            }
            content.add(new com.vaadin.flow.component.html.Hr());
        }

        dialog.add(content);
        dialog.open();
    }

    private void showDuplicateDialog(FileNode fileNode) {
        String hash = fileNode.getContentHash();
        if (hash == null) return;

        Map<String, List<FileNode>> groups = duplicateService.findDuplicateGroups();
        List<FileNode> group = groups.get(hash);
        if (group == null || group.isEmpty()) return;

        Dialog dialog = new Dialog();
        dialog.setWidth("700px");
        dialog.setDraggable(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        Span title = new Span("Duplikate — " + group.size() + " Dateien");
        title.getStyle().set("font-weight", "bold").set("font-size", "1.1em");
        Button closeBtn = new Button(new Icon(VaadinIcon.CLOSE), e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        header.add(title, closeBtn);

        VerticalLayout content = new VerticalLayout(header);
        content.setPadding(false);
        content.setSpacing(true);

        List<FileNode> remaining = new ArrayList<>(group);
        for (FileNode node : group) {
            content.add(buildDialogRow(node, remaining, content, dialog));
        }

        dialog.add(content);
        dialog.open();
    }

    private HorizontalLayout buildDialogRow(FileNode node, List<FileNode> remaining,
                                             VerticalLayout content, Dialog dialog) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        VerticalLayout info = new VerticalLayout();
        info.setPadding(false);
        info.setSpacing(false);

        Span nameLbl = new Span(node.getName());
        nameLbl.getStyle().set("font-weight", "bold");
        Span pathLbl = new Span(node.getAddress());
        pathLbl.getStyle().set("font-size", "0.8em").set("color", "gray");
        String sizeStr = node.getFileSize() != null ? formatSize(node.getFileSize()) : "?";
        String modStr  = node.getModifiedDate() != null ? node.getModifiedDate().toString() : "—";
        Span metaLbl = new Span("Größe: " + sizeStr + "  |  Geändert: " + modStr);
        metaLbl.getStyle().set("font-size", "0.8em").set("color", "#666");
        info.add(nameLbl, pathLbl, metaLbl);

        Button trashBtn = new Button("In Trash", VaadinIcon.TRASH.create());
        trashBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        trashBtn.addClickListener(e -> {
            try {
                duplicateService.moveToTrash(node);
                remaining.remove(node);
                content.remove(row);
                treeService.invalidateDuplicateCache();
                fileSystemTree.getDataProvider().refreshAll();
                if (remaining.size() <= 1) {
                    dialog.close();
                    Notification.show("Duplikat gelöscht — Gruppe aufgelöst.",
                            2000, Notification.Position.BOTTOM_START)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } else {
                    Notification.show("In Trash: " + node.getName(),
                            2000, Notification.Position.BOTTOM_START)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }
            } catch (Exception ex) {
                Notification.show("Fehler: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        trashBtn.setVisible(canEdit);
        row.add(info, trashBtn);
        row.expand(info);
        return row;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void scanSelected() {
        FileNode target = selectedNode != null ? selectedNode.getFileNode() : null;
        if (target != null && !target.getType().equals("file") && !(target instanceof ImageFile)) {
            UI ui = UI.getCurrent();
            ProcessLogger.ProcessHandle handle = processLogger.start(ui, ProcessType.SCAN, target.getAddress());
            Thread thread = new Thread(() -> {
                try {
                    fsService.scanSync(target);
                    ui.access(() -> {
                        fileSystemTree.getDataProvider().refreshItem(selectedNode, true);
                        handle.success("Scan abgeschlossen");
                    });
                } catch (NoSuchFileException e) {
                    handle.error("Verzeichnis nicht gefunden");
                } catch (Exception e) {
                    handle.error(e.getMessage());
                }
            });
            thread.start();
        } else {
            Notification.show("Bitte ein Verzeichnis auswählen", 2000, Notification.Position.BOTTOM_START);
        }
    }

    @Override
    public void refreshAll() {
        UI ui = UI.getCurrent();
        ProcessLogger.ProcessHandle handle = processLogger.start(ui, ProcessType.REFRESH, "Alle Dateisysteme");
        treeService.invalidateDuplicateCache();
        fileSystemTree.getDataProvider().refreshAll();
        handle.success("Tree aktualisiert");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        Set<String> expanded = TreeStateService.getExpandedAddresses();
        if (expanded.isEmpty()) return;

        List<VFSNode> roots = new ArrayList<>(treeService.getRoots());
        roots.add(TAG_ROOT);

        for (VFSNode root : roots) {
            String addr = root == TAG_ROOT ? "__TAG_ROOT__"
                    : root.getFileNode() != null ? root.getFileNode().getAddress() : null;
            if (addr != null && expanded.contains(addr)) {
                fileSystemTree.expand(root);
                expandChildrenRecursive(root, expanded);
            }
        }
    }

    private void expandChildrenRecursive(VFSNode parent, Set<String> expanded) {
        List<VFSNode> children;

        if (parent == TAG_ROOT) {
            // Kinder von TAG_ROOT sind TagNodes
            children = tagService.getRootTags(currentUser.get().orElse(null)).stream()
                    .map(t -> (VFSNode) new TagNode(t))
                    .collect(java.util.stream.Collectors.toList());
        } else if (parent instanceof TagNode tn) {
            // Kinder eines TagNode: Sub-Tags
            children = tagService.getChildren(tn.getTag(), currentUser.get().orElse(null)).stream()
                    .map(t -> (VFSNode) new TagNode(t))
                    .collect(java.util.stream.Collectors.toList());
        } else if (parent.getFileNode() != null) {
            // Normale Verzeichnis-Kinder
            try {
                children = treeService.getChildren(parent);
            } catch (Exception e) {
                return;
            }
        } else {
            return;
        }

        for (VFSNode child : children) {
            String addr;
            if (child instanceof TagNode tn) {
                addr = "__TAG__" + tn.getTag().getId();
            } else if (child.getFileNode() != null) {
                addr = child.getFileNode().getAddress();
            } else {
                continue;
            }
            if (expanded.contains(addr)) {
                fileSystemTree.expand(child);
                expandChildrenRecursive(child, expanded);
            }
        }
    }
}
