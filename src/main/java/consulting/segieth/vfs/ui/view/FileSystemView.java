package consulting.segieth.vfs.ui.view;

import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Set;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileSystem;
import consulting.segieth.vfs.service.FileSystemService;

public abstract class FileSystemView extends Main {

    protected final FileSystemService fsService;

    final TextField description;
    final TextField id;
    final TextField name;
    final TextField address;
    final DatePicker synchDate;
    final Button newBtn;
    final Button deleteBtn;
    final Button saveBtn;
    final Button scanBtn;
    Set<FileNode> selected;

    public FileSystemView(FileSystemService fsService) {
        this.fsService = fsService;

        description = new TextField();
        name = new TextField();
        address = new TextField();
        id = new TextField();
        id.setVisible(false);
        description.setPlaceholder("What do you want to do?");
        description.setAriaLabel("Task description");
        description.setMinWidth("20em");

        synchDate = new DatePicker();
        synchDate.setPlaceholder("Due date");
        synchDate.setAriaLabel("Due date");

        newBtn = new Button("New", event -> newEntity());
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        saveBtn = new Button("Save", event -> save());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        deleteBtn = new Button("Delete", event -> delete());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        scanBtn = new Button("Scan", event -> scan());
        scanBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(getLocale());


        setSizeFull();
        addClassNames(LumoUtility.BoxSizing.BORDER, LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN,
                LumoUtility.Padding.MEDIUM, LumoUtility.Gap.SMALL);

        // add(new ViewToolbar("FileSystem List", ViewToolbar.group(name, address, description, synchDate, newBtn,saveBtn,deleteBtn,scanBtn)));
    }

    protected FileSystem selectedFileSystem;
    protected void scan() {
    	try {
			fsService.scan(UI.getCurrent(),this,selectedFileSystem);
		} catch (NoSuchFileException e) {
	        Notification.show(String.format("directory %s not found", selectedFileSystem.getAddress()), 3000, Notification.Position.TOP_START)
	        .addThemeVariants(NotificationVariant.LUMO_ERROR);
		}
    }

    protected void delete() {
        Notification.show("not yet implemented", 3000, Notification.Position.TOP_START)
        .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    protected void save() {
        String idString = id.getValue();
		long idValue = 0;
        if (idString == null || idString.isBlank()) {
			fsService.createSystem(this,
	        		name.getValue(), 
	        		address.getValue(), 
	        		description.getValue(), 
	        		synchDate.getValue());
        }
        else
		try {
			idValue = Long.parseLong(idString);
			fsService.save(
	        		idValue,
	        		name.getValue(), 
	        		name.getValue(), 
	        		address.getValue(), 
	        		description.getValue(),
	        		null,
	        		synchDate.getValue());
		}
		catch (Exception exc) {
		}
        refreshAll();
        id.clear();
        name.clear();
        address.clear();
        description.clear();
        synchDate.clear();
        Notification.show("FileSystem added", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
    
	public abstract void refreshAll();

	private void newEntity() {
		fsService.createSystem(this,name.getValue(), name.getValue(), address.getValue(),LocalDate.now());
	    id.clear();
	    address.clear();
	    description.clear();
	    name.clear();
	}
}
