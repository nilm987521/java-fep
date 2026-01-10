package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TypeEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for BankCoreMessageSenderSampler.
 *
 * <p>Defines the GUI elements for sending proactive messages from Bank Core System.
 */
public class BankCoreMessageSenderSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String MESSAGE_GROUP = "message";
    private static final String TARGET_GROUP = "target";
    private static final String DATA_GROUP = "data";
    private static final String ADVANCED_GROUP = "advanced";

    // Message type options
    private static final String[] MESSAGE_TYPES = {
        BankCoreMessageSenderSampler.TYPE_ECHO_TEST,
        BankCoreMessageSenderSampler.TYPE_RECONCILIATION,
        BankCoreMessageSenderSampler.TYPE_ACCOUNT_UPDATE,
        BankCoreMessageSenderSampler.TYPE_SYSTEM_STATUS,
        BankCoreMessageSenderSampler.TYPE_SIGN_ON,
        BankCoreMessageSenderSampler.TYPE_SIGN_OFF,
        BankCoreMessageSenderSampler.TYPE_CUSTOM
    };

    public BankCoreMessageSenderSamplerBeanInfo() {
        super(BankCoreMessageSenderSampler.class);

        // Create property groups
        createPropertyGroup(MESSAGE_GROUP, new String[]{
            BankCoreMessageSenderSampler.MESSAGE_TYPE,
            BankCoreMessageSenderSampler.CUSTOM_MTI,
            BankCoreMessageSenderSampler.MESSAGE_FIELDS
        });

        createPropertyGroup(TARGET_GROUP, new String[]{
            BankCoreMessageSenderSampler.TARGET_FEP_ID
        });

        createPropertyGroup(DATA_GROUP, new String[]{
            BankCoreMessageSenderSampler.SETTLEMENT_DATE,
            BankCoreMessageSenderSampler.ACCOUNT_NUMBER
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            BankCoreMessageSenderSampler.SEND_PORT,
            BankCoreMessageSenderSampler.RECEIVE_PORT
        });

        // Message properties
        PropertyDescriptor messageTypeProp = property(BankCoreMessageSenderSampler.MESSAGE_TYPE);
        messageTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        messageTypeProp.setValue(DEFAULT, BankCoreMessageSenderSampler.TYPE_ECHO_TEST);
        messageTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        messageTypeProp.setValue(TAGS, MESSAGE_TYPES);
        messageTypeProp.setDisplayName("Message Type");
        messageTypeProp.setShortDescription(
            "Type of proactive message to send:\n" +
            "ECHO_TEST - Network management echo test\n" +
            "RECONCILIATION_NOTIFY - Settlement/Reconciliation notification\n" +
            "ACCOUNT_UPDATE - Account balance/status update\n" +
            "SYSTEM_STATUS - Core system status notification\n" +
            "SIGN_ON_NOTIFY - Sign-on notification\n" +
            "SIGN_OFF_NOTIFY - Sign-off notification\n" +
            "CUSTOM - Custom message with specified MTI"
        );

        PropertyDescriptor customMtiProp = property(BankCoreMessageSenderSampler.CUSTOM_MTI);
        customMtiProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customMtiProp.setValue(DEFAULT, "0800");
        customMtiProp.setDisplayName("Custom MTI");
        customMtiProp.setShortDescription("MTI for custom message type (e.g., 0800, 0200).");

        PropertyDescriptor messageFieldsProp = property(BankCoreMessageSenderSampler.MESSAGE_FIELDS);
        messageFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        messageFieldsProp.setValue(DEFAULT, "");
        messageFieldsProp.setValue(TypeEditor.class.getName(), TypeEditor.TextAreaEditor);
        messageFieldsProp.setDisplayName("Message Fields");
        messageFieldsProp.setShortDescription(
            "Custom message fields.\n" +
            "Format: field:value;field:value\n" +
            "Example: 70:301;48:CUSTOM_DATA"
        );

        // Target properties
        PropertyDescriptor targetFepIdProp = property(BankCoreMessageSenderSampler.TARGET_FEP_ID);
        targetFepIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        targetFepIdProp.setValue(DEFAULT, "");
        targetFepIdProp.setDisplayName("Target FEP ID");
        targetFepIdProp.setShortDescription(
            "FEP ID to send message to.\n" +
            "Leave empty or set 'ALL' to broadcast to all connected FEP clients."
        );

        // Data properties
        PropertyDescriptor settlementDateProp = property(BankCoreMessageSenderSampler.SETTLEMENT_DATE);
        settlementDateProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        settlementDateProp.setValue(DEFAULT, "");
        settlementDateProp.setDisplayName("Settlement Date");
        settlementDateProp.setShortDescription(
            "Settlement date for reconciliation notification.\n" +
            "Format: MMDD (e.g., 0115 for January 15).\n" +
            "Leave empty to use current date."
        );

        PropertyDescriptor accountNumberProp = property(BankCoreMessageSenderSampler.ACCOUNT_NUMBER);
        accountNumberProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        accountNumberProp.setValue(DEFAULT, "");
        accountNumberProp.setDisplayName("Account Number");
        accountNumberProp.setShortDescription(
            "Account number for account update notification.\n" +
            "Used in Field 102 (Account Identification)."
        );

        // Advanced properties
        PropertyDescriptor sendPortProp = property(BankCoreMessageSenderSampler.SEND_PORT);
        sendPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sendPortProp.setValue(DEFAULT, 0);
        sendPortProp.setDisplayName("Send Port");
        sendPortProp.setShortDescription("Send port of the target Bank Core server. Use 0 to auto-detect.");

        PropertyDescriptor receivePortProp = property(BankCoreMessageSenderSampler.RECEIVE_PORT);
        receivePortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        receivePortProp.setValue(DEFAULT, 0);
        receivePortProp.setDisplayName("Receive Port");
        receivePortProp.setShortDescription("Receive port of the target Bank Core server. Use 0 to auto-detect.");
    }
}
