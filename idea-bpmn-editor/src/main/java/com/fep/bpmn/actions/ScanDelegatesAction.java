package com.fep.bpmn.actions;

import com.fep.bpmn.scanner.JavaDelegateScanner;
import com.fep.bpmn.services.DelegateRegistryService;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Action to scan the project for JavaDelegate implementations.
 */
public class ScanDelegatesAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        JavaDelegateScanner scanner = project.getService(JavaDelegateScanner.class);
        scanner.scanAsync().thenAccept(delegates -> {
            DelegateRegistryService registry = ApplicationManager.getApplication()
                    .getService(DelegateRegistryService.class);
            registry.setDelegates(delegates);

            NotificationGroupManager.getInstance()
                    .getNotificationGroup("FEP BPMN Editor")
                    .createNotification(
                            "Found " + delegates.size() + " Java delegates",
                            NotificationType.INFORMATION
                    )
                    .notify(project);
        }).exceptionally(throwable -> {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("FEP BPMN Editor")
                    .createNotification(
                            "Failed to scan delegates: " + throwable.getMessage(),
                            NotificationType.ERROR
                    )
                    .notify(project);
            return null;
        });
    }
}
