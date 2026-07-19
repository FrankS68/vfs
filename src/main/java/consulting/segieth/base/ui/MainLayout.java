package consulting.segieth.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.theme.lumo.LumoUtility;
import de.witchcafe.auth.CurrentUser;
import jakarta.annotation.security.PermitAll;

@Layout
@PermitAll
public final class MainLayout extends AppLayout {

    private final CurrentUser currentUser;

    MainLayout(CurrentUser currentUser) {
        this.currentUser = currentUser;
        setPrimarySection(Section.DRAWER);
        addToDrawer(createHeader(), new Scroller(createSideNav()));
        addToNavbar(createNavbar());
    }

    private Component createNavbar() {
        Button backBtn = new Button(new Icon(VaadinIcon.ARROW_LEFT));
        backBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backBtn.getElement().setAttribute("title", "Zurück");
        backBtn.addClickListener(e ->
            UI.getCurrent().getPage().executeJs("window.history.back()"));

        HorizontalLayout left = new HorizontalLayout(backBtn);
        left.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        left.setPadding(false);
        left.getStyle().set("padding-left", "12px");

        // User-Info rechts
        HorizontalLayout right = new HorizontalLayout();
        right.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        right.setSpacing(true);
        right.getStyle().set("padding-right", "12px");

        currentUser.get().ifPresent(user -> {
            // Avatar
            String pictureUrl = user.getPictureUrl();
            if (pictureUrl != null && !pictureUrl.isBlank()) {
                Image avatar = new Image(pictureUrl, user.getPreferredUsername());
                avatar.setWidth("32px");
                avatar.setHeight("32px");
                avatar.getStyle()
                        .set("border-radius", "50%")
                        .set("object-fit", "cover");
                right.add(avatar);
            }

            Span userName = new Span(user.getPreferredUsername());
            userName.getStyle()
                    .set("font-size", "0.9em")
                    .set("font-weight", "500")
                    .set("color", "var(--lumo-body-text-color)");
            right.add(userName);

            Button logoutBtn = new Button("Abmelden", new Icon(VaadinIcon.SIGN_OUT));
            logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            logoutBtn.addClickListener(e ->
                UI.getCurrent().getPage().executeJs("window.location.href='/logout'"));
            right.add(logoutBtn);
        });

        HorizontalLayout navbar = new HorizontalLayout(left, right);
        navbar.setWidthFull();
        navbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        navbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.setPadding(false);
        return navbar;
    }

    private Component createHeader() {
        // TODO Replace with real application logo and name
        var appLogo = VaadinIcon.CUBES.create();
        appLogo.setSize("48px");
        appLogo.setColor("green");

        var appName = new Span("My Application");
        appName.getStyle().setFontWeight(Style.FontWeight.BOLD);

        var header = new VerticalLayout(appLogo, appName);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        return header;
    }

    private SideNav createSideNav() {
        var nav = new SideNav();
        nav.addClassNames(LumoUtility.Margin.Horizontal.MEDIUM);
        MenuConfiguration.getMenuEntries().forEach(entry -> nav.addItem(createSideNavItem(entry)));
        return nav;
    }

    private SideNavItem createSideNavItem(MenuEntry menuEntry) {
        if (menuEntry.icon() != null) {
            return new SideNavItem(menuEntry.title(), menuEntry.path(), new Icon(menuEntry.icon()));
        } else {
            return new SideNavItem(menuEntry.title(), menuEntry.path());
        }
    }
}
