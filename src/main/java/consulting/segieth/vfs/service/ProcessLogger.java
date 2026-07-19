package consulting.segieth.vfs.service;

import java.time.Duration;
import java.time.Instant;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;

import org.springframework.stereotype.Service;

/**
 * Zeigt persistente Prozess-Notifications mit Start, Ende und Laufzeit.
 * Muss aus dem UI-Thread heraus aufgerufen werden (oder via ui.access()).
 */
@Service
public class ProcessLogger {

    public enum ProcessType {
        SCAN("🔍 Scan"),
        REFRESH("🔄 Aktualisieren"),
        MOVE("📁 Verschieben"),
        HASH("🔑 Hashes berechnen"),
        THUMBNAIL("🖼 Thumbnails"),
        TRASH("🗑 In Trash");

        final String label;
        ProcessType(String label) { this.label = label; }
    }

    /**
     * Startet eine Prozess-Notification und gibt ein Handle zurück
     * mit dem der Prozess als abgeschlossen oder fehlgeschlagen markiert werden kann.
     */
    public ProcessHandle start(UI ui, ProcessType type, String location) {
        Instant startTime = Instant.now();
        ProcessHandle handle = new ProcessHandle(ui, type, location, startTime);
        ui.access(handle::showStarted);
        return handle;
    }

    public static class ProcessHandle {
        private final UI ui;
        private final ProcessType type;
        private final String location;
        private final Instant startTime;
        private Notification notification;

        ProcessHandle(UI ui, ProcessType type, String location, Instant startTime) {
            this.ui = ui;
            this.type = type;
            this.location = location;
            this.startTime = startTime;
        }

        void showStarted() {
            notification = new Notification();
            notification.setPosition(Position.TOP_END);
            notification.setDuration(0); // persistent bis wir sie schließen

            VerticalLayout content = buildContent(
                    "⏳ " + type.label,
                    location,
                    "läuft...",
                    "#666");

            notification.add(content);
            notification.open();
        }

        public void success(String result) {
            finishWith("✅ " + type.label, result, NotificationVariant.LUMO_SUCCESS, "#2e7d32");
        }

        public void error(String message) {
            finishWith("❌ " + type.label, message, NotificationVariant.LUMO_ERROR, "#c62828");
        }

        private void finishWith(String title, String result, NotificationVariant variant, String color) {
            long ms = Duration.between(startTime, Instant.now()).toMillis();
            String duration = ms < 1000 ? ms + " ms" : String.format("%.1f s", ms / 1000.0);

            ui.access(() -> {
                if (notification != null) notification.close();

                Notification newNotification = new Notification();
                newNotification.setPosition(Position.TOP_END);
                newNotification.setDuration(6000);
                newNotification.addThemeVariants(variant);

                VerticalLayout content = buildContent(title, location, result + "  (" + duration + ")", color);
                newNotification.add(content);
                newNotification.open();
            });
        }

        private VerticalLayout buildContent(String title, String loc, String detail, String detailColor) {
            Span titleSpan = new Span(title);
            titleSpan.getStyle().set("font-weight", "bold").set("font-size", "0.95em");

            Span locSpan = new Span(shortenPath(loc));
            locSpan.getStyle().set("font-size", "0.8em").set("color", "#555");

            Span detailSpan = new Span(detail);
            detailSpan.getStyle().set("font-size", "0.8em").set("color", detailColor);

            VerticalLayout layout = new VerticalLayout(titleSpan, locSpan, detailSpan);
            layout.setPadding(false);
            layout.setSpacing(false);
            layout.getStyle().set("gap", "2px");
            return layout;
        }

        private String shortenPath(String path) {
            if (path == null || path.length() <= 50) return path;
            // Zeige nur die letzten zwei Segmente
            String[] parts = path.replace("\\", "/").split("/");
            if (parts.length >= 2) {
                return "..." + "/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
            }
            return "..." + path.substring(path.length() - 47);
        }
    }
}
