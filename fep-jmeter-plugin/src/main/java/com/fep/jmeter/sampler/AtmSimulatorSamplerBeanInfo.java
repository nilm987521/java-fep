package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TypeEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for AtmSimulatorSampler.
 *
 * <p>Defines the GUI elements for configuring the ATM Simulator.
 */
public class AtmSimulatorSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String CONNECTION_GROUP = "connection";
    private static final String TERMINAL_GROUP = "terminal";
    private static final String TRANSACTION_GROUP = "transaction";
    private static final String CARD_GROUP = "card";
    private static final String ADVANCED_GROUP = "advanced";

    // Transaction types
    private static final String[] TRANSACTION_TYPES = AtmTransactionType.names();

    public AtmSimulatorSamplerBeanInfo() {
        super(AtmSimulatorSampler.class);

        // Create property groups
        createPropertyGroup(CONNECTION_GROUP, new String[]{
            AtmSimulatorSampler.FEP_HOST,
            AtmSimulatorSampler.FEP_PORT,
            AtmSimulatorSampler.CONNECTION_TIMEOUT,
            AtmSimulatorSampler.READ_TIMEOUT
        });

        createPropertyGroup(TERMINAL_GROUP, new String[]{
            AtmSimulatorSampler.ATM_ID,
            AtmSimulatorSampler.ATM_LOCATION,
            AtmSimulatorSampler.BANK_CODE
        });

        createPropertyGroup(TRANSACTION_GROUP, new String[]{
            AtmSimulatorSampler.TRANSACTION_TYPE,
            AtmSimulatorSampler.AMOUNT,
            AtmSimulatorSampler.DESTINATION_ACCOUNT
        });

        createPropertyGroup(CARD_GROUP, new String[]{
            AtmSimulatorSampler.CARD_NUMBER
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            AtmSimulatorSampler.CUSTOM_FIELDS
        });

        // Connection properties
        PropertyDescriptor fepHostProp = property(AtmSimulatorSampler.FEP_HOST);
        fepHostProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fepHostProp.setValue(DEFAULT, "localhost");
        fepHostProp.setDisplayName("FEP Host");
        fepHostProp.setShortDescription("Hostname or IP address of the FEP server.");

        PropertyDescriptor fepPortProp = property(AtmSimulatorSampler.FEP_PORT);
        fepPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fepPortProp.setValue(DEFAULT, 8080);
        fepPortProp.setDisplayName("FEP Port");
        fepPortProp.setShortDescription("TCP port of the FEP server for ATM connections.");

        PropertyDescriptor connTimeoutProp = property(AtmSimulatorSampler.CONNECTION_TIMEOUT);
        connTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        connTimeoutProp.setValue(DEFAULT, 10000);
        connTimeoutProp.setDisplayName("Connection Timeout (ms)");
        connTimeoutProp.setShortDescription("Maximum time to wait for connection in milliseconds.");

        PropertyDescriptor readTimeoutProp = property(AtmSimulatorSampler.READ_TIMEOUT);
        readTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        readTimeoutProp.setValue(DEFAULT, 30000);
        readTimeoutProp.setDisplayName("Read Timeout (ms)");
        readTimeoutProp.setShortDescription("Maximum time to wait for response in milliseconds.");

        // Terminal properties
        PropertyDescriptor atmIdProp = property(AtmSimulatorSampler.ATM_ID);
        atmIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        atmIdProp.setValue(DEFAULT, "");
        atmIdProp.setDisplayName("ATM ID");
        atmIdProp.setShortDescription("ATM terminal identifier (Field 41). Leave empty for default 'ATM00001'.");

        PropertyDescriptor atmLocationProp = property(AtmSimulatorSampler.ATM_LOCATION);
        atmLocationProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        atmLocationProp.setValue(DEFAULT, "");
        atmLocationProp.setDisplayName("ATM Location");
        atmLocationProp.setShortDescription("ATM location description (Field 43). Example: 'Taipei Main Branch'.");

        PropertyDescriptor bankCodeProp = property(AtmSimulatorSampler.BANK_CODE);
        bankCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        bankCodeProp.setValue(DEFAULT, "");
        bankCodeProp.setDisplayName("Bank Code");
        bankCodeProp.setShortDescription("Acquiring institution ID (Field 32). Example: '012' for Taipei Fubon.");

        // Transaction properties
        PropertyDescriptor txnTypeProp = property(AtmSimulatorSampler.TRANSACTION_TYPE);
        txnTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        txnTypeProp.setValue(DEFAULT, AtmTransactionType.BALANCE_INQUIRY.name());
        txnTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        txnTypeProp.setValue(NOT_OTHER, Boolean.TRUE);
        txnTypeProp.setValue(TAGS, TRANSACTION_TYPES);
        txnTypeProp.setDisplayName("Transaction Type");
        txnTypeProp.setShortDescription(
            "Type of ATM transaction:\n" +
            "WITHDRAWAL - Cash withdrawal (提款)\n" +
            "BALANCE_INQUIRY - Balance inquiry (餘額查詢)\n" +
            "TRANSFER - Fund transfer (轉帳)\n" +
            "DEPOSIT - Cash deposit (存款)\n" +
            "PIN_CHANGE - PIN change (密碼變更)\n" +
            "MINI_STATEMENT - Transaction history (交易明細)\n" +
            "CARDLESS_WITHDRAWAL - Cardless withdrawal (無卡提款)"
        );

        PropertyDescriptor amountProp = property(AtmSimulatorSampler.AMOUNT);
        amountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        amountProp.setValue(DEFAULT, "");
        amountProp.setDisplayName("Amount");
        amountProp.setShortDescription(
            "Transaction amount in cents (Field 4).\n" +
            "Example: 100000 = $1,000.00"
        );

        PropertyDescriptor destAccountProp = property(AtmSimulatorSampler.DESTINATION_ACCOUNT);
        destAccountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        destAccountProp.setValue(DEFAULT, "");
        destAccountProp.setDisplayName("Destination Account");
        destAccountProp.setShortDescription("Destination account number for transfer transactions (Field 103).");

        // Card properties
        PropertyDescriptor cardNumberProp = property(AtmSimulatorSampler.CARD_NUMBER);
        cardNumberProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        cardNumberProp.setValue(DEFAULT, "");
        cardNumberProp.setDisplayName("Card Number");
        cardNumberProp.setShortDescription("Primary Account Number (Field 2). Example: 4716********1234.");

        // Advanced properties
        PropertyDescriptor customFieldsProp = property(AtmSimulatorSampler.CUSTOM_FIELDS);
        customFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customFieldsProp.setValue(DEFAULT, "");
        customFieldsProp.setPropertyEditorClass(TypeEditor.class);
        customFieldsProp.setDisplayName("Custom Fields");
        customFieldsProp.setShortDescription(
            "Additional ISO 8583 fields to set.\n" +
            "Format: field:value;field:value\n" +
            "Example: 14:2512;35:4716...1234=2512\n" +
            "Supports JMeter variables: ${varName}"
        );
    }
}
