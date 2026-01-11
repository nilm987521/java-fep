package com.fep.jmeter.sampler;

import com.fep.jmeter.config.TransactionTemplate;
import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TextAreaEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for AtmSimulatorSampler.
 *
 * <p>Defines the GUI elements for configuring the ATM Simulator.
 * Supports high customization through:
 * <ul>
 *   <li>Predefined transaction types with sensible defaults</li>
 *   <li>MTI and Processing Code override for custom transactions</li>
 *   <li>JSON message template for full field control</li>
 *   <li>Custom fields for additional ISO 8583 fields</li>
 * </ul>
 */
public class AtmSimulatorSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String CONNECTION_GROUP = "connection";
    private static final String PROTOCOL_GROUP = "protocol";
    private static final String TERMINAL_GROUP = "terminal";
    private static final String TRANSACTION_GROUP = "transaction";
    private static final String CARD_GROUP = "card";
    private static final String SECURITY_GROUP = "security";
    private static final String ADVANCED_GROUP = "advanced";
    private static final String RAW_MODE_GROUP = "rawMode";
    private static final String TEMPLATE_CONFIG_GROUP = "templateConfig";
    private static final String GENERIC_SCHEMA_GROUP = "genericSchema";

    // Transaction types
    private static final String[] TRANSACTION_TYPES = AtmTransactionType.names();
    private static final String[] PROTOCOL_TYPES = AtmProtocolType.names();
    private static final String[] RAW_MESSAGE_FORMATS = RawMessageFormat.names();
    private static final String[] LENGTH_HEADER_TYPES = LengthHeaderType.names();
    private static final String[] TEMPLATE_NAMES = TransactionTemplate.CommonTemplates.names();
    private static final String[] SCHEMA_SOURCES = SchemaSource.names();
    private static final String[] PRESET_SCHEMAS = PresetSchema.names();

    public AtmSimulatorSamplerBeanInfo() {
        super(AtmSimulatorSampler.class);

        // Create property groups
        createPropertyGroup(CONNECTION_GROUP, new String[]{
            AtmSimulatorSampler.FEP_HOST,
            AtmSimulatorSampler.FEP_PORT,
            AtmSimulatorSampler.CONNECTION_TIMEOUT,
            AtmSimulatorSampler.READ_TIMEOUT
        });

        createPropertyGroup(PROTOCOL_GROUP, new String[]{
            AtmSimulatorSampler.PROTOCOL_TYPE,
            AtmSimulatorSampler.LENGTH_HEADER_TYPE
        });

        createPropertyGroup(TERMINAL_GROUP, new String[]{
            AtmSimulatorSampler.ATM_ID,
            AtmSimulatorSampler.ATM_LOCATION,
            AtmSimulatorSampler.BANK_CODE
        });

        createPropertyGroup(TRANSACTION_GROUP, new String[]{
            AtmSimulatorSampler.TRANSACTION_TYPE,
            AtmSimulatorSampler.MTI_OVERRIDE,
            AtmSimulatorSampler.PROCESSING_CODE_OVERRIDE,
            AtmSimulatorSampler.AMOUNT,
            AtmSimulatorSampler.DESTINATION_ACCOUNT
        });

        createPropertyGroup(CARD_GROUP, new String[]{
            AtmSimulatorSampler.CARD_NUMBER
        });

        createPropertyGroup(SECURITY_GROUP, new String[]{
            AtmSimulatorSampler.ENABLE_PIN_BLOCK,
            AtmSimulatorSampler.PIN_BLOCK
        });

        createPropertyGroup(ADVANCED_GROUP, new String[]{
            AtmSimulatorSampler.CUSTOM_FIELDS,
            AtmSimulatorSampler.MESSAGE_TEMPLATE
        });

        createPropertyGroup(RAW_MODE_GROUP, new String[]{
            AtmSimulatorSampler.RAW_MESSAGE_FORMAT,
            AtmSimulatorSampler.RAW_MESSAGE_DATA,
            AtmSimulatorSampler.EXPECT_RESPONSE,
            AtmSimulatorSampler.RESPONSE_MATCH_PATTERN
        });

        createPropertyGroup(TEMPLATE_CONFIG_GROUP, new String[]{
            AtmSimulatorSampler.USE_TEMPLATE_CONFIG,
            AtmSimulatorSampler.TEMPLATE_NAME
        });

        createPropertyGroup(GENERIC_SCHEMA_GROUP, new String[]{
            AtmSimulatorSampler.SCHEMA_SOURCE,
            AtmSimulatorSampler.SCHEMA_FILE,
            AtmSimulatorSampler.PRESET_SCHEMA,
            AtmSimulatorSampler.SCHEMA_CONTENT,
            AtmSimulatorSampler.FIELD_VALUES
        });

        // ===== Connection properties =====
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

        // ===== Protocol properties =====
        PropertyDescriptor protocolTypeProp = property(AtmSimulatorSampler.PROTOCOL_TYPE);
        protocolTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        protocolTypeProp.setValue(DEFAULT, AtmProtocolType.ISO_8583.name());
        protocolTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        protocolTypeProp.setValue(NOT_OTHER, Boolean.TRUE);
        protocolTypeProp.setValue(TAGS, PROTOCOL_TYPES);
        protocolTypeProp.setDisplayName("Protocol Type");
        protocolTypeProp.setShortDescription(
            "Message protocol type.\n" +
            "ISO_8583 - Standard ISO 8583 financial message format\n" +
            "RAW - Send raw bytes (HEX, Base64, or Text encoded)\n" +
            "GENERIC_SCHEMA - User-defined message format via JSON schema"
        );

        PropertyDescriptor lengthHeaderTypeProp = property(AtmSimulatorSampler.LENGTH_HEADER_TYPE);
        lengthHeaderTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        lengthHeaderTypeProp.setValue(DEFAULT, LengthHeaderType.TWO_BYTES.name());
        lengthHeaderTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        lengthHeaderTypeProp.setValue(NOT_OTHER, Boolean.TRUE);
        lengthHeaderTypeProp.setValue(TAGS, LENGTH_HEADER_TYPES);
        lengthHeaderTypeProp.setDisplayName("Length Header Type");
        lengthHeaderTypeProp.setShortDescription(
            "Message framing length header format.\n" +
            "NONE - No length header (raw bytes)\n" +
            "TWO_BYTES - 2-byte big-endian length prefix (most common)\n" +
            "FOUR_BYTES - 4-byte big-endian length prefix\n" +
            "TWO_BYTES_BCD - 2-byte BCD encoded length\n" +
            "ASCII_4 - 4-character ASCII decimal length"
        );

        // ===== Terminal properties =====
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

        // ===== Transaction properties =====
        PropertyDescriptor txnTypeProp = property(AtmSimulatorSampler.TRANSACTION_TYPE);
        txnTypeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        txnTypeProp.setValue(DEFAULT, AtmTransactionType.BALANCE_INQUIRY.name());
        txnTypeProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        txnTypeProp.setValue(NOT_OTHER, Boolean.TRUE);
        txnTypeProp.setValue(TAGS, TRANSACTION_TYPES);
        txnTypeProp.setDisplayName("Transaction Type");
        txnTypeProp.setShortDescription(
            "Type of ATM transaction. Use CUSTOM for full field control with JSON template.\n" +
            "WITHDRAWAL - Cash withdrawal (提款)\n" +
            "BALANCE_INQUIRY - Balance inquiry (餘額查詢)\n" +
            "TRANSFER - Fund transfer (轉帳)\n" +
            "DEPOSIT - Cash deposit (存款)\n" +
            "BILL_PAYMENT - Bill payment (繳費)\n" +
            "PIN_CHANGE - PIN change (密碼變更)\n" +
            "MINI_STATEMENT - Transaction history (交易明細)\n" +
            "CARDLESS_WITHDRAWAL - Cardless withdrawal (無卡提款)\n" +
            "AUTHORIZATION - Pre-authorization (授權)\n" +
            "REVERSAL - Transaction reversal (沖正)\n" +
            "SIGN_ON/SIGN_OFF/ECHO_TEST - Network management\n" +
            "CUSTOM - Custom message with JSON template"
        );

        PropertyDescriptor mtiOverrideProp = property(AtmSimulatorSampler.MTI_OVERRIDE);
        mtiOverrideProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        mtiOverrideProp.setValue(DEFAULT, "");
        mtiOverrideProp.setDisplayName("MTI Override");
        mtiOverrideProp.setShortDescription(
            "Override the default MTI for this transaction type.\n" +
            "Leave empty to use the default MTI.\n" +
            "Examples: 0100, 0200, 0400, 0800"
        );

        PropertyDescriptor processingCodeProp = property(AtmSimulatorSampler.PROCESSING_CODE_OVERRIDE);
        processingCodeProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        processingCodeProp.setValue(DEFAULT, "");
        processingCodeProp.setDisplayName("Processing Code Override");
        processingCodeProp.setShortDescription(
            "Override the default Processing Code (Field 3).\n" +
            "Leave empty to use the default for transaction type.\n" +
            "Examples: 010000 (withdrawal), 310000 (balance), 400000 (transfer)"
        );

        PropertyDescriptor amountProp = property(AtmSimulatorSampler.AMOUNT);
        amountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        amountProp.setValue(DEFAULT, "");
        amountProp.setDisplayName("Amount");
        amountProp.setShortDescription(
            "Transaction amount in cents (Field 4).\n" +
            "Example: 100000 = $1,000.00\n" +
            "Supports JMeter variables: ${amount}"
        );

        PropertyDescriptor destAccountProp = property(AtmSimulatorSampler.DESTINATION_ACCOUNT);
        destAccountProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        destAccountProp.setValue(DEFAULT, "");
        destAccountProp.setDisplayName("Destination Account");
        destAccountProp.setShortDescription("Destination account number for transfer transactions (Field 103).");

        // ===== Card properties =====
        PropertyDescriptor cardNumberProp = property(AtmSimulatorSampler.CARD_NUMBER);
        cardNumberProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        cardNumberProp.setValue(DEFAULT, "");
        cardNumberProp.setDisplayName("Card Number");
        cardNumberProp.setShortDescription(
            "Primary Account Number (Field 2).\n" +
            "Example: 4716123456781234\n" +
            "Supports JMeter variables: ${cardNumber}"
        );

        // ===== Security properties =====
        PropertyDescriptor enablePinBlockProp = property(AtmSimulatorSampler.ENABLE_PIN_BLOCK);
        enablePinBlockProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        enablePinBlockProp.setValue(DEFAULT, Boolean.FALSE);
        enablePinBlockProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        enablePinBlockProp.setValue(NOT_OTHER, Boolean.TRUE);
        enablePinBlockProp.setDisplayName("Enable PIN Block");
        enablePinBlockProp.setShortDescription("Enable PIN Block field (Field 52) in the transaction.");

        PropertyDescriptor pinBlockProp = property(AtmSimulatorSampler.PIN_BLOCK);
        pinBlockProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        pinBlockProp.setValue(DEFAULT, "");
        pinBlockProp.setDisplayName("PIN Block");
        pinBlockProp.setShortDescription(
            "Encrypted PIN Block (Field 52).\n" +
            "16 hex characters. Example: 1234567890ABCDEF\n" +
            "Supports JMeter variables: ${pinBlock}"
        );

        // ===== Advanced properties =====
        PropertyDescriptor customFieldsProp = property(AtmSimulatorSampler.CUSTOM_FIELDS);
        customFieldsProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        customFieldsProp.setValue(DEFAULT, "");
        customFieldsProp.setPropertyEditorClass(TextAreaEditor.class);
        customFieldsProp.setDisplayName("Custom Fields");
        customFieldsProp.setShortDescription(
            "Additional ISO 8583 fields (highest priority, overrides all).\n" +
            "Format: field:value;field:value\n" +
            "Example: 14:2512;35:4716...1234=2512;48:Custom Data\n" +
            "Supports JMeter variables: ${varName}"
        );

        PropertyDescriptor messageTemplateProp = property(AtmSimulatorSampler.MESSAGE_TEMPLATE);
        messageTemplateProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        messageTemplateProp.setValue(DEFAULT, "");
        messageTemplateProp.setPropertyEditorClass(TextAreaEditor.class);
        messageTemplateProp.setDisplayName("Message Template (JSON)");
        messageTemplateProp.setShortDescription(
            "JSON template for CUSTOM transaction type.\n" +
            "Provides full control over all ISO 8583 fields.\n\n" +
            "Format:\n" +
            "{\n" +
            "  \"fields\": {\n" +
            "    \"2\": \"4111111111111111\",\n" +
            "    \"3\": \"010000\",\n" +
            "    \"4\": \"000000100000\",\n" +
            "    \"22\": \"051\",\n" +
            "    \"...\": \"...\"\n" +
            "  }\n" +
            "}\n\n" +
            "Supports JMeter variables: ${varName}"
        );

        // ===== RAW Mode properties =====
        PropertyDescriptor rawMessageFormatProp = property(AtmSimulatorSampler.RAW_MESSAGE_FORMAT);
        rawMessageFormatProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        rawMessageFormatProp.setValue(DEFAULT, RawMessageFormat.HEX.name());
        rawMessageFormatProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        rawMessageFormatProp.setValue(NOT_OTHER, Boolean.TRUE);
        rawMessageFormatProp.setValue(TAGS, RAW_MESSAGE_FORMATS);
        rawMessageFormatProp.setDisplayName("RAW Message Format");
        rawMessageFormatProp.setShortDescription(
            "Encoding format for RAW message data (only used when Protocol Type = RAW).\n" +
            "HEX - Hexadecimal encoding (e.g., 0200603800...)\n" +
            "BASE64 - Base64 encoding\n" +
            "TEXT - Plain text / UTF-8"
        );

        PropertyDescriptor rawMessageDataProp = property(AtmSimulatorSampler.RAW_MESSAGE_DATA);
        rawMessageDataProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        rawMessageDataProp.setValue(DEFAULT, "");
        rawMessageDataProp.setPropertyEditorClass(TextAreaEditor.class);
        rawMessageDataProp.setDisplayName("RAW Message Data");
        rawMessageDataProp.setShortDescription(
            "Raw message data (only used when Protocol Type = RAW).\n" +
            "Enter data in the format specified by 'RAW Message Format'.\n" +
            "HEX example: 0200603800000000000002000000000010004111111111111111\n" +
            "Supports JMeter variables: ${varName}"
        );

        PropertyDescriptor expectResponseProp = property(AtmSimulatorSampler.EXPECT_RESPONSE);
        expectResponseProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        expectResponseProp.setValue(DEFAULT, Boolean.TRUE);
        expectResponseProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        expectResponseProp.setValue(NOT_OTHER, Boolean.TRUE);
        expectResponseProp.setDisplayName("Expect Response");
        expectResponseProp.setShortDescription(
            "Wait for response after sending (RAW mode only).\n" +
            "true - Wait for response (default)\n" +
            "false - Fire and forget (no response expected)"
        );

        PropertyDescriptor responseMatchPatternProp = property(AtmSimulatorSampler.RESPONSE_MATCH_PATTERN);
        responseMatchPatternProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        responseMatchPatternProp.setValue(DEFAULT, "");
        responseMatchPatternProp.setDisplayName("Response Match Pattern");
        responseMatchPatternProp.setShortDescription(
            "Pattern to validate response success (RAW mode only).\n" +
            "Leave empty to accept any response.\n" +
            "Formats:\n" +
            "  HEX:0210 - Response starts with hex 0210\n" +
            "  CONTAINS:OK - Response text contains 'OK'\n" +
            "  REGEX:.* - Response matches regex\n" +
            "  LENGTH:100 - Response is exactly 100 bytes"
        );

        // ===== Template Config properties =====
        PropertyDescriptor useTemplateConfigProp = property(AtmSimulatorSampler.USE_TEMPLATE_CONFIG);
        useTemplateConfigProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        useTemplateConfigProp.setValue(DEFAULT, Boolean.FALSE);
        useTemplateConfigProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        useTemplateConfigProp.setValue(NOT_OTHER, Boolean.TRUE);
        useTemplateConfigProp.setDisplayName("Use Template Config");
        useTemplateConfigProp.setShortDescription(
            "Use TransactionTemplateConfig for message generation.\n" +
            "When enabled, uses predefined templates instead of Transaction Type.\n" +
            "Templates provide flexible field configuration with variable substitution."
        );

        PropertyDescriptor templateNameProp = property(AtmSimulatorSampler.TEMPLATE_NAME);
        templateNameProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        templateNameProp.setValue(DEFAULT, "Withdrawal");
        templateNameProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        templateNameProp.setValue(NOT_OTHER, Boolean.TRUE);
        templateNameProp.setValue(TAGS, TEMPLATE_NAMES);
        templateNameProp.setDisplayName("Template Name");
        templateNameProp.setShortDescription(
            "Name of the template to use (only when Use Template Config is enabled).\n" +
            "Built-in templates:\n" +
            "  Withdrawal - Cash withdrawal (提款)\n" +
            "  Balance Inquiry - Balance inquiry (餘額查詢)\n" +
            "  Fund Transfer - Interbank transfer (轉帳)\n" +
            "  Bill Payment - Bill payment (繳費)\n" +
            "  Sign On/Sign Off/Echo Test - Network management\n" +
            "  Reversal - Transaction reversal (沖正)"
        );

        // ===== Generic Schema Mode properties =====
        PropertyDescriptor schemaSourceProp = property(AtmSimulatorSampler.SCHEMA_SOURCE);
        schemaSourceProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        schemaSourceProp.setValue(DEFAULT, SchemaSource.FILE.name());
        schemaSourceProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        schemaSourceProp.setValue(NOT_OTHER, Boolean.TRUE);
        schemaSourceProp.setValue(TAGS, SCHEMA_SOURCES);
        schemaSourceProp.setDisplayName("Schema Source");
        schemaSourceProp.setShortDescription(
            "Source of message schema (only when Protocol Type = GENERIC_SCHEMA).\n" +
            "FILE - Load schema from external JSON file\n" +
            "INLINE - Use inline JSON schema definition\n" +
            "PRESET - Use built-in schema for common ATM protocols"
        );

        PropertyDescriptor schemaFileProp = property(AtmSimulatorSampler.SCHEMA_FILE);
        schemaFileProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        schemaFileProp.setValue(DEFAULT, "");
        schemaFileProp.setDisplayName("Schema File Path");
        schemaFileProp.setShortDescription(
            "Path to JSON schema file (only when Schema Source = FILE).\n" +
            "Absolute or relative path to the schema JSON file.\n" +
            "Example: /path/to/my-protocol-schema.json\n" +
            "Supports JMeter variables: ${schemaPath}"
        );

        PropertyDescriptor presetSchemaProp = property(AtmSimulatorSampler.PRESET_SCHEMA);
        presetSchemaProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        presetSchemaProp.setValue(DEFAULT, PresetSchema.NCR_NDC.name());
        presetSchemaProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        presetSchemaProp.setValue(NOT_OTHER, Boolean.TRUE);
        presetSchemaProp.setValue(TAGS, PRESET_SCHEMAS);
        presetSchemaProp.setDisplayName("Preset Schema");
        presetSchemaProp.setShortDescription(
            "Built-in schema to use (only when Schema Source = PRESET).\n" +
            "NCR_NDC - NCR NDC ATM protocol\n" +
            "DIEBOLD_91X - Diebold 91x ATM protocol\n" +
            "WINCOR_DDC - Wincor Nixdorf DDC protocol\n" +
            "FISC_ATM - Taiwan FISC ATM message format\n" +
            "ISO8583_GENERIC - Generic ISO 8583 format via schema"
        );

        PropertyDescriptor schemaContentProp = property(AtmSimulatorSampler.SCHEMA_CONTENT);
        schemaContentProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        schemaContentProp.setValue(DEFAULT, "");
        schemaContentProp.setPropertyEditorClass(TextAreaEditor.class);
        schemaContentProp.setDisplayName("Inline Schema (JSON)");
        schemaContentProp.setShortDescription(
            "Inline JSON schema definition (only when Schema Source = INLINE).\n\n" +
            "Example:\n" +
            "{\n" +
            "  \"name\": \"My Protocol\",\n" +
            "  \"fields\": [\n" +
            "    { \"id\": \"command\", \"length\": 2, \"encoding\": \"ASCII\" },\n" +
            "    { \"id\": \"terminalId\", \"length\": 8, \"encoding\": \"ASCII\" },\n" +
            "    { \"id\": \"amount\", \"length\": 12, \"encoding\": \"BCD\" }\n" +
            "  ]\n" +
            "}"
        );

        PropertyDescriptor fieldValuesProp = property(AtmSimulatorSampler.FIELD_VALUES);
        fieldValuesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fieldValuesProp.setValue(DEFAULT, "");
        fieldValuesProp.setPropertyEditorClass(TextAreaEditor.class);
        fieldValuesProp.setDisplayName("Field Values (JSON)");
        fieldValuesProp.setShortDescription(
            "JSON object with field values (only when Protocol Type = GENERIC_SCHEMA).\n" +
            "Keys are field IDs from the schema, values are the data to send.\n\n" +
            "Example:\n" +
            "{\n" +
            "  \"command\": \"11\",\n" +
            "  \"terminalId\": \"${atmId}\",\n" +
            "  \"amount\": \"${amount}\",\n" +
            "  \"track2\": \"4111111111111111=2512\"\n" +
            "}\n\n" +
            "Variables (auto-generated if not provided):\n" +
            "  ${stan} - System Trace Audit Number\n" +
            "  ${time} - Local time (HHmmss)\n" +
            "  ${date} - Local date (MMdd)\n" +
            "  ${rrn} - Retrieval Reference Number\n" +
            "Supports JMeter variables for all values."
        );
    }
}
