package consulting.segieth.vfs.ui.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Leere Login-Route — leitet sofort zur statischen login.html weiter.
 * witch-auth erwartet dass loginView("login") auflösbar ist.
 */
@Route("login")
@PageTitle("Anmelden")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        UI.getCurrent().getPage().setLocation("/login.html");
    }
}
