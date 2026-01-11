package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TextAreaEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for FiscServerSampler.
 *
 * <p>This class defines the GUI elements for configuring the FISC Server Simulator
 * in JMeter's graphical interface. Properties are grouped into logical sections
 * for better usability.
 *
 * <p>Property Groups:
 * <ul>
 *   <li>Server Settings: Port, sample interval</li>
 *   <li>Response Settings: Default response code, delay, balance</li>
 *   <li>Advanced: Custom fields, response rules</li>
 * </ul>
 */
public class FiscServerSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String SERVER_GROUP = "server";
    private static final String RESPONSE_GROUP = "response";
    private static final String ADVANCED_GROUP = "advanced";

    // Common response codes for dropdown
    private static final String[] RESPONSE_CODES = {
        "00",  // Approved
        "01",  // Refer to card issuer
        "03",  // Invalid merchant
        "04",  // Pick up card
        "05",  // Do not honor
        "12",  // Invalid transaction
        "13",  // Invalid amount
        "14",  // Invalid card number
        "30",  // Format error
        "41",  // Lost card
        "43",  // Stolen card
        "51",  // Insufficient funds
        "54",  // Expired card
        "55",  // Incorrect PIN
        "57",  // Transaction not permitted
        "58",  // Transaction not permitted to terminal
        "61",  // Exceeds withdrawal limit
        "65",  // Exceeds withdrawal frequency
        "75",  // PIN tries exceeded
        "91",  // Issuer unavailable
        "96"   // System malfunction
    };

    public FiscServerSamplerBeanInfo() {
        super(FiscServerSampler.class);

        // Create property groups
        createPropertyGroup(SERVER_GROUP, new String[]{
            FiscServerSampler.PORT,
            FiscServerSampler.SAMPLE_INTERVAL
        });

        createPropertyGroup(RESPONSE_GROUP, new String[]{
            FiscServerSampler.RESPONSE_CODE,
            FiscServerSampler.RESPONSE_DELAY,
            FiscServerSampler.BALANCE_AMOUNT
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            FiscServerSampler.RESPONSE_RULES,
            FiscServerSampler.CUSTOM_RESPONSE_FIELDS
        });

        // Server properties
        PropertyDescriptor portProp = property(FiscServerSampler.PORT);
        portProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        portProp.setValue(DEFAULT, 9001);
        portProp.setDisplayName("Server Port");
        portProp.setShortDescription("TCP port for the FISC server simulator. Use 0 for random port.");

        PropertyDescriptor sampleIntervalProp = property(FiscServerSampler.SAMPLE_INTERVAL);
        sampleIntervalProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        sampleIntervalProp.setValue(DEFAULT, 1000);
        sampleIntervalProp.setDisplayName("Sample Interval (ms)");
        sampleIntervalProp.setShortDescription("Interval between samples in milliseconds. Controls how often statistics are collected.");

        // Response properties
        PropertyDescriptor responseCodeProp = property(FiscServerSampler.RESPONSE_CODE);
        responseCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseCodeProp.setValue(DEFAULT, "00");
        responseCodeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        responseCodeProp.setValue(NOT_OTHER, Boolean.TRUE);
        responseCodeProp.setValue(TAGS, RESPONSE_CODES);
        responseCodeProp.setDisplayName("Default Response Code");
        responseCodeProp.setShortDescription("Default ISO 8583 response code (Field 39). 00=Approved, 51=Insufficient funds, etc.");

        PropertyDescriptor responseDelayProp = property(FiscServerSampler.RESPONSE_DELAY);
        responseDelayProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseDelayProp.setValue(DEFAULT, 0);
        responseDelayProp.setDisplayName("Response Delay (ms)");
        responseDelayProp.setShortDescription("Simulated processing delay before sending response in milliseconds.");

        PropertyDescriptor balanceAmountProp = property(FiscServerSampler.BALANCE_AMOUNT);
        balanceAmountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        balanceAmountProp.setValue(DEFAULT, "");
        balanceAmountProp.setDisplayName("Balance Amount");
        balanceAmountProp.setShortDescription("Account balance to return in Field 54. Format: amount in cents. Example: 1000000 = NT$10,000.00");

        // Advanced properties
        PropertyDescriptor responseRulesProp = property(FiscServerSampler.RESPONSE_RULES);
        responseRulesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseRulesProp.setValue(DEFAULT, "");
        responseRulesProp.setPropertyEditorClass(TextAreaEditor.class);
        responseRulesProp.setDisplayName("Response Rules");
        responseRulesProp.setShortDescription(
            "Custom response rules by processing code. Format: processingCode:responseCode;...\n" +
            "Example: 010000:00;400000:51;310000:00\n" +
            "This allows different response codes for withdrawal (010000), transfer (400000), balance (310000)."
        );

        PropertyDescriptor customFieldsProp = property(FiscServerSampler.CUSTOM_RESPONSE_FIELDS);
        customFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customFieldsProp.setValue(DEFAULT, "");
        customFieldsProp.setPropertyEditorClass(TextAreaEditor.class);
        customFieldsProp.setDisplayName("Custom Response Fields");
        customFieldsProp.setShortDescription(
            "Additional ISO 8583 fields to include in responses.\n" +
            "Format: field:value;field:value\n" +
            "Example: 43:Test Merchant;49:901;102:1234567890"
        );
    }
}
