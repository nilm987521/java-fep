package com.fep.bpmn.actions;

import com.fep.bpmn.editor.BpmnEditor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Action to undo the last change in the BPMN editor.
 */
public class UndoAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        FileEditor[] editors = FileEditorManager.getInstance(project).getSelectedEditors();
        for (FileEditor editor : editors) {
            if (editor instanceof BpmnEditor bpmnEditor) {
                bpmnEditor.undo();
                break;
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean enabled = false;

        if (project != null) {
            FileEditor[] editors = FileEditorManager.getInstance(project).getSelectedEditors();
            for (FileEditor editor : editors) {
                if (editor instanceof BpmnEditor) {
                    enabled = true;
                    break;
                }
            }
        }

        e.getPresentation().setEnabled(enabled);
    }
}
