package com.fep.bpmn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Action to create a new BPMN file.
 */
public class NewBpmnFileAction extends AnAction {

    private static final String DEFAULT_BPMN_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                              xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                              xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                              xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                              id="Definitions_%s"
                              targetNamespace="http://bpmn.io/schema/bpmn"
                              exporter="FEP BPMN Editor"
                              exporterVersion="1.0.0">
              <bpmn:process id="%s" name="%s" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="Start">
                  <bpmn:outgoing>Flow_1</bpmn:outgoing>
                </bpmn:startEvent>
                <bpmn:endEvent id="EndEvent_1" name="End">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                </bpmn:endEvent>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1"/>
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="%s">
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
                    <di:waypoint x="188" y="120"/>
                    <di:waypoint x="432" y="120"/>
                  </bpmndi:BPMNEdge>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>
            """;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // Ask for file name
        String fileName = Messages.showInputDialog(
                project,
                "Enter BPMN file name:",
                "New BPMN File",
                Messages.getQuestionIcon(),
                "new-process",
                null
        );

        if (fileName == null || fileName.isBlank()) return;

        // Ensure .bpmn extension
        if (!fileName.endsWith(".bpmn")) {
            fileName = fileName + ".bpmn";
        }

        // Get base path
        String basePath = project.getBasePath();
        if (basePath == null) {
            Messages.showErrorDialog(project, "Cannot determine project path", "Error");
            return;
        }

        // Create file in src/main/resources/bpmn directory if it exists, otherwise in project root
        Path targetDir = Path.of(basePath, "src", "main", "resources", "bpmn");
        if (!Files.exists(targetDir)) {
            targetDir = Path.of(basePath);
        }

        Path filePath = targetDir.resolve(fileName);

        // Check if file exists
        if (Files.exists(filePath)) {
            int result = Messages.showYesNoDialog(
                    project,
                    "File already exists. Overwrite?",
                    "File Exists",
                    Messages.getWarningIcon()
            );
            if (result != Messages.YES) return;
        }

        // Generate process ID from filename
        String processId = fileName.replace(".bpmn", "")
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        String processName = processId.replace("-", " ").replace("_", " ");

        String content = String.format(DEFAULT_BPMN_TEMPLATE,
                System.currentTimeMillis(),
                processId,
                processName,
                processId
        );

        // Create the file
        String finalFileName = fileName;
        Path finalTargetDir = targetDir;
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                Files.createDirectories(finalTargetDir);
                Files.writeString(filePath, content, StandardCharsets.UTF_8);

                // Refresh and open
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.toString());
                if (virtualFile != null) {
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);
                }
            } catch (IOException ex) {
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showErrorDialog(project, "Failed to create file: " + ex.getMessage(), "Error")
                );
            }
        });
    }
}
