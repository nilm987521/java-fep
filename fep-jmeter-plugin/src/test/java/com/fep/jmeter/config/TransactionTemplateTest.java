package com.fep.jmeter.config;

import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TransactionTemplate.
 */
@DisplayName("TransactionTemplate Tests")
class TransactionTemplateTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should create empty template")
    void shouldCreateEmptyTemplate() {
        TransactionTemplate template = new TransactionTemplate();

        assertThat(template.getName()).isNull();
        assertThat(template.getMti()).isNull();
        assertThat(template.getFields()).isEmpty();
        assertThat(template.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should create template with name and MTI")
    void shouldCreateTemplateWithNameAndMti() {
        TransactionTemplate template = new TransactionTemplate("Withdrawal", "0200");

        assertThat(template.getName()).isEqualTo("Withdrawal");
        assertThat(template.getMti()).isEqualTo("0200");
    }

    @Test
    @DisplayName("Should use builder pattern")
    void shouldUseBuilderPattern() {
        TransactionTemplate template = new TransactionTemplate()
            .withName("Test Transaction")
            .withMti("0200")
            .withDescription("A test transaction")
            .withField(3, "010000")
            .withField(4, "${amount}")
            .withValidationRules("REQUIRED:3,4")
            .withEnabled(true)
            .withPriority(10);

        assertThat(template.getName()).isEqualTo("Test Transaction");
        assertThat(template.getMti()).isEqualTo("0200");
        assertThat(template.getDescription()).isEqualTo("A test transaction");
        assertThat(template.getFields()).hasSize(2);
        assertThat(template.getFields().get(3)).isEqualTo("010000");
        assertThat(template.getFields().get(4)).isEqualTo("${amount}");
        assertThat(template.getValidationRules()).isEqualTo("REQUIRED:3,4");
        assertThat(template.isEnabled()).isTrue();
        assertThat(template.getPriority()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should create message from template")
    void shouldCreateMessageFromTemplate() {
        TransactionTemplate template = new TransactionTemplate("Withdrawal", "0200")
            .withField(3, "010000")
            .withField(4, "000000100000");

        Iso8583Message message = template.createMessage();

        assertThat(message.getMti()).isEqualTo("0200");
        assertThat(message.getFieldAsString(3)).isEqualTo("010000");
        assertThat(message.getFieldAsString(4)).isEqualTo("000000100000");
    }

    @Test
    @DisplayName("Should substitute variables in message")
    void shouldSubstituteVariablesInMessage() {
        TransactionTemplate template = new TransactionTemplate("Withdrawal", "0200")
            .withField(3, "010000")
            .withField(4, "${amount}")
            .withField(11, "${stan}")
            .withField(41, "${terminalId}");

        Map<String, String> variables = new HashMap<>();
        variables.put("amount", "000000050000");
        variables.put("stan", "123456");
        variables.put("terminalId", "ATM00001");

        Iso8583Message message = template.createMessage(variables);

        assertThat(message.getFieldAsString(3)).isEqualTo("010000");
        assertThat(message.getFieldAsString(4)).isEqualTo("000000050000");
        assertThat(message.getFieldAsString(11)).isEqualTo("123456");
        assertThat(message.getFieldAsString(41)).isEqualTo("ATM00001");
    }

    @Test
    @DisplayName("Should leave unmatched variables as is")
    void shouldLeaveUnmatchedVariablesAsIs() {
        TransactionTemplate template = new TransactionTemplate("Test", "0200")
            .withField(4, "${unknownVariable}");

        Map<String, String> variables = new HashMap<>();
        // Not providing unknownVariable

        Iso8583Message message = template.createMessage(variables);

        assertThat(message.getFieldAsString(4)).isEqualTo("${unknownVariable}");
    }

    @Test
    @DisplayName("Should serialize to JSON")
    void shouldSerializeToJson() {
        TransactionTemplate template = new TransactionTemplate("Withdrawal", "0200")
            .withDescription("Test")
            .withField(3, "010000");

        String json = template.toJson();

        assertThat(json).contains("\"name\": \"Withdrawal\"");
        assertThat(json).contains("\"mti\": \"0200\"");
        assertThat(json).contains("\"description\": \"Test\"");
    }

    @Test
    @DisplayName("Should deserialize from JSON")
    void shouldDeserializeFromJson() {
        String json = """
            {
              "name": "Balance Inquiry",
              "mti": "0200",
              "description": "Check account balance",
              "fields": {
                "3": "310000",
                "11": "${stan}"
              },
              "validationRules": "REQUIRED:3,11"
            }
            """;

        TransactionTemplate template = TransactionTemplate.fromJson(json);

        assertThat(template.getName()).isEqualTo("Balance Inquiry");
        assertThat(template.getMti()).isEqualTo("0200");
        assertThat(template.getDescription()).isEqualTo("Check account balance");
        assertThat(template.getFields()).hasSize(2);
        assertThat(template.getFields().get(3)).isEqualTo("310000");
        assertThat(template.getFields().get(11)).isEqualTo("${stan}");
        assertThat(template.getValidationRules()).isEqualTo("REQUIRED:3,11");
    }

    @Test
    @DisplayName("Should deserialize list from JSON")
    void shouldDeserializeListFromJson() {
        String json = """
            [
              {"name": "Template1", "mti": "0200"},
              {"name": "Template2", "mti": "0400"}
            ]
            """;

        List<TransactionTemplate> templates = TransactionTemplate.listFromJson(json);

        assertThat(templates).hasSize(2);
        assertThat(templates.get(0).getName()).isEqualTo("Template1");
        assertThat(templates.get(1).getName()).isEqualTo("Template2");
    }

    @Test
    @DisplayName("Should save and load from file")
    void shouldSaveAndLoadFromFile() throws IOException {
        TransactionTemplate template = new TransactionTemplate("FileTest", "0200")
            .withField(3, "010000");

        Path filePath = tempDir.resolve("template.json");
        template.saveToFile(filePath);

        TransactionTemplate loaded = TransactionTemplate.fromFile(filePath);

        assertThat(loaded.getName()).isEqualTo("FileTest");
        assertThat(loaded.getMti()).isEqualTo("0200");
        assertThat(loaded.getFields().get(3)).isEqualTo("010000");
    }

    @Test
    @DisplayName("Should save and load list from file")
    void shouldSaveAndLoadListFromFile() throws IOException {
        List<TransactionTemplate> templates = List.of(
            new TransactionTemplate("T1", "0200"),
            new TransactionTemplate("T2", "0400")
        );

        Path filePath = tempDir.resolve("templates.json");
        TransactionTemplate.saveListToFile(templates, filePath);

        List<TransactionTemplate> loaded = TransactionTemplate.listFromFile(filePath);

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).getName()).isEqualTo("T1");
        assertThat(loaded.get(1).getName()).isEqualTo("T2");
    }

    @Test
    @DisplayName("Should have proper equals and hashCode")
    void shouldHaveProperEqualsAndHashCode() {
        TransactionTemplate t1 = new TransactionTemplate("Test", "0200");
        TransactionTemplate t2 = new TransactionTemplate("Test", "0200");
        TransactionTemplate t3 = new TransactionTemplate("Other", "0200");

        assertThat(t1).isEqualTo(t2);
        assertThat(t1).isNotEqualTo(t3);
        assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
    }

    @Test
    @DisplayName("Should have descriptive toString")
    void shouldHaveDescriptiveToString() {
        TransactionTemplate template = new TransactionTemplate("Withdrawal", "0200")
            .withField(3, "010000")
            .withField(4, "000000100000");

        String str = template.toString();

        assertThat(str).contains("Withdrawal");
        assertThat(str).contains("0200");
        assertThat(str).contains("2"); // number of fields
    }

    // Common Templates Tests

    @Test
    @DisplayName("Should create withdrawal template")
    void shouldCreateWithdrawalTemplate() {
        TransactionTemplate template = TransactionTemplate.CommonTemplates.withdrawal();

        assertThat(template.getName()).isEqualTo("Withdrawal");
        assertThat(template.getMti()).isEqualTo("0200");
        assertThat(template.getFields().get(3)).isEqualTo("010000");
        assertThat(template.getFields().get(4)).isEqualTo("${amount}");
    }

    @Test
    @DisplayName("Should create balance inquiry template")
    void shouldCreateBalanceInquiryTemplate() {
        TransactionTemplate template = TransactionTemplate.CommonTemplates.balanceInquiry();

        assertThat(template.getName()).isEqualTo("Balance Inquiry");
        assertThat(template.getMti()).isEqualTo("0200");
        assertThat(template.getFields().get(3)).isEqualTo("310000");
    }

    @Test
    @DisplayName("Should create transfer template")
    void shouldCreateTransferTemplate() {
        TransactionTemplate template = TransactionTemplate.CommonTemplates.transfer();

        assertThat(template.getName()).isEqualTo("Fund Transfer");
        assertThat(template.getMti()).isEqualTo("0200");
        assertThat(template.getFields().get(3)).isEqualTo("400000");
        assertThat(template.getFields().get(102)).isEqualTo("${sourceAccount}");
        assertThat(template.getFields().get(103)).isEqualTo("${destAccount}");
    }

    @Test
    @DisplayName("Should create sign on template")
    void shouldCreateSignOnTemplate() {
        TransactionTemplate template = TransactionTemplate.CommonTemplates.signOn();

        assertThat(template.getName()).isEqualTo("Sign On");
        assertThat(template.getMti()).isEqualTo("0800");
        assertThat(template.getFields().get(70)).isEqualTo("001");
    }

    @Test
    @DisplayName("Should create echo test template")
    void shouldCreateEchoTestTemplate() {
        TransactionTemplate template = TransactionTemplate.CommonTemplates.echoTest();

        assertThat(template.getName()).isEqualTo("Echo Test");
        assertThat(template.getMti()).isEqualTo("0800");
        assertThat(template.getFields().get(70)).isEqualTo("301");
    }

    @Test
    @DisplayName("Should return all common templates")
    void shouldReturnAllCommonTemplates() {
        List<TransactionTemplate> templates = TransactionTemplate.CommonTemplates.all();

        assertThat(templates).hasSizeGreaterThanOrEqualTo(8);
        assertThat(templates).extracting(TransactionTemplate::getName)
            .contains("Withdrawal", "Balance Inquiry", "Fund Transfer",
                      "Bill Payment", "Sign On", "Sign Off", "Echo Test", "Reversal");
    }

    @Test
    @DisplayName("Should handle fields map with multiple entries")
    void shouldHandleFieldsMapWithMultipleEntries() {
        Map<Integer, String> fields = new HashMap<>();
        fields.put(3, "010000");
        fields.put(4, "000000100000");
        fields.put(11, "123456");
        fields.put(41, "ATM00001");

        TransactionTemplate template = new TransactionTemplate("Test", "0200")
            .withFields(fields);

        assertThat(template.getFields()).hasSize(4);
        assertThat(template.getFields().get(3)).isEqualTo("010000");
        assertThat(template.getFields().get(4)).isEqualTo("000000100000");
    }
}
