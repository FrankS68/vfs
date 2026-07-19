package consulting.segieth.vfs.ui.view;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.service.TreeService;
import consulting.segieth.vfs.service.VFSNode;

/**
 * Dialog mit einem Tree zum Auswählen eines Zielverzeichnisses.
 * Ruft onSelect mit dem gewählten FileNode auf.
 */
public class TreePickerDialog extends Dialog {

    private VFSNode selectedNode = null;

    public TreePickerDialog(TreeService treeService, Consumer<FileNode> onSelect) {
        setWidth("500px");
        setHeight("500px");
        setDraggable(true);

        // Header
        Span title = new Span("Zielverzeichnis wählen");
        title.getStyle().set("font-weight", "bold").set("font-size", "1.1em");
        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout header = new HorizontalLayout(title, closeBtn);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        // Tree — nur Verzeichnisse und FileSystems
        TreeGrid<VFSNode> tree = new TreeGrid<>();
        tree.setDataProvider(new AbstractBackEndHierarchicalDataProvider<VFSNode, Void>() {

            @Override
            public int getChildCount(HierarchicalQuery<VFSNode, Void> query) {
                VFSNode parent = query.getParentOptional().orElse(null);
                if (parent == null) return treeService.getRoots().size();
                return (int) getDirectoriesOnly(treeService.getChildren(parent))
                        .skip(query.getOffset()).limit(query.getLimit()).count();
            }

            @Override
            public boolean hasChildren(VFSNode node) {
                return treeService.hasChildren(node) &&
                       !node.getFileNode().getType().equals("file");
            }

            @Override
            protected Stream<VFSNode> fetchChildrenFromBackEnd(HierarchicalQuery<VFSNode, Void> query) {
                VFSNode parent = query.getParentOptional().orElse(null);
                List<VFSNode> children = parent == null
                        ? treeService.getRoots()
                        : treeService.getChildren(parent);
                return getDirectoriesOnly(children)
                        .skip(query.getOffset()).limit(query.getLimit());
            }

            private Stream<VFSNode> getDirectoriesOnly(List<VFSNode> nodes) {
                return nodes.stream().filter(n ->
                        n.getFileNode().getType().equals("directory") ||
                        n.getFileNode().getType().equals("system"));
            }
        });

        tree.addHierarchyColumn(VFSNode::getName).setHeader("Verzeichnis").setAutoWidth(true);
        tree.setSizeFull();

        tree.addSelectionListener(e -> selectedNode = e.getFirstSelectedItem().orElse(null));

        // Footer
        Button selectBtn = new Button("Hierher verschieben", e -> {
            if (selectedNode != null) {
                onSelect.accept(selectedNode.getFileNode());
                close();
            }
        });
        selectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Abbrechen", e -> close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footer = new HorizontalLayout(cancelBtn, selectBtn);
        footer.setJustifyContentMode(JustifyContentMode.END);
        footer.setWidthFull();

        VerticalLayout content = new VerticalLayout(header, tree, footer);
        content.setSizeFull();
        content.setPadding(false);
        content.expand(tree);

        add(content);
    }
}
