package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.GenericTestBeanCustomizer;
import org.apache.jmeter.testbeans.gui.TextAreaEditor;
import org.apache.jmeter.testbeans.gui.TypeEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for BankCoreServerSampler.
 *
 * <p>Defines the GUI elements for configuring the Bank Core System Dual-Channel Server Simulator.
 */
public class BankCoreServerSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String SERVER_GROUP = "server";
    private static final String RESPONSE_GROUP = "response";
    private static final String BALANCE_GROUP = "balance";
    private static final String VALIDATION_GROUP = "validation";
    private static final String ROUTING_GROUP = "routing";
    private static final String ADVANCED_GROUP = "advanced";

    // Common response codes
    private static final String[] RESPONSE_CODES = {
        "00", "01", "03", "04", "05", "12", "13", "14",
        "30", "41", "43", "51", "54", "55", "57", "58",
        "61", "65", "75", "91", "96"
    };

    public BankCoreServerSamplerBeanInfo() {
        super(BankCoreServerSampler.class);

        // Create property groups
        createPropertyGroup(SERVER_GROUP, new String[]{
            BankCoreServerSampler.SEND_PORT,
            BankCoreServerSampler.RECEIVE_PORT,
            BankCoreServerSampler.SAMPLE_INTERVAL
        });

        createPropertyGroup(RESPONSE_GROUP, new String[]{
            BankCoreServerSampler.DEFAULT_RESPONSE_CODE,
            BankCoreServerSampler.RESPONSE_DELAY
        });

        createPropertyGroup(BALANCE_GROUP, new String[]{
            BankCoreServerSampler.AVAILABLE_BALANCE,
            BankCoreServerSampler.LEDGER_BALANCE
        });

        createPropertyGroup(VALIDATION_GROUP, new String[]{
            BankCoreServerSampler.ENABLE_VALIDATION,
            BankCoreServerSampler.VALIDATION_ERROR_CODE,
            BankCoreServerSampler.VALIDATION_RULES
        });

        createPropertyGroup(ROUTING_GROUP, new String[]{
            BankCoreServerSampler.ENABLE_FEP_ID_ROUTING,
            BankCoreServerSampler.FEP_ID_FIELD
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            BankCoreServerSampler.RESPONSE_RULES,
            BankCoreServerSampler.CUSTOM_RESPONSE_FIELDS
        });

        // Server properties
        PropertyDescriptor sendPortProp = property(BankCoreServerSampler.SEND_PORT);
        sendPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sendPortProp.setValue(DEFAULT, 9100);
        sendPortProp.setDisplayName("Send Port");
        sendPortProp.setShortDescription("TCP port for receiving requests from FEP (Send Channel). Use 0 for random port.");

        PropertyDescriptor receivePortProp = property(BankCoreServerSampler.RECEIVE_PORT);
        receivePortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        receivePortProp.setValue(DEFAULT, 9101);
        receivePortProp.setDisplayName("Receive Port");
        receivePortProp.setShortDescription("TCP port for sending responses to FEP (Receive Channel). Use 0 for random port.");

        PropertyDescriptor sampleIntervalProp = property(BankCoreServerSampler.SAMPLE_INTERVAL);
        sampleIntervalProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sampleIntervalProp.setValue(DEFAULT, 1000);
        sampleIntervalProp.setDisplayName("Sample Interval (ms)");
        sampleIntervalProp.setShortDescription("Interval between statistics collection in milliseconds.");

        // Response properties
        PropertyDescriptor responseCodeProp = property(BankCoreServerSampler.DEFAULT_RESPONSE_CODE);
        responseCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseCodeProp.setValue(DEFAULT, "00");
        responseCodeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        responseCodeProp.setValue(NOT_OTHER, Boolean.TRUE);
        responseCodeProp.setValue(TAGS, RESPONSE_CODES);
        responseCodeProp.setDisplayName("Default Response Code");
        responseCodeProp.setShortDescription("Default ISO 8583 response code (Field 39).");

        PropertyDescriptor responseDelayProp = property(BankCoreServerSampler.RESPONSE_DELAY);
        responseDelayProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseDelayProp.setValue(DEFAULT, 0);
        responseDelayProp.setDisplayName("Response Delay (ms)");
        responseDelayProp.setShortDescription("Simulated processing delay before sending response (core system processing time).");

        // Balance properties
        PropertyDescriptor availableBalanceProp = property(BankCoreServerSampler.AVAILABLE_BALANCE);
        availableBalanceProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        availableBalanceProp.setValue(DEFAULT, "100000");
        availableBalanceProp.setDisplayName("Available Balance");
        availableBalanceProp.setShortDescription("Available account balance for Field 54 in cents (e.g., 100000 = $1,000.00).");

        PropertyDescriptor ledgerBalanceProp = property(BankCoreServerSampler.LEDGER_BALANCE);
        ledgerBalanceProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        ledgerBalanceProp.setValue(DEFAULT, "150000");
        ledgerBalanceProp.setDisplayName("Ledger Balance");
        ledgerBalanceProp.setShortDescription("Ledger account balance for Field 54 in cents (e.g., 150000 = $1,500.00).");

        // Validation properties
        PropertyDescriptor enableValidationProp = property(BankCoreServerSampler.ENABLE_VALIDATION);
        enableValidationProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        enableValidationProp.setValue(DEFAULT, Boolean.TRUE);
        enableValidationProp.setDisplayName("Enable Validation");
        enableValidationProp.setShortDescription("Enable message validation against rules.");

        PropertyDescriptor validationErrorCodeProp = property(BankCoreServerSampler.VALIDATION_ERROR_CODE);
        validationErrorCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        validationErrorCodeProp.setValue(DEFAULT, "30");
        validationErrorCodeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        validationErrorCodeProp.setValue(NOT_OTHER, Boolean.TRUE);
        validationErrorCodeProp.setValue(TAGS, RESPONSE_CODES);
        validationErrorCodeProp.setDisplayName("Validation Error Code");
        validationErrorCodeProp.setShortDescription("Response code when validation fails (default: 30 = Format Error).");

        PropertyDescriptor validationRulesProp = property(BankCoreServerSampler.VALIDATION_RULES);
        validationRulesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        validationRulesProp.setValue(DEFAULT, "");
        validationRulesProp.setPropertyEditorClass(TextAreaEditor.class);
        validationRulesProp.setValue(GenericTestBeanCustomizer.TEXT_LANGUAGE, "json");
        validationRulesProp.setDisplayName("Validation Rules (JSON)");
        validationRulesProp.setShortDescription(
            "JSON validation rules configuration.\n\n" +
            "Example:\n" +
            "{\n" +
            "  \"globalRules\": {\n" +
            "    \"required\": [2, 3, 4, 11],\n" +
            "    \"format\": {\"2\": \"N(13-19)\", \"3\": \"N(6)\"},\n" +
            "    \"value\": {\"3\": [\"010000\", \"400000\"]},\n" +
            "    \"length\": {\"4\": 12},\n" +
            "    \"pattern\": {\"37\": \"^[A-Z0-9]{12}$\"}\n" +
            "  }\n" +
            "}"
        );

        // Routing properties
        PropertyDescriptor enableRoutingProp = property(BankCoreServerSampler.ENABLE_FEP_ID_ROUTING);
        enableRoutingProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        enableRoutingProp.setValue(DEFAULT, Boolean.TRUE);
        enableRoutingProp.setDisplayName("Enable FEP ID Routing");
        enableRoutingProp.setShortDescription("Route responses to FEP clients based on FEP ID field.");

        PropertyDescriptor fepIdFieldProp = property(BankCoreServerSampler.FEP_ID_FIELD);
        fepIdFieldProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fepIdFieldProp.setValue(DEFAULT, 32);
        fepIdFieldProp.setDisplayName("FEP ID Field");
        fepIdFieldProp.setShortDescription("Field number containing FEP ID (default: 32 = Acquiring Institution ID).");

        // Advanced properties
        PropertyDescriptor responseRulesProp = property(BankCoreServerSampler.RESPONSE_RULES);
        responseRulesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseRulesProp.setValue(DEFAULT, "");
        responseRulesProp.setPropertyEditorClass(TextAreaEditor.class);
        responseRulesProp.setValue(GenericTestBeanCustomizer.TEXT_LANGUAGE, "json");
        responseRulesProp.setDisplayName("Response Rules (JSON)");
        responseRulesProp.setShortDescription(
            "JSON response rules by processing code.\n\n" +
            "Example:\n" +
            "{\"010000\": \"00\", \"400000\": \"51\", \"310000\": \"00\"}"
        );

        PropertyDescriptor customFieldsProp = property(BankCoreServerSampler.CUSTOM_RESPONSE_FIELDS);
        customFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customFieldsProp.setValue(DEFAULT, "");
        customFieldsProp.setPropertyEditorClass(TypeEditor.class);
        customFieldsProp.setPropertyEditorClass(TypeEditor.class);
        customFieldsProp.setDisplayName("Custom Response Fields");
        customFieldsProp.setShortDescription(
            "Custom fields to add to responses.\n" +
            "Format: field:value;field:value\n" +
            "Example: 43:Bank Core System;49:901"
        );
    }
}
