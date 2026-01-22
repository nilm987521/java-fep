package com.fep.bpmn.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Persistent settings for the FEP BPMN Editor plugin.
 */
@State(
        name = "FepBpmnSettings",
        storages = @Storage("FepBpmnSettings.xml")
)
@Service(Service.Level.APP)
public final class BpmnSettings implements PersistentStateComponent<BpmnSettings> {

    // Delegate scanning patterns
    private List<String> scanPatterns = new ArrayList<>(Arrays.asList(
            "**/delegate/**/*.java",
            "**/bpmn/**/*.java",
            "**/processor/**/*.java",
            "**/handler/**/*.java"
    ));

    // Required annotations for delegate detection
    private List<String> delegateAnnotations = new ArrayList<>(Arrays.asList(
            "@Component",
            "@Service",
            "@Named"
    ));

    // Interface that delegates must implement
    private String delegateInterface = "JavaDelegate";

    // Auto-scan on project open
    private boolean autoScan = true;

    // Show minimap in editor
    private boolean showMinimap = true;

    // Show palette by default
    private boolean showPalette = true;

    // Show properties panel by default
    private boolean showProperties = true;

    // Editor theme (auto, light, dark)
    private String editorTheme = "auto";

    // Grid snapping enabled
    private boolean gridSnapping = true;

    // Grid size in pixels
    private int gridSize = 10;

    public static BpmnSettings getInstance() {
        return ApplicationManager.getApplication().getService(BpmnSettings.class);
    }

    @Override
    public @Nullable BpmnSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull BpmnSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // Getters and setters

    public List<String> getScanPatterns() {
        return new ArrayList<>(scanPatterns);
    }

    public void setScanPatterns(List<String> scanPatterns) {
        this.scanPatterns = new ArrayList<>(scanPatterns);
    }

    public String getScanPatternsAsString() {
        return String.join("\n", scanPatterns);
    }

    public void setScanPatternsFromString(String patterns) {
        this.scanPatterns = new ArrayList<>(Arrays.asList(patterns.split("\n")));
    }

    public List<String> getDelegateAnnotations() {
        return new ArrayList<>(delegateAnnotations);
    }

    public void setDelegateAnnotations(List<String> delegateAnnotations) {
        this.delegateAnnotations = new ArrayList<>(delegateAnnotations);
    }

    public String getDelegateAnnotationsAsString() {
        return String.join(", ", delegateAnnotations);
    }

    public void setDelegateAnnotationsFromString(String annotations) {
        this.delegateAnnotations = new ArrayList<>(Arrays.asList(annotations.split("\\s*,\\s*")));
    }

    public String getDelegateInterface() {
        return delegateInterface;
    }

    public void setDelegateInterface(String delegateInterface) {
        this.delegateInterface = delegateInterface;
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        this.autoScan = autoScan;
    }

    public boolean isShowMinimap() {
        return showMinimap;
    }

    public void setShowMinimap(boolean showMinimap) {
        this.showMinimap = showMinimap;
    }

    public boolean isShowPalette() {
        return showPalette;
    }

    public void setShowPalette(boolean showPalette) {
        this.showPalette = showPalette;
    }

    public boolean isShowProperties() {
        return showProperties;
    }

    public void setShowProperties(boolean showProperties) {
        this.showProperties = showProperties;
    }

    public String getEditorTheme() {
        return editorTheme;
    }

    public void setEditorTheme(String editorTheme) {
        this.editorTheme = editorTheme;
    }

    public boolean isGridSnapping() {
        return gridSnapping;
    }

    public void setGridSnapping(boolean gridSnapping) {
        this.gridSnapping = gridSnapping;
    }

    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
    }
}
