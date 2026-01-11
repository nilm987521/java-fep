package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.GenericTestBeanCustomizer;
import org.apache.jmeter.testbeans.gui.TextAreaEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for FiscDualChannelServerSampler.
 *
 * <p>Defines the GUI elements for configuring the FISC Dual-Channel Server Simulator.
 */
public class FiscDualChannelServerSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String MODE_GROUP = "mode";
    private static final String SERVER_GROUP = "server";
    private static final String RESPONSE_GROUP = "response";
    private static final String VALIDATION_GROUP = "validation";
    private static final String ROUTING_GROUP = "routing";
    private static final String ACTIVE_GROUP = "active";
    private static final String ADVANCED_GROUP = "advanced";

    // Common response codes
    private static final String[] RESPONSE_CODES = {
        "00", "01", "03", "04", "05", "12", "13", "14",
        "30", "41", "43", "51", "54", "55", "57", "58",
        "61", "65", "75", "91", "96"
    };

    // Operation modes
    private static final String[] OPERATION_MODES = OperationMode.names();

    // Active message types
    private static final String[] ACTIVE_MESSAGE_TYPES = ActiveMessageType.names();

    public FiscDualChannelServerSamplerBeanInfo() {
        super(FiscDualChannelServerSampler.class);

        // Create property groups (order matters for display)
        createPropertyGroup(MODE_GROUP, new String[]{
            FiscDualChannelServerSampler.OPERATION_MODE
        });

        createPropertyGroup(SERVER_GROUP, new String[]{
            FiscDualChannelServerSampler.SEND_PORT,
            FiscDualChannelServerSampler.RECEIVE_PORT,
            FiscDualChannelServerSampler.SAMPLE_INTERVAL
        });

        createPropertyGroup(RESPONSE_GROUP, new String[]{
            FiscDualChannelServerSampler.DEFAULT_RESPONSE_CODE,
            FiscDualChannelServerSampler.RESPONSE_DELAY,
            FiscDualChannelServerSampler.BALANCE_AMOUNT
        });

        createPropertyGroup(VALIDATION_GROUP, new String[]{
            FiscDualChannelServerSampler.ENABLE_VALIDATION,
            FiscDualChannelServerSampler.VALIDATION_ERROR_CODE,
            FiscDualChannelServerSampler.VALIDATION_RULES
        });

        createPropertyGroup(ROUTING_GROUP, new String[]{
            FiscDualChannelServerSampler.ENABLE_BANK_ID_ROUTING,
            FiscDualChannelServerSampler.BANK_ID_FIELD
        });

        createPropertyGroup(ACTIVE_GROUP, new String[]{
            FiscDualChannelServerSampler.ACTIVE_MESSAGE_TYPE,
            FiscDualChannelServerSampler.ACTIVE_TARGET_BANK_ID,
            FiscDualChannelServerSampler.ACTIVE_CUSTOM_MTI,
            FiscDualChannelServerSampler.ACTIVE_CUSTOM_FIELDS
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            FiscDualChannelServerSampler.MTI_RESPONSE_RULES,
            FiscDualChannelServerSampler.RESPONSE_RULES,
            FiscDualChannelServerSampler.CUSTOM_RESPONSE_FIELDS
        });

        // Operation Mode property
        PropertyDescriptor operationModeProp = property(FiscDualChannelServerSampler.OPERATION_MODE);
        operationModeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        operationModeProp.setValue(DEFAULT, OperationMode.PASSIVE.name());
        operationModeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        operationModeProp.setValue(NOT_OTHER, Boolean.TRUE);
        operationModeProp.setValue(TAGS, OPERATION_MODES);
        operationModeProp.setDisplayName("Operation Mode");
        operationModeProp.setShortDescription(
            "PASSIVE: Wait for incoming messages and respond.\n" +
            "ACTIVE: Send proactive messages to clients.\n" +
            "BIDIRECTIONAL: Simultaneously send and receive messages."
        );

        // Server properties
        PropertyDescriptor sendPortProp = property(FiscDualChannelServerSampler.SEND_PORT);
        sendPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sendPortProp.setValue(DEFAULT, 9000);
        sendPortProp.setDisplayName("Send Port");
        sendPortProp.setShortDescription("TCP port for receiving requests (Send Channel). Use 0 for random port.");

        PropertyDescriptor receivePortProp = property(FiscDualChannelServerSampler.RECEIVE_PORT);
        receivePortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        receivePortProp.setValue(DEFAULT, 9001);
        receivePortProp.setDisplayName("Receive Port");
        receivePortProp.setShortDescription("TCP port for sending responses (Receive Channel). Use 0 for random port.");

        PropertyDescriptor sampleIntervalProp = property(FiscDualChannelServerSampler.SAMPLE_INTERVAL);
        sampleIntervalProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sampleIntervalProp.setValue(DEFAULT, 1000);
        sampleIntervalProp.setDisplayName("Sample Interval (ms)");
        sampleIntervalProp.setShortDescription("Interval between statistics collection in milliseconds.");

        // Response properties
        PropertyDescriptor responseCodeProp = property(FiscDualChannelServerSampler.DEFAULT_RESPONSE_CODE);
        responseCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseCodeProp.setValue(DEFAULT, "00");
        responseCodeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        responseCodeProp.setValue(NOT_OTHER, Boolean.TRUE);
        responseCodeProp.setValue(TAGS, RESPONSE_CODES);
        responseCodeProp.setDisplayName("Default Response Code");
        responseCodeProp.setShortDescription("Default ISO 8583 response code (Field 39).");

        PropertyDescriptor responseDelayProp = property(FiscDualChannelServerSampler.RESPONSE_DELAY);
        responseDelayProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseDelayProp.setValue(DEFAULT, 0);
        responseDelayProp.setDisplayName("Response Delay (ms)");
        responseDelayProp.setShortDescription("Simulated processing delay before sending response.");

        PropertyDescriptor balanceAmountProp = property(FiscDualChannelServerSampler.BALANCE_AMOUNT);
        balanceAmountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        balanceAmountProp.setValue(DEFAULT, "");
        balanceAmountProp.setDisplayName("Balance Amount");
        balanceAmountProp.setShortDescription("Account balance for Field 54. Format: amount in cents.");

        // Validation properties
        PropertyDescriptor enableValidationProp = property(FiscDualChannelServerSampler.ENABLE_VALIDATION);
        enableValidationProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        enableValidationProp.setValue(DEFAULT, Boolean.TRUE);
        enableValidationProp.setDisplayName("Enable Validation");
        enableValidationProp.setShortDescription("Enable message validation against rules.");

        PropertyDescriptor validationErrorCodeProp = property(FiscDualChannelServerSampler.VALIDATION_ERROR_CODE);
        validationErrorCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        validationErrorCodeProp.setValue(DEFAULT, "30");
        validationErrorCodeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        validationErrorCodeProp.setValue(NOT_OTHER, Boolean.TRUE);
        validationErrorCodeProp.setValue(TAGS, RESPONSE_CODES);
        validationErrorCodeProp.setDisplayName("Validation Error Code");
        validationErrorCodeProp.setShortDescription("Response code when validation fails (default: 30 = Format Error).");

        PropertyDescriptor validationRulesProp = property(FiscDualChannelServerSampler.VALIDATION_RULES);
        validationRulesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        validationRulesProp.setValue(DEFAULT, "");
        validationRulesProp.setPropertyEditorClass(TextAreaEditor.class);
        validationRulesProp.setDisplayName("Validation Rules");
        validationRulesProp.setShortDescription(
            "Validation rules (JSON or Text format - auto-detected).\n\n" +
            "JSON format (recommended):\n" +
            "{\"globalRules\": {\"required\": [2,3,4], \"format\": {\"2\": \"N(13-19)\"}}}\n\n" +
            "Text format:\n" +
            "REQUIRED:2,3,4,11,41,42\n" +
            "FORMAT:2=N(13-19);3=N(6)\n" +
            "VALUE:3=010000|400000|310000\n" +
            "LENGTH:4=12;11=6\n" +
            "PATTERN:37=^[A-Z0-9]{12}$\n" +
            "MTI:0200=REQUIRED:2,3,4,11"
        );

        // Routing properties
        PropertyDescriptor enableRoutingProp = property(FiscDualChannelServerSampler.ENABLE_BANK_ID_ROUTING);
        enableRoutingProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        enableRoutingProp.setValue(DEFAULT, Boolean.TRUE);
        enableRoutingProp.setDisplayName("Enable Bank ID Routing");
        enableRoutingProp.setShortDescription("Route responses to clients based on Bank ID field.");

        PropertyDescriptor bankIdFieldProp = property(FiscDualChannelServerSampler.BANK_ID_FIELD);
        bankIdFieldProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        bankIdFieldProp.setValue(DEFAULT, 32);
        bankIdFieldProp.setDisplayName("Bank ID Field");
        bankIdFieldProp.setShortDescription("Field number containing Bank ID (default: 32 = Acquiring Institution ID).");

        // Advanced properties - MTI Response Rules (JSON format)
        PropertyDescriptor mtiRulesProp = property(FiscDualChannelServerSampler.MTI_RESPONSE_RULES);
        mtiRulesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        mtiRulesProp.setValue(DEFAULT, "");
        mtiRulesProp.setPropertyEditorClass(TextAreaEditor.class);
        mtiRulesProp.setValue(GenericTestBeanCustomizer.TEXT_LANGUAGE, "json");
        mtiRulesProp.setDisplayName("MTI Response Rules (JSON)");
        mtiRulesProp.setShortDescription(
            "JSON format MTI response rules.\n" +
            "Variables: ${VAR}, ${Fnn}, ${STAN}, ${RRN}"
        );

        // Legacy Response Rules
        PropertyDescriptor responseRulesProp = property(FiscDualChannelServerSampler.RESPONSE_RULES);
        responseRulesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseRulesProp.setValue(DEFAULT, "");
        responseRulesProp.setPropertyEditorClass(TextAreaEditor.class);
        responseRulesProp.setDisplayName("Response Rules");
        responseRulesProp.setShortDescription(
            "Response code rules by processing code.\n" +
            "Format: processingCode:responseCode;...\n" +
            "Example: 010000:00;400000:51;310000:00"
        );

        PropertyDescriptor customFieldsProp = property(FiscDualChannelServerSampler.CUSTOM_RESPONSE_FIELDS);
        customFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customFieldsProp.setValue(DEFAULT, "");
        customFieldsProp.setPropertyEditorClass(TextAreaEditor.class);
        customFieldsProp.setDisplayName("Custom Response Fields");
        customFieldsProp.setShortDescription(
            "Custom fields to add to responses.\n" +
            "Format: field:value;field:value\n" +
            "Example: 43:Test Merchant;49:901"
        );

        // Active mode properties
        PropertyDescriptor activeMessageTypeProp = property(FiscDualChannelServerSampler.ACTIVE_MESSAGE_TYPE);
        activeMessageTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        activeMessageTypeProp.setValue(DEFAULT, ActiveMessageType.ECHO_TEST.name());
        activeMessageTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        activeMessageTypeProp.setValue(NOT_OTHER, Boolean.TRUE);
        activeMessageTypeProp.setValue(TAGS, ACTIVE_MESSAGE_TYPES);
        activeMessageTypeProp.setDisplayName("Active Message Type");
        activeMessageTypeProp.setShortDescription(
            "Type of message to send in ACTIVE/BIDIRECTIONAL mode.\n" +
            "SIGN_ON: Sign-on request (0800/F70=001)\n" +
            "SIGN_OFF: Sign-off request (0800/F70=002)\n" +
            "ECHO_TEST: Echo test (0800/F70=301)\n" +
            "KEY_EXCHANGE: Key exchange notification (0800/F70=161)\n" +
            "CUSTOM: Custom message with user-defined MTI"
        );

        PropertyDescriptor activeTargetBankIdProp = property(FiscDualChannelServerSampler.ACTIVE_TARGET_BANK_ID);
        activeTargetBankIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        activeTargetBankIdProp.setValue(DEFAULT, "");
        activeTargetBankIdProp.setDisplayName("Target Bank ID");
        activeTargetBankIdProp.setShortDescription(
            "Bank ID to send the message to.\n" +
            "Leave empty or 'ALL' to broadcast to all connected clients."
        );

        PropertyDescriptor activeCustomMtiProp = property(FiscDualChannelServerSampler.ACTIVE_CUSTOM_MTI);
        activeCustomMtiProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        activeCustomMtiProp.setValue(DEFAULT, "0800");
        activeCustomMtiProp.setDisplayName("Custom MTI");
        activeCustomMtiProp.setShortDescription(
            "MTI for CUSTOM message type.\n" +
            "Default: 0800 (Network Management Request)"
        );

        PropertyDescriptor activeCustomFieldsProp = property(FiscDualChannelServerSampler.ACTIVE_CUSTOM_FIELDS);
        activeCustomFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        activeCustomFieldsProp.setValue(DEFAULT, "");
        activeCustomFieldsProp.setPropertyEditorClass(TextAreaEditor.class);
        activeCustomFieldsProp.setDisplayName("Custom Fields");
        activeCustomFieldsProp.setShortDescription(
            "Custom fields to add to the active message.\n" +
            "Format: field:value;field:value\n" +
            "Example: 48:NOTIFICATION;32:004"
        );
    }
}
