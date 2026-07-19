package consulting.segieth.vfs.ui.view;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.FlexLayout.FlexWrap;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.Tag;
import consulting.segieth.vfs.service.TagService;
import de.witchcafe.auth.AppUserInfo;

/**
 * Dialog zum Anzeigen und Bearbeiten der Tags einer oder mehrerer Dateien.
 * Zeigt alle verfügbaren Tags als Checkboxen, neue Tags können inline erstellt werden.
 */
public class TagDialog extends Dialog {

    public TagDialog(List<FileNode> nodes, TagService tagService, AppUserInfo user, Runnable onClose) {
        setWidth("500px");
        setDraggable(true);

        String title = nodes.size() == 1
                ? "Tags: " + nodes.get(0).getName()
                : "Tags für " + nodes.size() + " Dateien";

        Span heading = new Span(title);
        heading.getStyle().set("font-weight", "bold").set("font-size", "1.1em");

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> { close(); onClose.run(); });
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(heading, closeBtn);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        // Tag-Chips — alle verfügbaren Tags als Checkboxen
        List<Tag> allTags = tagService.getAllTags(user);
        List<Tag> currentTags = nodes.size() == 1 ? tagService.getTagsForNode(nodes.get(0)) : List.of();

        FlexLayout tagGrid = new FlexLayout();
        tagGrid.setFlexWrap(FlexWrap.WRAP);
        tagGrid.getStyle().set("gap", "8px").set("padding", "8px 0");

        for (Tag tag : allTags) {
            boolean checked = currentTags.stream().anyMatch(t -> t.getId().equals(tag.getId()));
            Checkbox cb = new Checkbox(tag.getFullPath(), checked);
            cb.addValueChangeListener(e -> {
                for (FileNode node : nodes) {
                    if (e.getValue()) tagService.addTag(node, tag);
                    else tagService.removeTag(node, tag);
                }
            });
            tagGrid.add(cb);
        }

        if (allTags.isEmpty()) {
            tagGrid.add(new Span("Noch keine Tags vorhanden."));
        }

        // Neuen Tag inline erstellen
        TextField newTagField = new TextField();
        newTagField.setPlaceholder("Neuer Tag (z.B. Urlaub/Kreta)");
        newTagField.setClearButtonVisible(true);
        newTagField.setWidthFull();

        Checkbox publicCb = new Checkbox("Öffentlich");

        Button createBtn = new Button("Tag erstellen + zuweisen", e -> {
            String input = newTagField.getValue().trim();
            if (input.isEmpty()) {
                Notification.show("Bitte Tag-Namen eingeben", 2000, Notification.Position.BOTTOM_START);
                return;
            }
            try {
                String[] parts = input.split("[/\\\\]");
                Tag parent = null;
                Tag created = null;
                for (String part : parts) {
                    if (part.isBlank()) continue;
                    created = tagService.createTag(part.trim(), parent, user, publicCb.getValue());
                    parent = created;
                }

                if (created != null) {
                    Tag finalTag = created;
                    for (FileNode node : nodes) tagService.addTag(node, finalTag);

                    Checkbox cb = new Checkbox(finalTag.getFullPath(), true);
                    cb.addValueChangeListener(ev -> {
                        for (FileNode node : nodes) {
                            if (ev.getValue()) tagService.addTag(node, finalTag);
                            else tagService.removeTag(node, finalTag);
                        }
                    });
                    tagGrid.add(cb);
                    newTagField.clear();

                    Notification.show("Tag erstellt: " + finalTag.getFullPath(),
                            2000, Notification.Position.BOTTOM_START)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Notification.show("Fehler: " + ex.getMessage(), 4000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout newTagRow = new HorizontalLayout(newTagField, publicCb, createBtn);
        newTagRow.setDefaultVerticalComponentAlignment(Alignment.END);
        newTagRow.setWidthFull();
        newTagRow.expand(newTagField);

        VerticalLayout content = new VerticalLayout(header, tagGrid, new Hr(), newTagRow);
        content.setPadding(false);
        content.setSpacing(true);

        add(content);
    }
}
