package com.fep.jmeter.config;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TypeEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for TransactionTemplateConfig.
 *
 * <p>Defines the GUI elements for configuring transaction templates.
 */
public class TransactionTemplateConfigBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String SOURCE_GROUP = "source";
    private static final String OPTIONS_GROUP = "options";

    // Template source options
    private static final String[] TEMPLATE_SOURCES = {
        TransactionTemplateConfig.SOURCE_COMMON,
        TransactionTemplateConfig.SOURCE_FILE,
        TransactionTemplateConfig.SOURCE_INLINE
    };

    public TransactionTemplateConfigBeanInfo() {
        super(TransactionTemplateConfig.class);

        // Create property groups
        createPropertyGroup(SOURCE_GROUP, new String[]{
            TransactionTemplateConfig.TEMPLATE_SOURCE,
            TransactionTemplateConfig.TEMPLATE_FILE,
            TransactionTemplateConfig.INLINE_TEMPLATES
        });

        createPropertyGroup(OPTIONS_GROUP, new String[]{
            TransactionTemplateConfig.USE_COMMON_TEMPLATES,
            TransactionTemplateConfig.AUTO_GENERATE_STAN,
            TransactionTemplateConfig.AUTO_GENERATE_TIMESTAMP
        });

        // Template source property
        PropertyDescriptor sourceProp = property(TransactionTemplateConfig.TEMPLATE_SOURCE);
        sourceProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sourceProp.setValue(DEFAULT, TransactionTemplateConfig.SOURCE_COMMON);
        sourceProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        sourceProp.setValue(TAGS, TEMPLATE_SOURCES);
        sourceProp.setDisplayName("Template Source");
        sourceProp.setShortDescription(
            "Source of transaction templates:\n" +
            "COMMON - Use predefined common templates\n" +
            "FILE - Load from JSON file\n" +
            "INLINE - Define templates inline as JSON"
        );

        // Template file property
        PropertyDescriptor fileProp = property(TransactionTemplateConfig.TEMPLATE_FILE);
        fileProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fileProp.setValue(DEFAULT, "");
        fileProp.setDisplayName("Template File");
        fileProp.setShortDescription(
            "Path to JSON file containing transaction templates.\n" +
            "Used when Template Source is 'FILE'."
        );

        // Inline templates property
        PropertyDescriptor inlineProp = property(TransactionTemplateConfig.INLINE_TEMPLATES);
        inlineProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        inlineProp.setValue(DEFAULT, "");
        inlineProp.setValue(TypeEditor.class.getName(), TypeEditor.TextAreaEditor);
        inlineProp.setDisplayName("Inline Templates (JSON)");
        inlineProp.setShortDescription(
            "JSON array of transaction templates.\n" +
            "Used when Template Source is 'INLINE'.\n" +
            "Example:\n" +
            "[\n" +
            "  {\n" +
            "    \"name\": \"Custom Withdrawal\",\n" +
            "    \"mti\": \"0200\",\n" +
            "    \"fields\": {\"3\": \"010000\", \"4\": \"${amount}\"}\n" +
            "  }\n" +
            "]"
        );

        // Use common templates property
        PropertyDescriptor useCommonProp = property(TransactionTemplateConfig.USE_COMMON_TEMPLATES);
        useCommonProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        useCommonProp.setValue(DEFAULT, Boolean.TRUE);
        useCommonProp.setDisplayName("Include Common Templates");
        useCommonProp.setShortDescription(
            "Add common templates (Withdrawal, Balance Inquiry, Transfer, etc.)\n" +
            "in addition to custom templates from file or inline."
        );

        // Auto-generate STAN property
        PropertyDescriptor autoStanProp = property(TransactionTemplateConfig.AUTO_GENERATE_STAN);
        autoStanProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        autoStanProp.setValue(DEFAULT, Boolean.TRUE);
        autoStanProp.setDisplayName("Auto-generate STAN");
        autoStanProp.setShortDescription(
            "Automatically generate System Trace Audit Number.\n" +
            "Use ${stan} in template fields to reference."
        );

        // Auto-generate timestamp property
        PropertyDescriptor autoTimeProp = property(TransactionTemplateConfig.AUTO_GENERATE_TIMESTAMP);
        autoTimeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        autoTimeProp.setValue(DEFAULT, Boolean.TRUE);
        autoTimeProp.setDisplayName("Auto-generate Timestamp");
        autoTimeProp.setShortDescription(
            "Automatically generate date/time values.\n" +
            "Available variables: ${time}, ${date}, ${datetime}"
        );
    }
}
