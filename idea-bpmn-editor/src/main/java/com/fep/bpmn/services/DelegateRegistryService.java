package com.fep.bpmn.services;

import com.fep.bpmn.scanner.model.DelegateCategory;
import com.fep.bpmn.scanner.model.JavaDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.messages.Topic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application-level service that maintains a registry of discovered JavaDelegates.
 * Provides methods for querying delegates by category, name, etc.
 */
@Service(Service.Level.APP)
public final class DelegateRegistryService {

    // Topic for delegate registry change notifications
    public static final Topic<DelegateRegistryListener> DELEGATES_CHANGED_TOPIC =
            Topic.create("BPMN Delegates Changed", DelegateRegistryListener.class);

    private final Map<String, JavaDelegate> delegatesByName = new ConcurrentHashMap<>();
    private final Map<String, JavaDelegate> delegatesByClass = new ConcurrentHashMap<>();
    private volatile long lastScanTime = 0;

    public DelegateRegistryService() {
    }

    /**
     * Set the list of delegates, replacing any existing entries.
     */
    public void setDelegates(List<JavaDelegate> delegates) {
        delegatesByName.clear();
        delegatesByClass.clear();

        for (JavaDelegate delegate : delegates) {
            delegatesByName.put(delegate.getName(), delegate);
            delegatesByClass.put(delegate.getFullyQualifiedName(), delegate);
        }

        lastScanTime = System.currentTimeMillis();

        // Notify listeners
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(DELEGATES_CHANGED_TOPIC)
                .delegatesChanged(delegates);
    }

    /**
     * Get all registered delegates.
     */
    public List<JavaDelegate> getDelegates() {
        return new ArrayList<>(delegatesByName.values());
    }

    /**
     * Get delegates sorted by category and name.
     */
    public List<JavaDelegate> getDelegatesSorted() {
        return delegatesByName.values().stream()
                .sorted(Comparator
                        .comparing((JavaDelegate d) -> d.getCategory().ordinal())
                        .thenComparing(JavaDelegate::getName))
                .collect(Collectors.toList());
    }

    /**
     * Get delegates grouped by category.
     */
    public Map<DelegateCategory, List<JavaDelegate>> getDelegatesByCategory() {
        return delegatesByName.values().stream()
                .collect(Collectors.groupingBy(JavaDelegate::getCategory));
    }

    /**
     * Get delegates for a specific category.
     */
    public List<JavaDelegate> getDelegatesForCategory(DelegateCategory category) {
        return delegatesByName.values().stream()
                .filter(d -> d.getCategory() == category)
                .sorted(Comparator.comparing(JavaDelegate::getName))
                .collect(Collectors.toList());
    }

    /**
     * Find a delegate by its bean name.
     */
    public Optional<JavaDelegate> findByName(String name) {
        return Optional.ofNullable(delegatesByName.get(name));
    }

    /**
     * Find a delegate by its fully qualified class name.
     */
    public Optional<JavaDelegate> findByClassName(String className) {
        return Optional.ofNullable(delegatesByClass.get(className));
    }

    /**
     * Search delegates by partial name match.
     */
    public List<JavaDelegate> searchByName(String query) {
        String lowerQuery = query.toLowerCase();
        return delegatesByName.values().stream()
                .filter(d -> d.getName().toLowerCase().contains(lowerQuery)
                        || d.getDisplayName().toLowerCase().contains(lowerQuery)
                        || d.getDescription().toLowerCase().contains(lowerQuery))
                .sorted(Comparator.comparing(JavaDelegate::getName))
                .collect(Collectors.toList());
    }

    /**
     * Get the count of delegates.
     */
    public int getDelegateCount() {
        return delegatesByName.size();
    }

    /**
     * Get the timestamp of the last scan.
     */
    public long getLastScanTime() {
        return lastScanTime;
    }

    /**
     * Check if the registry has been populated.
     */
    public boolean isEmpty() {
        return delegatesByName.isEmpty();
    }

    /**
     * Clear all delegates from the registry.
     */
    public void clear() {
        delegatesByName.clear();
        delegatesByClass.clear();
    }

    /**
     * Listener interface for delegate registry changes.
     */
    public interface DelegateRegistryListener {
        void delegatesChanged(List<JavaDelegate> delegates);
    }
}
