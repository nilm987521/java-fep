package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TypeEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for FiscSampler.
 *
 * <p>This class defines the GUI elements for configuring the FISC Sampler
 * in JMeter's graphical interface. Properties are grouped into logical
 * sections for better usability.
 */
public class FiscSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String CONNECTION_GROUP = "connection";
    private static final String TRANSACTION_GROUP = "transaction";
    private static final String MESSAGE_GROUP = "message";

    // Transaction type options
    private static final String[] TRANSACTION_TYPES = {
        FiscSampler.TXN_ECHO_TEST,
        FiscSampler.TXN_SIGN_ON,
        FiscSampler.TXN_SIGN_OFF,
        FiscSampler.TXN_WITHDRAWAL,
        FiscSampler.TXN_TRANSFER,
        FiscSampler.TXN_BALANCE_INQUIRY,
        FiscSampler.TXN_BILL_PAYMENT
    };

    public FiscSamplerBeanInfo() {
        super(FiscSampler.class);

        // Create property groups
        createPropertyGroup(CONNECTION_GROUP, new String[]{
            FiscSampler.HOST,
            FiscSampler.PORT,
            FiscSampler.CONNECTION_TIMEOUT,
            FiscSampler.READ_TIMEOUT
        });

        createPropertyGroup(TRANSACTION_GROUP, new String[]{
            FiscSampler.TRANSACTION_TYPE,
            FiscSampler.INSTITUTION_ID,
            FiscSampler.TERMINAL_ID
        });

        createPropertyGroup(MESSAGE_GROUP, new String[]{
            FiscSampler.PAN,
            FiscSampler.PROCESSING_CODE,
            FiscSampler.AMOUNT,
            FiscSampler.CUSTOM_FIELDS
        });

        // Connection properties
        PropertyDescriptor hostProp = property(FiscSampler.HOST);
        hostProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        hostProp.setValue(DEFAULT, "localhost");
        hostProp.setDisplayName("FISC Host");
        hostProp.setShortDescription("FISC server hostname or IP address");

        PropertyDescriptor portProp = property(FiscSampler.PORT);
        portProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        portProp.setValue(DEFAULT, 9000);
        portProp.setDisplayName("FISC Port");
        portProp.setShortDescription("FISC server port number");

        PropertyDescriptor connTimeoutProp = property(FiscSampler.CONNECTION_TIMEOUT);
        connTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        connTimeoutProp.setValue(DEFAULT, 10000);
        connTimeoutProp.setDisplayName("Connection Timeout (ms)");
        connTimeoutProp.setShortDescription("TCP connection timeout in milliseconds");

        PropertyDescriptor readTimeoutProp = property(FiscSampler.READ_TIMEOUT);
        readTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        readTimeoutProp.setValue(DEFAULT, 30000);
        readTimeoutProp.setDisplayName("Read Timeout (ms)");
        readTimeoutProp.setShortDescription("Response timeout in milliseconds");

        // Transaction properties
        PropertyDescriptor txnTypeProp = property(FiscSampler.TRANSACTION_TYPE);
        txnTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        txnTypeProp.setValue(DEFAULT, FiscSampler.TXN_ECHO_TEST);
        txnTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        txnTypeProp.setValue(TAGS, TRANSACTION_TYPES);
        txnTypeProp.setDisplayName("Transaction Type");
        txnTypeProp.setShortDescription("Type of FISC transaction to perform");

        PropertyDescriptor instIdProp = property(FiscSampler.INSTITUTION_ID);
        instIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        instIdProp.setValue(DEFAULT, "");
        instIdProp.setDisplayName("Institution ID");
        instIdProp.setShortDescription("Bank institution ID (Field 32)");

        PropertyDescriptor termIdProp = property(FiscSampler.TERMINAL_ID);
        termIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        termIdProp.setValue(DEFAULT, "");
        termIdProp.setDisplayName("Terminal ID");
        termIdProp.setShortDescription("Terminal ID (Field 41)");

        // Message properties
        PropertyDescriptor panProp = property(FiscSampler.PAN);
        panProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        panProp.setValue(DEFAULT, "");
        panProp.setDisplayName("PAN (Card Number)");
        panProp.setShortDescription("Primary Account Number (Field 2)");

        PropertyDescriptor procCodeProp = property(FiscSampler.PROCESSING_CODE);
        procCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        procCodeProp.setValue(DEFAULT, "");
        procCodeProp.setDisplayName("Processing Code");
        procCodeProp.setShortDescription("Processing code (Field 3). Leave empty for default based on transaction type");

        PropertyDescriptor amountProp = property(FiscSampler.AMOUNT);
        amountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        amountProp.setValue(DEFAULT, "");
        amountProp.setDisplayName("Amount");
        amountProp.setShortDescription("Transaction amount in cents (Field 4). Example: 10000 = NT$100.00");

        PropertyDescriptor customFieldsProp = property(FiscSampler.CUSTOM_FIELDS);
        customFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customFieldsProp.setValue(DEFAULT, "");
        customFieldsProp.setValue(TypeEditor.class.getName(), TypeEditor.TextAreaEditor);
        customFieldsProp.setDisplayName("Custom Fields");
        customFieldsProp.setShortDescription("Additional ISO 8583 fields. Format: field:value;field:value. Example: 43:Merchant Name;49:901");
    }
}
