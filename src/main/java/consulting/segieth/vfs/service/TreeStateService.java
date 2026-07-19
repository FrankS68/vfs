package consulting.segieth.vfs.service;

import java.util.HashSet;
import java.util.Set;

import com.vaadin.flow.server.VaadinSession;

/**
 * Speichert den Aufklapp-Zustand des Trees in der VaadinSession.
 * Kein Spring-Scope nötig — VaadinSession ist pro Browser-Tab.
 */
public class TreeStateService {

    private static final String KEY = "vfs.treeState";

    @SuppressWarnings("unchecked")
    private static Set<String> getSet() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) return new HashSet<>();
        Set<String> set = (Set<String>) session.getAttribute(KEY);
        if (set == null) {
            set = new HashSet<>();
            session.setAttribute(KEY, set);
        }
        return set;
    }

    public static Set<String> getExpandedAddresses() {
        return getSet();
    }

    public static void expand(String address) {
        if (address != null) getSet().add(address);
    }

    public static void collapse(String address) {
        if (address != null) getSet().remove(address);
    }

    public static boolean isExpanded(String address) {
        return address != null && getSet().contains(address);
    }

    public static void clear() {
        getSet().clear();
    }
}
