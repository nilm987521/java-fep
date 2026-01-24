package com.fep.message.generic.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DynamicExpressionChain - Chain of Responsibility pattern.
 */
class DynamicExpressionChainTest {

    private DynamicExpressionChain chain;

    @BeforeEach
    void setUp() {
        chain = DynamicExpressionChain.createDefault();
    }

    @Test
    void shouldCreateDefaultChainWithFourResolvers() {
        assertThat(chain.size()).isEqualTo(4);
    }

    @Test
    void shouldResolveNowExpression() {
        String result = chain.resolve("@NOW(yyyyMMdd)");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(result).isEqualTo(today);
    }

    @Test
    void shouldResolveDateExpression() {
        String result = chain.resolve("@DATE");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(result).isEqualTo(today);
    }

    @Test
    void shouldResolveTimeExpression() {
        String result = chain.resolve("@TIME");
        assertThat(result).matches("\\d{6}");
    }

    @Test
    void shouldResolveUuidExpression() {
        String result = chain.resolve("@UUID");
        assertThat(result).hasSize(12);
        assertThat(result).matches("[a-f0-9]{12}");
    }

    @Test
    void shouldResolveMixedExpressions() {
        String result = chain.resolve("TXN@DATE-@TIME");
        assertThat(result).matches("TXN\\d{8}-\\d{6}");
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(chain.resolve(null)).isNull();
    }

    @Test
    void shouldReturnSameValueWithoutAtSymbol() {
        String input = "STATIC_VALUE";
        assertThat(chain.resolve(input)).isEqualTo(input);
    }

    @Test
    void shouldPreserveUnknownExpressions() {
        String result = chain.resolve("@UNKNOWN(arg)");
        assertThat(result).isEqualTo("@UNKNOWN(arg)");
    }

    @Test
    void shouldAddCustomResolver() {
        // Given - add custom resolver
        chain.addResolver(new DynamicExpressionResolver() {
            @Override
            public boolean supports(String functionName) {
                return "CUSTOM".equalsIgnoreCase(functionName);
            }

            @Override
            public Object resolve(String functionName, Object args) {
                return "CUSTOM_VALUE_" + (args != null ? args : "");
            }
        });

        // When
        String result = chain.resolve("@CUSTOM(test)");

        // Then
        assertThat(result).isEqualTo("CUSTOM_VALUE_test");
        assertThat(chain.size()).isEqualTo(5);
    }

    @Test
    void shouldRemoveResolver() {
        // Given
        DynamicExpressionResolver customResolver = new DynamicExpressionResolver() {
            @Override
            public boolean supports(String functionName) {
                return "TEMP".equalsIgnoreCase(functionName);
            }

            @Override
            public Object resolve(String functionName, Object args) {
                return "TEMP_VALUE";
            }
        };
        chain.addResolver(customResolver);
        assertThat(chain.size()).isEqualTo(5);

        // When
        boolean removed = chain.removeResolver(customResolver);

        // Then
        assertThat(removed).isTrue();
        assertThat(chain.size()).isEqualTo(4);
    }

    @Test
    void shouldClearAllResolvers() {
        chain.clearResolvers();
        assertThat(chain.size()).isZero();
        // Unknown expressions are preserved
        assertThat(chain.resolve("@NOW(yyyyMMdd)")).isEqualTo("@NOW(yyyyMMdd)");
    }

    @Test
    void shouldSupportFluentApi() {
        DynamicExpressionChain newChain = new DynamicExpressionChain()
                .addResolver(new NowExpressionResolver())
                .addResolver(new DateExpressionResolver());

        assertThat(newChain.size()).isEqualTo(2);
    }

    @Test
    void shouldResolveExpressionWithEmptyArgs() {
        String result = chain.resolve("@NOW()");
        // Default format is yyyyMMddHHmmss (14 chars)
        assertThat(result).matches("\\d{14}");
    }

    @Test
    void shouldHandleCaseInsensitiveFunctionNames() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        assertThat(chain.resolve("@date")).isEqualTo(today);
        assertThat(chain.resolve("@DATE")).isEqualTo(today);
        assertThat(chain.resolve("@Date")).isEqualTo(today);
    }

    @Test
    void shouldResolveToObjectForSingleExpression() {
        Object result = chain.resolveToObject("@DATE");
        assertThat(result).isInstanceOf(String.class);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(result).isEqualTo(today);
    }

    @Test
    void shouldResolveToObjectReturnOriginalForUnknown() {
        Object result = chain.resolveToObject("@UNKNOWN");
        assertThat(result).isEqualTo("@UNKNOWN");
    }

    @Test
    void shouldResolveToObjectFallbackToStringForMixedContent() {
        Object result = chain.resolveToObject("PREFIX@DATE");
        assertThat(result).isInstanceOf(String.class);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(result).isEqualTo("PREFIX" + today);
    }

    @Test
    void shouldResolveToObjectReturnNullForNull() {
        assertThat(chain.resolveToObject(null)).isNull();
    }

    @Test
    void shouldResolveToObjectReturnSameForNoExpression() {
        assertThat(chain.resolveToObject("STATIC")).isEqualTo("STATIC");
    }

    @Test
    void shouldResolveToObjectWithCustomByteArrayResolver() {
        // Given - add resolver that returns byte[]
        chain.addResolver(new DynamicExpressionResolver() {
            @Override
            public boolean supports(String functionName) {
                return "HEX".equalsIgnoreCase(functionName);
            }

            @Override
            public Object resolve(String functionName, Object args) {
                if (args instanceof String hex) {
                    // Simple hex to byte array conversion
                    int len = hex.length();
                    byte[] data = new byte[len / 2];
                    for (int i = 0; i < len; i += 2) {
                        data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i + 1), 16));
                    }
                    return data;
                }
                return new byte[0];
            }
        });

        // When
        Object result = chain.resolveToObject("@HEX(AABBCC)");

        // Then
        assertThat(result).isInstanceOf(byte[].class);
        assertThat((byte[]) result).containsExactly((byte) 0xAA, (byte) 0xBB, (byte) 0xCC);
    }

    @Test
    void shouldUuidResolverAcceptLengthArg() {
        String result = chain.resolve("@UUID(8)");
        assertThat(result).hasSize(8);
        assertThat(result).matches("[a-f0-9]{8}");
    }
}
