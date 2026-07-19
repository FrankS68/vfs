package consulting.segieth.vfs.ui.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.service.ImageService;
import jakarta.annotation.security.PermitAll;

@Route("imagelist")
@PageTitle("Image List")
@Menu(order = 0, icon = "vaadin:clipboard-check", title = "Image List")
@PermitAll // When security is enabled, allow all authenticated users
public class ImageListView extends VerticalLayout {

	private final ImageService service;
    final Grid<ImageFile> fileSystemGrid;
	private ListDataProvider<ImageFile> dataprovider;
    private final TextField searchField;
    private final NumberField countField;
    private final NumberField countAllField;
	private Set<ImageFile> selected;
    final Button searchButton;
	private ImageFile selectedFileSystem;

    public ImageListView(ImageService service) {
        this.service = service;

        countField = new NumberField();
        countField.setEnabled(false);
        countAllField = new NumberField();
        countAllField.setEnabled(false);
        // Setup UI
        searchField = getSearchField();
        searchField.setPlaceholder("z.B. Urlaub 2024");
        searchField.setWidthFull();
        
        searchButton = new Button("Search", event -> {
        	System.out.println("search");
        });
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout searchBar = new HorizontalLayout(searchField, searchButton,countField,countAllField);
        searchBar.setVerticalComponentAlignment(Alignment.END, searchButton);
        searchBar.setWidthFull();
        
        dataprovider = new ListDataProvider<ImageFile>(new ArrayList<ImageFile>());
        fileSystemGrid = new Grid<>(ImageFile.class);
        fileSystemGrid.setDataProvider(dataprovider);
        // dataprovider.getItems().addAll(service.findAll());

		GridContextMenu<ImageFile> contextMenu = fileSystemGrid.addContextMenu();

		// Einen einfachen Eintrag hinzufügen
		contextMenu.addItem("Bearbeiten", event -> {
			event.getItem().ifPresent(department -> {
				// Logik zum Bearbeiten
				System.out.println("Edit: " + department.getName());
			});
		});

		fileSystemGrid.setSizeFull();

        fileSystemGrid.addSelectionListener(event -> {
            selected = event.getAllSelectedItems();
    		selectedFileSystem = selected.iterator().next();
            System.out.println(selected.size() + " items selected");
         });
        

        setSizeFull();
        addClassNames(LumoUtility.BoxSizing.BORDER, LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN,
                LumoUtility.Padding.MEDIUM, LumoUtility.Gap.SMALL);

        add(searchBar);
        add(fileSystemGrid);
    }
    
    @Override
    protected void onAttach(AttachEvent attachEvent) {
    	super.onAttach(attachEvent);
        
        // UI-Referenz speichern, da der Background-Thread sie braucht
        UI ui = attachEvent.getUI();

        dataprovider.getItems().clear();
        double totalCount = service.count().doubleValue();
        countAllField.setValue(totalCount);

        // Starte die Arbeit in einem Hintergrund-Thread
        Thread thread = new Thread(() -> {
            int pageSize = 100;
            Slice<ImageFile> slice = service.findAll(PageRequest.of(0, pageSize));

            while (slice.hasContent()) {
                List<ImageFile> data = slice.getContent();

                // Änderungen an der UI müssen in ui.access() gekapselt werden
                ui.access(() -> {
                    dataprovider.getItems().addAll(data);
                    countField.setValue((double) dataprovider.getItems().size());
                    dataprovider.refreshAll();
                    
                    ui.push();
                });

                if (slice.hasNext()) {
                    slice = service.findAll(slice.nextPageable());
                } else {
                    break;
                }
                
                // Optional: Kleiner Sleep, damit man das Update optisch verfolgen kann
                // try { Thread.sleep(50); } catch (InterruptedException e) {}
            }
        });
        
        thread.start();
    }


    
	private TextField getSearchField() {
		// Das Suchfeld erstellen
		TextField searchField = new TextField();
		searchField.setPlaceholder("Suche nach Name oder Typ...");
		searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
		searchField.setClearButtonVisible(true); // Erlaubt schnelles Löschen der Suche
		searchField.setValueChangeMode(ValueChangeMode.EAGER); // Filtert sofort beim Tippen

		// Die Filter-Logik
		searchField.addValueChangeListener(event -> {
		    String searchTerm = event.getValue().trim().toLowerCase();
		    
		    dataprovider.setFilter(imageFile -> {
		        if (searchTerm.isEmpty()) return true;
		        
		        boolean matches = matchAttribute(imageFile.getName(), searchTerm);
		        matches |= matchAttribute(imageFile.getAddress(), searchTerm);
		        matches |= matchAttribute(imageFile.getDescription(), searchTerm);
		        return matches;
		    });
		});
		return searchField;
	}

	private boolean matchAttribute(String attribute, String searchTerm) {
		return attribute != null && 
				attribute.toLowerCase().contains(searchTerm);
	}
}
