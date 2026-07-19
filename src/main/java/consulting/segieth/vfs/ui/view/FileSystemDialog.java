package consulting.segieth.vfs.ui.view;

import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import consulting.segieth.vfs.domain.FileSystem;
import consulting.segieth.vfs.service.FileSystemService;

/**
 * Dialog zum Anlegen oder Bearbeiten eines FileSystems.
 */
public class FileSystemDialog extends Dialog {

    public FileSystemDialog(FileSystemService fsService, FileSystem existing, Runnable onSave) {
        boolean isNew = existing == null;
        setWidth("480px");
        setDraggable(true);

        Span heading = new Span(isNew ? "Neues Dateisystem" : "Dateisystem bearbeiten");
        heading.getStyle().set("font-weight", "bold").set("font-size", "1.1em");

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setValue(existing != null ? existing.getName() : "");

        TextField addressField = new TextField("Pfad");
        addressField.setWidthFull();
        addressField.setPlaceholder("z.B. C:\\Pictures oder P:\\");
        addressField.setValue(existing != null ? existing.getAddress() : "");

        TextField trashField = new TextField("Trash-Pfad");
        trashField.setWidthFull();
        trashField.setPlaceholder("z.B. C:\\trash");
        trashField.setValue(existing != null && existing.getTrashPath() != null ? existing.getTrashPath() : "");

        TextField descField = new TextField("Beschreibung");
        descField.setWidthFull();
        descField.setValue(existing != null && existing.getDescription() != null ? existing.getDescription() : "");

        Button saveBtn = new Button(isNew ? "Anlegen" : "Speichern");
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> {
            String name = nameField.getValue().trim();
            String address = addressField.getValue().trim();
            if (name.isEmpty() || address.isEmpty()) {
                Notification.show("Name und Pfad sind Pflichtfelder.", 2000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                if (isNew) {
                    fsService.create(name, name, "system", address, descField.getValue(), null);
                } else {
                    fsService.save(existing.getId(), name, name, address, descField.getValue(),
                            trashField.getValue(), null);
                }
                close();
                onSave.run();
                Notification.show(isNew ? "Dateisystem angelegt" : "Gespeichert",
                        2000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Fehler: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Abbrechen", e -> close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footer = new HorizontalLayout(cancelBtn, saveBtn);
        footer.setJustifyContentMode(JustifyContentMode.END);
        footer.setWidthFull();

        VerticalLayout content = new VerticalLayout(heading, nameField, addressField, trashField, descField, footer);
        content.setPadding(false);
        content.setSpacing(true);
        add(content);
    }
}
