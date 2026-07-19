package consulting.segieth.vfs.ui.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.service.DuplicateService;
import jakarta.annotation.security.PermitAll;

@Route("duplicates")
@PageTitle("Duplikate")
@Menu(order = 3, icon = "vaadin:copy", title = "Duplikate")
@PermitAll
public class DuplicateView extends VerticalLayout {

    private final DuplicateService duplicateService;
    private final VerticalLayout groupContainer;

    public DuplicateView(DuplicateService duplicateService) {
        this.duplicateService = duplicateService;

        Button scanBtn = new Button("Duplikate suchen", e -> loadDuplicates());
        scanBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(scanBtn);

        groupContainer = new VerticalLayout();
        groupContainer.setPadding(false);
        groupContainer.setSpacing(false);

        Scroller scroller = new Scroller(groupContainer);
        scroller.setSizeFull();

        add(toolbar, scroller);
        setSizeFull();
        setPadding(true);
    }

    private void loadDuplicates() {
        groupContainer.removeAll();

        Map<String, List<FileNode>> groups = duplicateService.findDuplicateGroups();

        if (groups.isEmpty()) {
            groupContainer.add(new Span("Keine Duplikate gefunden."));
            return;
        }

        int groupNr = 1;
        for (Map.Entry<String, List<FileNode>> entry : groups.entrySet()) {
            groupContainer.add(buildGroupLayout(groupNr++, entry.getKey(), new ArrayList<>(entry.getValue())));
        }
    }

    private VerticalLayout buildGroupLayout(int groupNr, String hash, List<FileNode> nodes) {
        VerticalLayout groupLayout = new VerticalLayout();
        groupLayout.setPadding(true);
        groupLayout.setSpacing(false);
        groupLayout.getStyle()
                .set("border", "1px solid #e2e2e2")
                .set("border-radius", "8px")
                .set("margin-bottom", "12px");

        Span groupTitle = new Span("Gruppe " + groupNr + " — " + nodes.size() + " Dateien  |  Hash: " + hash.substring(0, 12) + "...");
        groupTitle.getStyle().set("font-weight", "bold").set("color", "#555");
        groupLayout.add(groupTitle);

        for (FileNode node : nodes) {
            groupLayout.add(buildNodeRow(node, nodes, groupLayout));
        }

        groupLayout.add(new Hr());
        return groupLayout;
    }

    private HorizontalLayout buildNodeRow(FileNode node, List<FileNode> groupNodes, VerticalLayout groupLayout) {
        HorizontalLayout row = new HorizontalLayout();
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        row.setWidthFull();

        VerticalLayout info = new VerticalLayout();
        info.setPadding(false);
        info.setSpacing(false);

        Span nameLbl = new Span(node.getName());
        nameLbl.getStyle().set("font-weight", "bold");

        Span pathLbl = new Span(node.getAddress());
        pathLbl.getStyle().set("font-size", "0.8em").set("color", "gray");

        Span systemLbl = new Span("System: " + node.getSystem());
        systemLbl.getStyle().set("font-size", "0.8em").set("color", "#888");

        String sizeStr = node.getFileSize() != null ? formatSize(node.getFileSize()) : "unbekannt";
        String createdStr = node.getCreatedDate() != null ? node.getCreatedDate().toString() : "—";
        String modifiedStr = node.getModifiedDate() != null ? node.getModifiedDate().toString() : "—";

        Span metaLbl = new Span("Größe: " + sizeStr + "  |  Erstellt: " + createdStr + "  |  Geändert: " + modifiedStr);
        metaLbl.getStyle().set("font-size", "0.8em").set("color", "#666");

        info.add(nameLbl, pathLbl, systemLbl, metaLbl);

        Button trashBtn = new Button("In Trash");
        trashBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        trashBtn.addClickListener(e -> deleteNode(node, groupNodes, row, groupLayout));

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

    private void deleteNode(FileNode node, List<FileNode> groupNodes, HorizontalLayout row, VerticalLayout groupLayout) {
        try {
            duplicateService.moveToTrash(node);
            groupNodes.remove(node);
            groupLayout.remove(row);

            // Gruppe ausblenden wenn nur noch eine Datei übrig
            if (groupNodes.size() <= 1) {
                groupContainer.remove(groupLayout);
                // Wenn gar keine Gruppen mehr: Meldung zeigen
                if (groupContainer.getComponentCount() == 0) {
                    groupContainer.add(new Span("Keine Duplikate mehr vorhanden."));
                }
            } else {
                // Zähler in der Gruppenüberschrift aktualisieren
                groupLayout.getChildren().findFirst().ifPresent(c -> {
                    if (c instanceof Span title) {
                        String current = title.getText();
                        title.setText(current.replaceAll("\\d+ Dateien", groupNodes.size() + " Dateien"));
                    }
                });
            }

            Notification.show("In Trash verschoben: " + node.getName(), 2000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception ex) {
            Notification.show("Fehler: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
