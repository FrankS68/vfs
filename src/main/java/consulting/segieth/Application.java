package consulting.segieth;

import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.spring.annotation.EnableVaadin;

@SpringBootApplication(scanBasePackages = {
    "consulting.segieth",
    "de.witchcafe.auth"
})
@EntityScan(basePackages = {
    "consulting.segieth",
    "de.witchcafe.auth.domain"
})
@EnableJpaRepositories(basePackages = {
    "consulting.segieth",
    "de.witchcafe.auth.domain"
})
// Vaadin-Routen sonst nur im Paket dieser Klasse; witch-auth bringt /login mit
@EnableVaadin({"consulting.segieth", "de.witchcafe.auth"})
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@StyleSheet("styles.css")
@EnableAsync
@Push
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        if (DesktopLauncher.handOverToRunningInstance(args)) {
            return;
        }
        SpringApplication.run(Application.class, args);
    }
}
