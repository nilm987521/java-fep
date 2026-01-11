package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TypeEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for FiscMessageSenderSampler.
 *
 * <p>Defines the GUI elements for sending proactive FISC messages.
 */
public class FiscMessageSenderSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String MESSAGE_GROUP = "message";
    private static final String TARGET_GROUP = "target";
    private static final String ADVANCED_GROUP = "advanced";

    // Message type options
    private static final String[] MESSAGE_TYPES = {
        FiscMessageSenderSampler.TYPE_ECHO_TEST,
        FiscMessageSenderSampler.TYPE_KEY_CHANGE,
        FiscMessageSenderSampler.TYPE_SYSTEM_STATUS,
        FiscMessageSenderSampler.TYPE_SIGN_ON,
        FiscMessageSenderSampler.TYPE_SIGN_OFF,
        FiscMessageSenderSampler.TYPE_CUSTOM
    };

    public FiscMessageSenderSamplerBeanInfo() {
        super(FiscMessageSenderSampler.class);

        // Create property groups
        createPropertyGroup(MESSAGE_GROUP, new String[]{
            FiscMessageSenderSampler.MESSAGE_TYPE,
            FiscMessageSenderSampler.CUSTOM_MTI,
            FiscMessageSenderSampler.MESSAGE_FIELDS
        });

        createPropertyGroup(TARGET_GROUP, new String[]{
            FiscMessageSenderSampler.TARGET_BANK_ID
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            FiscMessageSenderSampler.SEND_PORT,
            FiscMessageSenderSampler.RECEIVE_PORT
        });

        // Message properties
        PropertyDescriptor messageTypeProp = property(FiscMessageSenderSampler.MESSAGE_TYPE);
        messageTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        messageTypeProp.setValue(DEFAULT, FiscMessageSenderSampler.TYPE_ECHO_TEST);
        messageTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        messageTypeProp.setValue(NOT_OTHER, Boolean.TRUE);
        messageTypeProp.setValue(TAGS, MESSAGE_TYPES);
        messageTypeProp.setDisplayName("Message Type");
        messageTypeProp.setShortDescription(
            "Type of proactive message to send:\n" +
            "ECHO_TEST - Network management echo test\n" +
            "KEY_CHANGE_NOTIFY - Key change notification\n" +
            "SYSTEM_STATUS - System status notification\n" +
            "SIGN_ON_NOTIFY - Sign-on notification\n" +
            "SIGN_OFF_NOTIFY - Sign-off notification\n" +
            "CUSTOM - Custom message with specified MTI"
        );

        PropertyDescriptor customMtiProp = property(FiscMessageSenderSampler.CUSTOM_MTI);
        customMtiProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customMtiProp.setValue(DEFAULT, "0800");
        customMtiProp.setDisplayName("Custom MTI");
        customMtiProp.setShortDescription("MTI for custom message type (e.g., 0800, 0200).");

        PropertyDescriptor messageFieldsProp = property(FiscMessageSenderSampler.MESSAGE_FIELDS);
        messageFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        messageFieldsProp.setValue(DEFAULT, "");
        messageFieldsProp.setPropertyEditorClass(TypeEditor.class);
        messageFieldsProp.setDisplayName("Message Fields");
        messageFieldsProp.setShortDescription(
            "Custom message fields.\n" +
            "Format: field:value;field:value\n" +
            "Example: 70:301;48:KEY_DATA"
        );

        // Target properties
        PropertyDescriptor targetBankIdProp = property(FiscMessageSenderSampler.TARGET_BANK_ID);
        targetBankIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        targetBankIdProp.setValue(DEFAULT, "");
        targetBankIdProp.setDisplayName("Target Bank ID");
        targetBankIdProp.setShortDescription(
            "Bank ID to send message to.\n" +
            "Leave empty or set 'ALL' to broadcast to all connected clients."
        );

        // Advanced properties
        PropertyDescriptor sendPortProp = property(FiscMessageSenderSampler.SEND_PORT);
        sendPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sendPortProp.setValue(DEFAULT, 0);
        sendPortProp.setDisplayName("Send Port");
        sendPortProp.setShortDescription("Send port of the target server. Use 0 to auto-detect.");

        PropertyDescriptor receivePortProp = property(FiscMessageSenderSampler.RECEIVE_PORT);
        receivePortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        receivePortProp.setValue(DEFAULT, 0);
        receivePortProp.setDisplayName("Receive Port");
        receivePortProp.setShortDescription("Receive port of the target server. Use 0 to auto-detect.");
    }
}
