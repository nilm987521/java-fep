package com.fep.bpmn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Context menu action to open the source code of a delegate.
 * This is used in the BPMN editor context menu when a ServiceTask is selected.
 */
public class OpenDelegateSourceAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Implementation is handled by the BpmnEditor through the webview
        // This action is primarily for the context menu visibility
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Only enable if we have a project
        Project project = e.getProject();
        e.getPresentation().setEnabled(project != null);
    }
}
