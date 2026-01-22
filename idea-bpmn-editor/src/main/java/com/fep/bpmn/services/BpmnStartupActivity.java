package com.fep.bpmn.services;

import com.fep.bpmn.scanner.JavaDelegateScanner;
import com.fep.bpmn.settings.BpmnSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Startup activity that performs initial delegate scanning when a project is opened.
 */
public class BpmnStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(BpmnStartupActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        BpmnSettings settings = ApplicationManager.getApplication().getService(BpmnSettings.class);

        if (settings.isAutoScan()) {
            LOG.info("Auto-scanning for Java delegates in project: " + project.getName());

            JavaDelegateScanner scanner = project.getService(JavaDelegateScanner.class);
            scanner.scanAsync().thenAccept(delegates -> {
                DelegateRegistryService registry = ApplicationManager.getApplication()
                        .getService(DelegateRegistryService.class);
                registry.setDelegates(delegates);
                LOG.info("Auto-scan complete: found " + delegates.size() + " delegates");
            }).exceptionally(throwable -> {
                LOG.warn("Auto-scan failed", throwable);
                return null;
            });
        }

        return Unit.INSTANCE;
    }
}
