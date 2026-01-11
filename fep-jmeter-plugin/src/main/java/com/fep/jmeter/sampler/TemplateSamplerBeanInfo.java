package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TypeEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for TemplateSampler.
 *
 * <p>Defines the GUI elements for configuring the Template Sampler.
 * Supports:
 * <ul>
 *   <li>Template selection from TransactionTemplateConfig</li>
 *   <li>Connection settings for target server</li>
 *   <li>Variable values for template substitution</li>
 *   <li>Custom field overrides</li>
 * </ul>
 */
public class TemplateSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String CONNECTION_GROUP = "connection";
    private static final String TEMPLATE_GROUP = "template";
    private static final String VARIABLES_GROUP = "variables";
    private static final String ADVANCED_GROUP = "advanced";

    // Common template names for dropdown
    private static final String[] COMMON_TEMPLATES = {
        "Withdrawal",
        "Balance Inquiry",
        "Fund Transfer",
        "Bill Payment",
        "Sign On",
        "Sign Off",
        "Echo Test",
        "Reversal"
    };

    public TemplateSamplerBeanInfo() {
        super(TemplateSampler.class);

        // Create property groups
        createPropertyGroup(CONNECTION_GROUP, new String[]{
            TemplateSampler.TARGET_HOST,
            TemplateSampler.TARGET_PORT,
            TemplateSampler.CONNECTION_TIMEOUT,
            TemplateSampler.READ_TIMEOUT
        });

        createPropertyGroup(TEMPLATE_GROUP, new String[]{
            TemplateSampler.TEMPLATE_NAME,
            TemplateSampler.MTI_OVERRIDE
        });

        createPropertyGroup(VARIABLES_GROUP, new String[]{
            TemplateSampler.AMOUNT,
            TemplateSampler.CARD_NUMBER,
            TemplateSampler.TERMINAL_ID,
            TemplateSampler.BANK_CODE,
            TemplateSampler.SOURCE_ACCOUNT,
            TemplateSampler.DEST_ACCOUNT
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            TemplateSampler.CUSTOM_FIELDS
        });

        // ===== Connection properties =====
        PropertyDescriptor hostProp = property(TemplateSampler.TARGET_HOST);
        hostProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        hostProp.setValue(DEFAULT, "localhost");
        hostProp.setDisplayName("Target Host");
        hostProp.setShortDescription("Hostname or IP address of the target server.");

        PropertyDescriptor portProp = property(TemplateSampler.TARGET_PORT);
        portProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        portProp.setValue(DEFAULT, 8080);
        portProp.setDisplayName("Target Port");
        portProp.setShortDescription("TCP port of the target server.");

        PropertyDescriptor connTimeoutProp = property(TemplateSampler.CONNECTION_TIMEOUT);
        connTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        connTimeoutProp.setValue(DEFAULT, 10000);
        connTimeoutProp.setDisplayName("Connection Timeout (ms)");
        connTimeoutProp.setShortDescription("Maximum time to wait for connection in milliseconds.");

        PropertyDescriptor readTimeoutProp = property(TemplateSampler.READ_TIMEOUT);
        readTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        readTimeoutProp.setValue(DEFAULT, 30000);
        readTimeoutProp.setDisplayName("Read Timeout (ms)");
        readTimeoutProp.setShortDescription("Maximum time to wait for response in milliseconds.");

        // ===== Template properties =====
        PropertyDescriptor templateNameProp = property(TemplateSampler.TEMPLATE_NAME);
        templateNameProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        templateNameProp.setValue(DEFAULT, "Echo Test");
        templateNameProp.setValue(TAGS, COMMON_TEMPLATES);
        templateNameProp.setDisplayName("Template Name");
        templateNameProp.setShortDescription(
            "Name of the transaction template to use.\n" +
            "Templates are loaded from TransactionTemplateConfig.\n" +
            "Common templates: Withdrawal, Balance Inquiry, Fund Transfer, etc."
        );

        PropertyDescriptor mtiOverrideProp = property(TemplateSampler.MTI_OVERRIDE);
        mtiOverrideProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        mtiOverrideProp.setValue(DEFAULT, "");
        mtiOverrideProp.setDisplayName("MTI Override");
        mtiOverrideProp.setShortDescription(
            "Override the template's default MTI.\n" +
            "Leave empty to use the template's MTI.\n" +
            "Examples: 0100, 0200, 0400, 0800"
        );

        // ===== Variable properties =====
        PropertyDescriptor amountProp = property(TemplateSampler.AMOUNT);
        amountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        amountProp.setValue(DEFAULT, "");
        amountProp.setDisplayName("Amount");
        amountProp.setShortDescription(
            "Transaction amount for ${amount} variable.\n" +
            "Example: 100000 = $1,000.00"
        );

        PropertyDescriptor cardNumberProp = property(TemplateSampler.CARD_NUMBER);
        cardNumberProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        cardNumberProp.setValue(DEFAULT, "");
        cardNumberProp.setDisplayName("Card Number");
        cardNumberProp.setShortDescription(
            "Primary Account Number for ${cardNumber} or ${pan} variable.\n" +
            "Example: 4716123456781234"
        );

        PropertyDescriptor terminalIdProp = property(TemplateSampler.TERMINAL_ID);
        terminalIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        terminalIdProp.setValue(DEFAULT, "");
        terminalIdProp.setDisplayName("Terminal ID");
        terminalIdProp.setShortDescription(
            "Terminal identifier for ${terminalId} variable.\n" +
            "Example: ATM00001"
        );

        PropertyDescriptor bankCodeProp = property(TemplateSampler.BANK_CODE);
        bankCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        bankCodeProp.setValue(DEFAULT, "");
        bankCodeProp.setDisplayName("Bank Code");
        bankCodeProp.setShortDescription(
            "Acquiring institution ID for ${bankCode} variable.\n" +
            "Example: 012 for Taipei Fubon"
        );

        PropertyDescriptor sourceAccountProp = property(TemplateSampler.SOURCE_ACCOUNT);
        sourceAccountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sourceAccountProp.setValue(DEFAULT, "");
        sourceAccountProp.setDisplayName("Source Account");
        sourceAccountProp.setShortDescription(
            "Source account number for ${sourceAccount} variable.\n" +
            "Used in transfer transactions (Field 102)."
        );

        PropertyDescriptor destAccountProp = property(TemplateSampler.DEST_ACCOUNT);
        destAccountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        destAccountProp.setValue(DEFAULT, "");
        destAccountProp.setDisplayName("Destination Account");
        destAccountProp.setShortDescription(
            "Destination account number for ${destAccount} variable.\n" +
            "Used in transfer transactions (Field 103)."
        );

        // ===== Advanced properties =====
        PropertyDescriptor customFieldsProp = property(TemplateSampler.CUSTOM_FIELDS);
        customFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customFieldsProp.setValue(DEFAULT, "");
        customFieldsProp.setValue(TypeEditor.class.getName(), TypeEditor.TextAreaEditor);
        customFieldsProp.setDisplayName("Custom Fields");
        customFieldsProp.setShortDescription(
            "Additional ISO 8583 fields (highest priority, overrides template).\n" +
            "Format: field:value;field:value\n" +
            "Example: 48:Custom Data;62:Additional Info\n" +
            "Supports JMeter variables: ${varName}"
        );
    }
}
