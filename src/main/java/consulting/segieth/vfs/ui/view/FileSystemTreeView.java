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
import jakarta.annotation.security.PermitAll;

@Route("")
@PageTitle("FileSystem Tree")
@Menu(order = 1, icon = "vaadin:folder-open", title = "FileSystem Tree")
@PreserveOnRefresh
@PermitAll
public class FileSystemTreeView extends FileSystemView {

    private final TreeService treeService;
    private final DuplicateService duplicateService;
    private final MoveService moveService;
    private final TagService tagService;
    private final CurrentUser currentUser;
    private final ProcessLogger processLogger;
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
        this.processLogger = processLogger;

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

        HorizontalLayout toolbar = new HorizontalLayout(
                refreshBtn, scanBtn,
                new com.vaadin.flow.component.html.Span(" | "),
                newFsBtn, editFsBtn, deleteFsBtn,
                new com.vaadin.flow.component.html.Span(" | "),
                thumbnailBtn, hashBtn);
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
                if (parent == TAG_ROOT) return tagService.getRootTags(currentUser.require()).size();
                if (parent instanceof TagNode tn) return tagService.getChildren(tn.getTag(), currentUser.require()).size() + tagService.getFilesForTag(tn.getTag()).size();
                return (int) treeService.getChildren(parent).stream()
                        .skip(query.getOffset()).limit(query.getLimit()).count();
            }

            @Override
            public boolean hasChildren(VFSNode node) {
                if (node == TAG_ROOT) return true;
                if (node instanceof TagNode tn) {
                    AppUserInfo user = currentUser.require();
                    return tagService.hasChildren(tn.getTag(), user);
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
                    // Kinder von TAG_ROOT: Root-Tags als TagNode
                    AppUserInfo user = currentUser.require();
                    return tagService.getRootTags(user).stream()
                            .map(t -> (VFSNode) new TagNode(t))
                            .skip(query.getOffset()).limit(query.getLimit());
                }

                if (parent instanceof TagNode tn) {
                    // Kinder eines Tags: Sub-Tags + getaggte Dateien
                    AppUserInfo user = currentUser.require();
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
            icon.addClickListener(e -> showDuplicateDialog(node.getFileNode()));
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
                actions.add(triageBtn);

                // Duplikate unter diesem Tag
                Button dupBtn = new Button(VaadinIcon.COPY.create());
                dupBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                dupBtn.getElement().setAttribute("title", "Duplikate unter diesem Tag");
                dupBtn.addClickListener(e -> showDuplicatesForTag(tn));
                actions.add(dupBtn);

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
                actions.add(triageBtn);
            }

            if (!type.equals("system")) {
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

        contextMenu.addItem("Vom Dateisystem scannen", event -> event.getItem().ifPresent(node -> {
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

        contextMenu.addItem("Tags bearbeiten", event ->
            event.getItem().filter(n -> n != TAG_ROOT && !(n instanceof TagNode) && n.getFileNode() != null)
                 .ifPresent(n -> {
                    AppUserInfo user = currentUser.require();
                    new TagDialog(List.of(n.getFileNode()), tagService, user, () -> {}).open();
                }));

        contextMenu.addItem("Verschieben nach...", event ->
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

    private void showDuplicatesForFolder(String system, String folderAddress) {
        Map<String, List<FileNode>> allGroups = duplicateService.findDuplicateGroups();

        // Nur Gruppen wo mindestens eine Datei unter diesem Ordner liegt
        String prefix = folderAddress.endsWith("\\") || folderAddress.endsWith("/")
                ? folderAddress : folderAddress + "\\";
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

        Dialog dialog = new Dialog();
        dialog.setWidth("700px");
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
            children = tagService.getRootTags(currentUser.require()).stream()
                    .map(t -> (VFSNode) new TagNode(t))
                    .collect(java.util.stream.Collectors.toList());
        } else if (parent instanceof TagNode tn) {
            // Kinder eines TagNode: Sub-Tags
            children = tagService.getChildren(tn.getTag(), currentUser.require()).stream()
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
