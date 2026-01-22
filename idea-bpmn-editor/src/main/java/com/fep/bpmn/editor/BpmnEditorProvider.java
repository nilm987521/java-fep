package com.fep.bpmn.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Provider for the custom BPMN visual editor.
 * This provider creates BpmnEditor instances for .bpmn files.
 */
public class BpmnEditorProvider implements FileEditorProvider, DumbAware {

    private static final String EDITOR_TYPE_ID = "fep-bpmn-editor";

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return file.getExtension() != null && file.getExtension().equalsIgnoreCase("bpmn");
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new BpmnEditor(project, file);
    }

    @Override
    public @NotNull @NonNls String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        // Use PLACE_BEFORE_DEFAULT_EDITOR to make the visual editor the primary option
        return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
    }
}
