package com.fep.bpmn.editor;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * File type for BPMN 2.0 process files.
 * BPMN files are XML-based with the .bpmn extension.
 */
public class BpmnFileType extends LanguageFileType {

    public static final BpmnFileType INSTANCE = new BpmnFileType();

    private BpmnFileType() {
        super(XMLLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "BPMN";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "BPMN 2.0 Process File";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "bpmn";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return IconLoader.getIcon("/icons/bpmn.svg", BpmnFileType.class);
    }
}
