package consulting.segieth.vfs.ui.view;

import java.nio.file.NoSuchFileException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Set;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import consulting.segieth.base.ui.ViewToolbar;
import consulting.segieth.vfs.domain.FileSystem;
import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.service.FileSystemService;
import consulting.segieth.vfs.service.HashService;
import consulting.segieth.vfs.service.ImageService;
import consulting.segieth.vfs.service.VFSNode;
import jakarta.annotation.security.PermitAll;

@Route("")
@PageTitle("FileSystem List")
@Menu(order = 0, icon = "vaadin:clipboard-check", title = "FileSystem List")
@PermitAll // When security is enabled, allow all authenticated users
public class FileSystemListView extends FileSystemView {

    // private final FileSystemService fsService;

    final TextField description;
    final TextField id;
    final TextField name;
    final TextField address;
    final TextField trashPath;
    final DatePicker synchDate;
    final Button newBtn;
    final Button deleteBtn;
    final Button saveBtn;
    final Button scanBtn;
    Button thumbnailBtn;
    Button hashBtn;
    com.vaadin.flow.component.progressbar.ProgressBar progressBar;
    com.vaadin.flow.component.html.Span progressLabel;
    final Grid<FileSystem> fileSystemGrid;
    Set<FileSystem> selected;
	private ListDataProvider<FileSystem> dataprovider;

    public FileSystemListView(FileSystemService fsService, ImageService imageService, HashService hashService) {
        super(fsService);

        description = new TextField();
        name = new TextField();
        address = new TextField();
        trashPath = new TextField();
        trashPath.setPlaceholder("Trash-Pfad (z.B. P:\\trash)");
        trashPath.setAriaLabel("Trash-Pfad");
        trashPath.setMinWidth("20em");
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

        thumbnailBtn = new Button("Thumbnails generieren", event -> {
            thumbnailBtn.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setValue(0);
            progressLabel.setVisible(true);
            progressLabel.setText("0 von ? Bildern");
            UI ui = UI.getCurrent();
            Thread thread = new Thread(() -> {
                imageService.generateMissingThumbnails((done, total) -> {
                    ui.access(() -> {
                        progressBar.setValue((double) done / total);
                        progressLabel.setText(done + " von " + total + " Bildern");
                    });
                });
                ui.access(() -> {
                    thumbnailBtn.setEnabled(true);
                    progressBar.setVisible(false);
                    progressLabel.setVisible(false);
                    Notification.show("Thumbnails fertig!", 3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
            });
            thread.start();
        });
        thumbnailBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        hashBtn = new Button("Hashes berechnen", event -> {
            hashBtn.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setValue(0);
            progressLabel.setVisible(true);
            progressLabel.setText("0 von ? Dateien");
            UI ui = UI.getCurrent();
            Thread thread = new Thread(() -> {
                hashService.computeMissingHashes((done, total) -> {
                    ui.access(() -> {
                        progressBar.setValue((double) done / total);
                        progressLabel.setText(done + " von " + total + " Dateien");
                    });
                });
                ui.access(() -> {
                    hashBtn.setEnabled(true);
                    progressBar.setVisible(false);
                    progressLabel.setVisible(false);
                    Notification.show("Hashes fertig!", 3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
            });
            thread.start();
        });
        hashBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        progressBar = new com.vaadin.flow.component.progressbar.ProgressBar();
        progressBar.setWidthFull();
        progressBar.setVisible(false);

        progressLabel = new com.vaadin.flow.component.html.Span();
        progressLabel.setVisible(false);

        var dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(getLocale());

        dataprovider = new ListDataProvider<FileSystem>(new ArrayList<FileSystem>());
        fileSystemGrid = new Grid<>(FileSystem.class);
        fileSystemGrid.setDataProvider(dataprovider);
        dataprovider.getItems().addAll(fsService.list());

		GridContextMenu<FileSystem> contextMenu = fileSystemGrid.addContextMenu();

		// Einen einfachen Eintrag hinzufügen
		contextMenu.addItem("Bearbeiten", event -> {
			event.getItem().ifPresent(department -> {
				// Logik zum Bearbeiten
				System.out.println("Edit: " + department.getName());
			});
		});

		// Einen Eintrag mit Icon oder spezieller Logik
		contextMenu.addItem("scan", event -> {
			event.getItem().ifPresent(fileSystem -> {
				try {
					fsService.scan(UI.getCurrent(),this,fileSystem);
				} catch (NoSuchFileException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		});

		fileSystemGrid.setSizeFull();

        fileSystemGrid.addSelectionListener(event -> {
            selected = event.getAllSelectedItems();
    		selectedFileSystem = selected.iterator().next();
            id.setValue("" + selectedFileSystem.getId());
            name.setValue(selectedFileSystem.getName());
            address.setValue(selectedFileSystem.getAddress());
            description.setValue(selectedFileSystem.getDescription());
            trashPath.setValue(selectedFileSystem.getTrashPath() != null ? selectedFileSystem.getTrashPath() : "");
            System.out.println(selected.size() + " items selected");
         });
        

        setSizeFull();
        addClassNames(LumoUtility.BoxSizing.BORDER, LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN,
                LumoUtility.Padding.MEDIUM, LumoUtility.Gap.SMALL);

        add(new ViewToolbar("FileSystem List", ViewToolbar.group(name, address, trashPath, description, synchDate, newBtn, saveBtn, deleteBtn, scanBtn, thumbnailBtn, hashBtn)));
        add(progressLabel);
        add(progressBar);
        add(fileSystemGrid);
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
	        		trashPath.getValue(),
	        		synchDate.getValue());
		}
		catch (Exception exc) {
		}
        dataprovider.refreshAll();
        id.clear();
        name.clear();
        address.clear();
        description.clear();
        trashPath.clear();
        synchDate.clear();
        Notification.show("FileSystem saved", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

	private void newEntity() {
	    id.clear();
	    address.clear();
	    description.clear();
	    name.clear();
	    trashPath.clear();
	}

	@Override
	public void refreshAll() {
		fileSystemGrid.getDataProvider().refreshAll();
	}

}
