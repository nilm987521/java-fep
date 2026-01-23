package com.fep.bpmn.editor;

import com.fep.bpmn.scanner.JavaDelegateScanner;
import com.fep.bpmn.scanner.model.JavaDelegate;
import com.fep.bpmn.services.DelegateRegistryService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * Custom file editor that displays BPMN diagrams using JCEF (Chromium Embedded Framework).
 * Uses bpmn-js for visual rendering and editing.
 */
public class BpmnEditor extends UserDataHolderBase implements FileEditor {

    private static final Logger LOG = Logger.getInstance(BpmnEditor.class);
    private static final Gson GSON = new Gson();

    private final Project project;
    private final VirtualFile file;
    private final JBCefBrowser browser;
    private final JBCefJSQuery jsQuery;
    private boolean isModified = false;
    private String currentXml;
    private Path tempWebviewDir;

    public BpmnEditor(@NotNull Project project, @NotNull VirtualFile file) {
        this.project = project;
        this.file = file;
        this.browser = new JBCefBrowser();

        // Create JS query handler for communication from webview to IDE
        this.jsQuery = JBCefJSQuery.create((JBCefBrowserBase) browser);

        // Set up message handler
        jsQuery.addHandler(this::handleWebviewMessage);

        // Load initial content
        loadBpmnContent();

        // Set up load handler
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (frame.isMain()) {
                    injectJavaScript();
                    sendBpmnToWebview();
                    // If delegates registry is empty, trigger a scan first
                    DelegateRegistryService registry = ApplicationManager.getApplication()
                            .getService(DelegateRegistryService.class);
                    if (registry.isEmpty()) {
                        LOG.info("Delegates registry is empty, triggering initial scan");
                        rescanDelegates();
                    } else {
                        sendDelegatesToWebview();
                    }
                }
            }
        }, browser.getCefBrowser());

        // Load the HTML page
        loadHtmlPage();
    }

    private void loadBpmnContent() {
        try {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) {
                currentXml = document.getText();
            } else {
                currentXml = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.error("Failed to load BPMN content", e);
            currentXml = getEmptyBpmnTemplate();
        }
    }

    private void loadHtmlPage() {
        try {
            // JCEF doesn't support jar: URLs, so we need to extract resources to a temp directory
            tempWebviewDir = extractWebviewResources();
            if (tempWebviewDir != null) {
                Path indexHtml = tempWebviewDir.resolve("index.html");
                String fileUrl = indexHtml.toUri().toString();
                LOG.info("Loading BPMN webview from: " + fileUrl);
                browser.loadURL(fileUrl);
            } else {
                LOG.warn("Webview resources extraction failed, loading fallback");
                browser.loadHTML(getFallbackHtml());
            }
        } catch (Exception e) {
            LOG.error("Failed to load webview HTML", e);
            browser.loadHTML(getFallbackHtml());
        }
    }

    /**
     * Extract webview resources from JAR to a temp directory.
     * JCEF cannot load resources directly from jar: URLs, so we need to extract them first.
     */
    private Path extractWebviewResources() {
        try {
            // Create temp directory for webview resources
            Path tempDir = Files.createTempDirectory("bpmn-editor-webview");

            // Read the resource listing file to know which files to extract
            // This file is generated during build to avoid hardcoding file names
            try (InputStream listingIs = getClass().getResourceAsStream("/webview/resource-listing.txt")) {
                if (listingIs != null) {
                    String listing = new String(listingIs.readAllBytes(), StandardCharsets.UTF_8);
                    String[] resources = listing.split("\n");
                    for (String resource : resources) {
                        resource = resource.trim();
                        if (!resource.isEmpty() && !resource.equals("resource-listing.txt")) {
                            extractResource(tempDir, resource);
                        }
                    }
                } else {
                    // Fallback: try to extract known essential files
                    LOG.warn("Resource listing file not found, using fallback list");
                    extractEssentialResources(tempDir);
                }
            }

            LOG.info("Webview resources extracted to: " + tempDir);
            return tempDir;
        } catch (IOException e) {
            LOG.error("Failed to extract webview resources", e);
            return null;
        }
    }

    private void extractResource(Path tempDir, String resource) {
        try (InputStream is = getClass().getResourceAsStream("/webview/" + resource)) {
            if (is != null) {
                Path targetPath = tempDir.resolve(resource);
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOG.debug("Extracted webview resource: " + resource);
            } else {
                LOG.warn("Webview resource not found: " + resource);
            }
        } catch (IOException e) {
            LOG.warn("Failed to extract resource: " + resource, e);
        }
    }

    private void extractEssentialResources(Path tempDir) {
        // Essential files that must exist for the webview to work
        String[] essentialFiles = {"index.html", "webview.js"};
        for (String file : essentialFiles) {
            extractResource(tempDir, file);
        }

        // Try to extract common font file patterns (these have hash suffixes from webpack)
        String[] fontExtensions = {".woff", ".woff2", ".ttf", ".eot", ".svg"};
        for (String ext : fontExtensions) {
            // We don't know the exact hash, so we try common patterns
            // This is a fallback - the resource-listing.txt approach is preferred
            LOG.debug("Skipping font extraction for " + ext + " - use resource-listing.txt for reliable extraction");
        }
    }

    private void injectJavaScript() {
        // Inject the callback function for webview-to-IDE communication
        String js = String.format(
                "window.ideCallback = function(message) { %s };",
                jsQuery.inject("message")
        );
        browser.getCefBrowser().executeJavaScript(js, browser.getCefBrowser().getURL(), 0);
    }

    private void sendBpmnToWebview() {
        String escapedXml = GSON.toJson(currentXml);
        String js = String.format("window.loadBpmn && window.loadBpmn(%s);", escapedXml);
        browser.getCefBrowser().executeJavaScript(js, browser.getCefBrowser().getURL(), 0);
    }

    private void sendDelegatesToWebview() {
        DelegateRegistryService registry = ApplicationManager.getApplication()
                .getService(DelegateRegistryService.class);
        List<JavaDelegate> delegates = registry.getDelegates();
        String delegatesJson = GSON.toJson(delegates);
        String js = String.format("window.setDelegates && window.setDelegates(%s);", delegatesJson);
        browser.getCefBrowser().executeJavaScript(js, browser.getCefBrowser().getURL(), 0);
    }

    /**
     * Handle messages from the webview.
     */
    private JBCefJSQuery.Response handleWebviewMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
                case "ready":
                    sendBpmnToWebview();
                    sendDelegatesToWebview();
                    break;

                case "update":
                    String xml = json.get("xml").getAsString();
                    handleBpmnUpdate(xml);
                    break;

                case "save":
                    saveBpmn();
                    break;

                case "requestDelegates":
                    rescanDelegates();
                    break;

                case "openDelegate":
                    String filePath = json.get("filePath").getAsString();
                    int lineNumber = json.has("lineNumber") ? json.get("lineNumber").getAsInt() : 1;
                    openDelegateSource(filePath, lineNumber);
                    break;

                case "exportSvg":
                    String svg = json.get("svg").getAsString();
                    handleExportSvg(svg);
                    break;

                case "exportPng":
                    String dataUrl = json.get("dataUrl").getAsString();
                    handleExportPng(dataUrl);
                    break;

                case "showInfo":
                    String infoMsg = json.get("message").getAsString();
                    showNotification(infoMsg, false);
                    break;

                case "showError":
                    String errorMsg = json.get("message").getAsString();
                    showNotification(errorMsg, true);
                    break;

                default:
                    LOG.warn("Unknown message type: " + type);
            }
        } catch (Exception e) {
            LOG.error("Error handling webview message", e);
            return new JBCefJSQuery.Response(null, 1, "Error: " + e.getMessage());
        }
        return new JBCefJSQuery.Response(null);
    }

    private void handleBpmnUpdate(String xml) {
        if (!Objects.equals(currentXml, xml)) {
            currentXml = xml;
            isModified = true;
            // Update the document
            ApplicationManager.getApplication().invokeLater(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    Document document = FileDocumentManager.getInstance().getDocument(file);
                    if (document != null) {
                        document.setText(xml);
                    }
                });
            });
        }
    }

    private void saveBpmn() {
        ApplicationManager.getApplication().invokeLater(() -> {
            ApplicationManager.getApplication().runWriteAction(() -> {
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document != null) {
                    FileDocumentManager.getInstance().saveDocument(document);
                    isModified = false;
                    showNotification("BPMN file saved successfully", false);
                }
            });
        });
    }

    private void rescanDelegates() {
        JavaDelegateScanner scanner = project.getService(JavaDelegateScanner.class);
        scanner.scanAsync().thenAccept(delegates -> {
            DelegateRegistryService registry = ApplicationManager.getApplication()
                    .getService(DelegateRegistryService.class);
            registry.setDelegates(delegates);
            sendDelegatesToWebview();
            showNotification("Found " + delegates.size() + " Java delegates", false);
        });
    }

    private void openDelegateSource(String filePath, int lineNumber) {
        ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile delegateFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .findFileByPath(filePath);
            if (delegateFile != null) {
                com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor =
                        new com.intellij.openapi.fileEditor.OpenFileDescriptor(project, delegateFile, lineNumber - 1, 0);
                descriptor.navigate(true);
            }
        });
    }

    private void handleExportSvg(String svg) {
        ApplicationManager.getApplication().invokeLater(() -> {
            String baseName = file.getNameWithoutExtension();
            com.intellij.openapi.fileChooser.FileSaverDescriptor descriptor =
                    new com.intellij.openapi.fileChooser.FileSaverDescriptor("Export as SVG", "Choose location to save SVG file", "svg");
            com.intellij.openapi.fileChooser.FileSaverDialog dialog =
                    com.intellij.openapi.fileChooser.FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
            com.intellij.openapi.vfs.VirtualFileWrapper wrapper = dialog.save(file.getParent(), baseName + ".svg");
            if (wrapper != null) {
                try {
                    java.nio.file.Files.writeString(wrapper.getFile().toPath(), svg, StandardCharsets.UTF_8);
                    showNotification("SVG exported successfully", false);
                } catch (IOException e) {
                    showNotification("Failed to export SVG: " + e.getMessage(), true);
                }
            }
        });
    }

    private void handleExportPng(String dataUrl) {
        ApplicationManager.getApplication().invokeLater(() -> {
            String baseName = file.getNameWithoutExtension();
            com.intellij.openapi.fileChooser.FileSaverDescriptor descriptor =
                    new com.intellij.openapi.fileChooser.FileSaverDescriptor("Export as PNG", "Choose location to save PNG file", "png");
            com.intellij.openapi.fileChooser.FileSaverDialog dialog =
                    com.intellij.openapi.fileChooser.FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
            com.intellij.openapi.vfs.VirtualFileWrapper wrapper = dialog.save(file.getParent(), baseName + ".png");
            if (wrapper != null) {
                try {
                    // Remove data URL prefix and decode base64
                    String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
                    byte[] imageData = java.util.Base64.getDecoder().decode(base64Data);
                    java.nio.file.Files.write(wrapper.getFile().toPath(), imageData);
                    showNotification("PNG exported successfully", false);
                } catch (Exception e) {
                    showNotification("Failed to export PNG: " + e.getMessage(), true);
                }
            }
        });
    }

    private void showNotification(String message, boolean isError) {
        ApplicationManager.getApplication().invokeLater(() -> {
            com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("FEP BPMN Editor")
                    .createNotification(
                            message,
                            isError ? com.intellij.notification.NotificationType.ERROR
                                    : com.intellij.notification.NotificationType.INFORMATION
                    )
                    .notify(project);
        });
    }

    // Public methods for actions
    public void executeCommand(String command) {
        String js = String.format("window.executeCommand && window.executeCommand('%s');", command);
        browser.getCefBrowser().executeJavaScript(js, browser.getCefBrowser().getURL(), 0);
    }

    public void zoomIn() {
        executeCommand("zoomIn");
    }

    public void zoomOut() {
        executeCommand("zoomOut");
    }

    public void fitToScreen() {
        executeCommand("fitToScreen");
    }

    public void undo() {
        executeCommand("undo");
    }

    public void redo() {
        executeCommand("redo");
    }

    public void exportSvg() {
        executeCommand("exportSvg");
    }

    public void exportPng() {
        executeCommand("exportPng");
    }

    public void refreshDelegates() {
        rescanDelegates();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return browser.getComponent();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return browser.getComponent();
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() {
        return "BPMN Editor";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
        // Optional: restore editor state
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        // Not implemented
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        // Not implemented
    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    public void dispose() {
        Disposer.dispose(jsQuery);
        Disposer.dispose(browser);
        cleanupTempWebviewDir();
    }

    /**
     * Clean up the temporary webview directory when the editor is disposed.
     */
    private void cleanupTempWebviewDir() {
        if (tempWebviewDir != null) {
            try {
                // Delete all files in the temp directory
                try (var files = Files.list(tempWebviewDir)) {
                    files.forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException e) {
                            LOG.warn("Failed to delete temp file: " + file, e);
                        }
                    });
                }
                // Delete the directory itself
                Files.deleteIfExists(tempWebviewDir);
                LOG.debug("Cleaned up temp webview directory: " + tempWebviewDir);
            } catch (IOException e) {
                LOG.warn("Failed to cleanup temp webview directory", e);
            }
        }
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    private String getEmptyBpmnTemplate() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn"
                                  exporter="FEP BPMN Editor"
                                  exporterVersion="1.0.0">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1" name="Start">
                      <bpmn:outgoing>Flow_1</bpmn:outgoing>
                    </bpmn:startEvent>
                    <bpmn:endEvent id="EndEvent_1" name="End">
                      <bpmn:incoming>Flow_1</bpmn:incoming>
                    </bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
                      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
                        <dc:Bounds x="152" y="102" width="36" height="36"/>
                        <bpmndi:BPMNLabel>
                          <dc:Bounds x="158" y="145" width="25" height="14"/>
                        </bpmndi:BPMNLabel>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
                        <dc:Bounds x="432" y="102" width="36" height="36"/>
                        <bpmndi:BPMNLabel>
                          <dc:Bounds x="440" y="145" width="20" height="14"/>
                        </bpmndi:BPMNLabel>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
                        <di:waypoint xmlns:di="http://www.omg.org/spec/DD/20100524/DI" x="188" y="120"/>
                        <di:waypoint xmlns:di="http://www.omg.org/spec/DD/20100524/DI" x="432" y="120"/>
                      </bpmndi:BPMNEdge>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """;
    }

    private String getFallbackHtml() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            height: 100vh;
                            margin: 0;
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            background: #1e1e1e;
                            color: #cccccc;
                        }
                        .loading {
                            text-align: center;
                        }
                        .spinner {
                            width: 50px;
                            height: 50px;
                            border: 3px solid #333;
                            border-top-color: #007acc;
                            border-radius: 50%;
                            animation: spin 1s linear infinite;
                        }
                        @keyframes spin {
                            to { transform: rotate(360deg); }
                        }
                    </style>
                </head>
                <body>
                    <div class="loading">
                        <div class="spinner"></div>
                        <p>Loading BPMN Editor...</p>
                        <p style="font-size: 12px; color: #888;">Please rebuild the plugin with webview resources.</p>
                    </div>
                </body>
                </html>
                """;
    }
}
