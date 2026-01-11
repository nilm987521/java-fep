package com.fep.jmeter.sampler;

import com.fep.jmeter.config.TemplateSource;
import com.fep.jmeter.config.TransactionTemplate;
import com.fep.jmeter.config.TransactionTemplateConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TemplateSampler and TransactionTemplateConfig.
 */
@DisplayName("TemplateSampler Integration Tests")
class TemplateSamplerIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        TransactionTemplateConfig.clearCache();
    }

    @AfterEach
    void tearDown() {
        TransactionTemplateConfig.clearCache();
    }

    // ===== TransactionTemplateConfig Tests =====

    @Nested
    @DisplayName("TransactionTemplateConfig Tests")
    class TransactionTemplateConfigTests {

        @Test
        @DisplayName("Should load common templates by default")
        void shouldLoadCommonTemplatesByDefault() {
            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.COMMON.name());

            List<TransactionTemplate> templates = config.getTemplates();

            assertThat(templates).isNotEmpty();
            assertThat(templates).hasSizeGreaterThanOrEqualTo(8);
            assertThat(config.getTemplateNames())
                .contains("Withdrawal", "Balance Inquiry", "Echo Test");
        }

        @Test
        @DisplayName("Should get template by name")
        void shouldGetTemplateByName() {
            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.COMMON.name());

            var template = config.getTemplate("Withdrawal");

            assertThat(template).isPresent();
            assertThat(template.get().getMti()).isEqualTo("0200");
            assertThat(template.get().getFields().get(3)).isEqualTo("010000");
        }

        @Test
        @DisplayName("Should return empty for non-existent template")
        void shouldReturnEmptyForNonExistentTemplate() {
            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.COMMON.name());

            var template = config.getTemplate("NonExistent");

            assertThat(template).isEmpty();
        }

        @Test
        @DisplayName("Should load templates from file")
        void shouldLoadTemplatesFromFile() throws IOException {
            // Create test template file
            String json = """
                [
                  {
                    "name": "Custom Template 1",
                    "mti": "0200",
                    "fields": {"3": "010000", "4": "${amount}"}
                  },
                  {
                    "name": "Custom Template 2",
                    "mti": "0400",
                    "fields": {"3": "010000"}
                  }
                ]
                """;
            Path templateFile = tempDir.resolve("templates.json");
            Files.writeString(templateFile, json);

            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.FILE.name());
            config.setTemplateFile(templateFile.toString());
            config.setUseCommonTemplates(false);

            List<TransactionTemplate> templates = config.getTemplates();

            assertThat(templates).hasSize(2);
            assertThat(templates.get(0).getName()).isEqualTo("Custom Template 1");
            assertThat(templates.get(1).getName()).isEqualTo("Custom Template 2");
        }

        @Test
        @DisplayName("Should load inline templates")
        void shouldLoadInlineTemplates() {
            String json = """
                [
                  {
                    "name": "Inline Template",
                    "mti": "0200",
                    "fields": {"3": "990000"}
                  }
                ]
                """;

            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.INLINE.name());
            config.setInlineTemplates(json);
            config.setUseCommonTemplates(false);

            List<TransactionTemplate> templates = config.getTemplates();

            assertThat(templates).hasSize(1);
            assertThat(templates.get(0).getName()).isEqualTo("Inline Template");
            assertThat(templates.get(0).getFields().get(3)).isEqualTo("990000");
        }

        @Test
        @DisplayName("Should load single inline template")
        void shouldLoadSingleInlineTemplate() {
            String json = """
                {
                  "name": "Single Template",
                  "mti": "0200",
                  "fields": {"3": "110000"}
                }
                """;

            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.INLINE.name());
            config.setInlineTemplates(json);
            config.setUseCommonTemplates(false);

            List<TransactionTemplate> templates = config.getTemplates();

            assertThat(templates).hasSize(1);
            assertThat(templates.get(0).getName()).isEqualTo("Single Template");
        }

        @Test
        @DisplayName("Should combine custom and common templates")
        void shouldCombineCustomAndCommonTemplates() {
            String json = """
                [{"name": "Custom Only", "mti": "0200", "fields": {"3": "999999"}}]
                """;

            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.INLINE.name());
            config.setInlineTemplates(json);
            config.setUseCommonTemplates(true);

            List<TransactionTemplate> templates = config.getTemplates();

            // Should have custom template plus common templates
            assertThat(templates.size()).isGreaterThan(8);
            assertThat(config.getTemplateNames()).contains("Custom Only", "Withdrawal", "Echo Test");
        }

        @Test
        @DisplayName("Should not duplicate templates when combining")
        void shouldNotDuplicateTemplatesWhenCombining() {
            // Custom template with same name as common template
            String json = """
                [{"name": "Withdrawal", "mti": "0200", "fields": {"3": "999999"}}]
                """;

            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.INLINE.name());
            config.setInlineTemplates(json);
            config.setUseCommonTemplates(true);

            List<TransactionTemplate> templates = config.getTemplates();

            // Count Withdrawal templates
            long withdrawalCount = templates.stream()
                .filter(t -> "Withdrawal".equals(t.getName()))
                .count();

            assertThat(withdrawalCount).isEqualTo(1);
            // Custom should override common
            var withdrawal = config.getTemplate("Withdrawal");
            assertThat(withdrawal).isPresent();
            assertThat(withdrawal.get().getFields().get(3)).isEqualTo("999999");
        }

        @Test
        @DisplayName("Should auto-generate STAN and timestamp")
        void shouldAutoGenerateStanAndTimestamp() {
            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.COMMON.name());
            config.setAutoGenerateStan(true);
            config.setAutoGenerateTimestamp(true);

            var template = config.getTemplate("Echo Test");
            assertThat(template).isPresent();

            // Note: createMessageFromTemplate would require JMeter context
            // This test verifies configuration is set correctly
            assertThat(config.isAutoGenerateStan()).isTrue();
            assertThat(config.isAutoGenerateTimestamp()).isTrue();
        }

        @Test
        @DisplayName("Should cache templates")
        void shouldCacheTemplates() {
            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.COMMON.name());

            // First call
            List<TransactionTemplate> templates1 = config.getTemplates();
            // Second call
            List<TransactionTemplate> templates2 = config.getTemplates();

            // Should be same instance (cached)
            assertThat(templates1).isSameAs(templates2);
        }

        @Test
        @DisplayName("Should clear cache")
        void shouldClearCache() {
            TransactionTemplateConfig config = new TransactionTemplateConfig();
            config.setTemplateSource(TemplateSource.COMMON.name());

            List<TransactionTemplate> templates1 = config.getTemplates();
            TransactionTemplateConfig.clearCache();
            List<TransactionTemplate> templates2 = config.getTemplates();

            // Should be different instance after cache clear
            assertThat(templates1).isNotSameAs(templates2);
        }
    }

    // ===== TemplateSampler Tests =====

    @Nested
    @DisplayName("TemplateSampler Tests")
    class TemplateSamplerTests {

        @Test
        @DisplayName("Should have default property values")
        void shouldHaveDefaultPropertyValues() {
            TemplateSampler sampler = new TemplateSampler();

            assertThat(sampler.getTargetHost()).isEqualTo("localhost");
            assertThat(sampler.getTargetPort()).isEqualTo(8080);
            assertThat(sampler.getConnectionTimeout()).isEqualTo(10000);
            assertThat(sampler.getReadTimeout()).isEqualTo(30000);
            assertThat(sampler.getTemplateName()).isEqualTo("Echo Test");
        }

        @Test
        @DisplayName("Should set and get properties")
        void shouldSetAndGetProperties() {
            TemplateSampler sampler = new TemplateSampler();

            sampler.setTargetHost("192.168.1.100");
            sampler.setTargetPort(9000);
            sampler.setConnectionTimeout(5000);
            sampler.setReadTimeout(15000);
            sampler.setTemplateName("Withdrawal");
            sampler.setMtiOverride("0100");
            sampler.setAmount("100000");
            sampler.setCardNumber("4111111111111111");
            sampler.setTerminalId("ATM00001");
            sampler.setBankCode("012");
            sampler.setSourceAccount("1234567890");
            sampler.setDestAccount("0987654321");
            sampler.setCustomFields("48:CustomData;62:MoreData");

            assertThat(sampler.getTargetHost()).isEqualTo("192.168.1.100");
            assertThat(sampler.getTargetPort()).isEqualTo(9000);
            assertThat(sampler.getConnectionTimeout()).isEqualTo(5000);
            assertThat(sampler.getReadTimeout()).isEqualTo(15000);
            assertThat(sampler.getTemplateName()).isEqualTo("Withdrawal");
            assertThat(sampler.getMtiOverride()).isEqualTo("0100");
            assertThat(sampler.getAmount()).isEqualTo("100000");
            assertThat(sampler.getCardNumber()).isEqualTo("4111111111111111");
            assertThat(sampler.getTerminalId()).isEqualTo("ATM00001");
            assertThat(sampler.getBankCode()).isEqualTo("012");
            assertThat(sampler.getSourceAccount()).isEqualTo("1234567890");
            assertThat(sampler.getDestAccount()).isEqualTo("0987654321");
            assertThat(sampler.getCustomFields()).isEqualTo("48:CustomData;62:MoreData");
        }

        @Test
        @DisplayName("Should have default sampler name")
        void shouldHaveDefaultSamplerName() {
            TemplateSampler sampler = new TemplateSampler();

            assertThat(sampler.getName()).isEqualTo("Template Sampler");
        }

        @Test
        @DisplayName("Should implement TestStateListener")
        void shouldImplementTestStateListener() {
            TemplateSampler sampler = new TemplateSampler();

            // Should not throw
            assertThatCode(() -> {
                sampler.testStarted();
                sampler.testStarted("localhost");
                sampler.testEnded();
                sampler.testEnded("localhost");
            }).doesNotThrowAnyException();
        }
    }

    // ===== TemplateSource Tests =====

    @Nested
    @DisplayName("TemplateSource Enum Tests")
    class TemplateSourceTests {

        @Test
        @DisplayName("Should have correct enum values")
        void shouldHaveCorrectEnumValues() {
            assertThat(TemplateSource.values())
                .containsExactly(TemplateSource.COMMON, TemplateSource.FILE, TemplateSource.INLINE);
        }

        @Test
        @DisplayName("Should return names array")
        void shouldReturnNamesArray() {
            String[] names = TemplateSource.names();

            assertThat(names).containsExactly("COMMON", "FILE", "INLINE");
        }

        @Test
        @DisplayName("Should parse from string")
        void shouldParseFromString() {
            assertThat(TemplateSource.fromString("COMMON")).isEqualTo(TemplateSource.COMMON);
            assertThat(TemplateSource.fromString("FILE")).isEqualTo(TemplateSource.FILE);
            assertThat(TemplateSource.fromString("INLINE")).isEqualTo(TemplateSource.INLINE);
            assertThat(TemplateSource.fromString("file")).isEqualTo(TemplateSource.FILE);
        }

        @Test
        @DisplayName("Should return default for invalid string")
        void shouldReturnDefaultForInvalidString() {
            assertThat(TemplateSource.fromString(null)).isEqualTo(TemplateSource.COMMON);
            assertThat(TemplateSource.fromString("")).isEqualTo(TemplateSource.COMMON);
            assertThat(TemplateSource.fromString("INVALID")).isEqualTo(TemplateSource.COMMON);
        }
    }

    // ===== Common Templates Tests =====

    @Nested
    @DisplayName("Common Templates Tests")
    class CommonTemplatesTests {

        @Test
        @DisplayName("Should have withdrawal template with correct fields")
        void shouldHaveWithdrawalTemplateWithCorrectFields() {
            TransactionTemplate template = TransactionTemplate.CommonTemplates.withdrawal();

            assertThat(template.getName()).isEqualTo("Withdrawal");
            assertThat(template.getMti()).isEqualTo("0200");
            assertThat(template.getFields()).containsKey(3);
            assertThat(template.getFields()).containsKey(4);
            assertThat(template.getFields()).containsKey(11);
            assertThat(template.getFields().get(3)).isEqualTo("010000");
            assertThat(template.getFields().get(4)).isEqualTo("${amount}");
        }

        @Test
        @DisplayName("Should have transfer template with account fields")
        void shouldHaveTransferTemplateWithAccountFields() {
            TransactionTemplate template = TransactionTemplate.CommonTemplates.transfer();

            assertThat(template.getName()).isEqualTo("Fund Transfer");
            assertThat(template.getFields()).containsKey(102);
            assertThat(template.getFields()).containsKey(103);
            assertThat(template.getFields().get(102)).isEqualTo("${sourceAccount}");
            assertThat(template.getFields().get(103)).isEqualTo("${destAccount}");
        }

        @Test
        @DisplayName("Should have network management templates")
        void shouldHaveNetworkManagementTemplates() {
            TransactionTemplate signOn = TransactionTemplate.CommonTemplates.signOn();
            TransactionTemplate signOff = TransactionTemplate.CommonTemplates.signOff();
            TransactionTemplate echoTest = TransactionTemplate.CommonTemplates.echoTest();

            assertThat(signOn.getMti()).isEqualTo("0800");
            assertThat(signOff.getMti()).isEqualTo("0800");
            assertThat(echoTest.getMti()).isEqualTo("0800");

            assertThat(signOn.getFields().get(70)).isEqualTo("001");
            assertThat(signOff.getFields().get(70)).isEqualTo("002");
            assertThat(echoTest.getFields().get(70)).isEqualTo("301");
        }

        @Test
        @DisplayName("Should have reversal template")
        void shouldHaveReversalTemplate() {
            TransactionTemplate template = TransactionTemplate.CommonTemplates.reversal();

            assertThat(template.getName()).isEqualTo("Reversal");
            assertThat(template.getMti()).isEqualTo("0400");
            assertThat(template.getFields()).containsKey(37); // Original RRN
            assertThat(template.getFields()).containsKey(90); // Original data elements
        }

        @Test
        @DisplayName("Should have bill payment template")
        void shouldHaveBillPaymentTemplate() {
            TransactionTemplate template = TransactionTemplate.CommonTemplates.billPayment();

            assertThat(template.getName()).isEqualTo("Bill Payment");
            assertThat(template.getMti()).isEqualTo("0200");
            assertThat(template.getFields().get(3)).isEqualTo("500000");
            assertThat(template.getFields()).containsKey(48); // Biller code
            assertThat(template.getFields()).containsKey(62); // Bill reference
        }
    }
}
