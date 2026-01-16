package com.fep.jmeter.assertion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FieldMatcher.
 */
class FieldMatcherTest {

    @Nested
    @DisplayName("Exact Match ($eq)")
    class ExactMatchTests {

        @Test
        @DisplayName("should match equal strings")
        void shouldMatchEqualStrings() {
            FieldMatcher matcher = FieldMatcher.fromValue("responseCode", "00");
            FieldMatcher.MatchResult result = matcher.match("00");

            assertTrue(result.isSuccess());
            assertNull(result.getFailureMessage());
        }

        @Test
        @DisplayName("should fail on different strings")
        void shouldFailOnDifferentStrings() {
            FieldMatcher matcher = FieldMatcher.fromValue("responseCode", "00");
            FieldMatcher.MatchResult result = matcher.match("51");

            assertFalse(result.isSuccess());
            assertTrue(result.getFailureMessage().contains("responseCode"));
            assertTrue(result.getFailureMessage().contains("expected [00]"));
            assertTrue(result.getFailureMessage().contains("actual [51]"));
        }

        @Test
        @DisplayName("should fail when actual is null")
        void shouldFailWhenActualIsNull() {
            FieldMatcher matcher = FieldMatcher.fromValue("responseCode", "00");
            FieldMatcher.MatchResult result = matcher.match(null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailureMessage().contains("not present or null"));
        }

        @Test
        @DisplayName("should match with explicit $eq operator")
        void shouldMatchWithExplicitEqOperator() {
            FieldMatcher matcher = FieldMatcher.fromValue("mti", Map.of("$eq", "0210"));
            FieldMatcher.MatchResult result = matcher.match("0210");

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Not Equal ($ne)")
    class NotEqualTests {

        @Test
        @DisplayName("should succeed when values are different")
        void shouldSucceedWhenDifferent() {
            FieldMatcher matcher = FieldMatcher.fromValue("responseCode", Map.of("$ne", "99"));
            FieldMatcher.MatchResult result = matcher.match("00");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should fail when values are equal")
        void shouldFailWhenEqual() {
            FieldMatcher matcher = FieldMatcher.fromValue("responseCode", Map.of("$ne", "99"));
            FieldMatcher.MatchResult result = matcher.match("99");

            assertFalse(result.isSuccess());
            assertTrue(result.getFailureMessage().contains("not 99"));
        }
    }

    @Nested
    @DisplayName("Regex Match ($regex)")
    class RegexTests {

        @Test
        @DisplayName("should match valid regex pattern")
        void shouldMatchValidRegex() {
            FieldMatcher matcher = FieldMatcher.fromValue("amount", Map.of("$regex", "^\\d{12}$"));
            FieldMatcher.MatchResult result = matcher.match("000000001000");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should fail on non-matching regex")
        void shouldFailOnNonMatchingRegex() {
            FieldMatcher matcher = FieldMatcher.fromValue("amount", Map.of("$regex", "^\\d{12}$"));
            FieldMatcher.MatchResult result = matcher.match("ABC123");

            assertFalse(result.isSuccess());
            assertTrue(result.getFailureMessage().contains("regex"));
        }

        @Test
        @DisplayName("should handle invalid regex gracefully")
        void shouldHandleInvalidRegex() {
            FieldMatcher matcher = FieldMatcher.fromValue("field", Map.of("$regex", "[invalid("));
            FieldMatcher.MatchResult result = matcher.match("test");

            assertFalse(result.isSuccess());
            assertTrue(result.getFailureMessage().contains("PatternSyntaxException"));
        }
    }

    @Nested
    @DisplayName("Contains Match ($contains)")
    class ContainsTests {

        @Test
        @DisplayName("should match when substring present")
        void shouldMatchWhenContains() {
            FieldMatcher matcher = FieldMatcher.fromValue("terminalId", Map.of("$contains", "ATM"));
            FieldMatcher.MatchResult result = matcher.match("ATM12345");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should match when substring in middle")
        void shouldMatchSubstringInMiddle() {
            FieldMatcher matcher = FieldMatcher.fromValue("description", Map.of("$contains", "BANK"));
            FieldMatcher.MatchResult result = matcher.match("MY BANK BRANCH");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should fail when substring not present")
        void shouldFailWhenNotContains() {
            FieldMatcher matcher = FieldMatcher.fromValue("terminalId", Map.of("$contains", "ATM"));
            FieldMatcher.MatchResult result = matcher.match("POS12345");

            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("StartsWith Match ($startsWith)")
    class StartsWithTests {

        @Test
        @DisplayName("should match when starts with prefix")
        void shouldMatchStartsWith() {
            FieldMatcher matcher = FieldMatcher.fromValue("pan", Map.of("$startsWith", "4111"));
            FieldMatcher.MatchResult result = matcher.match("4111111111111111");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should fail when not starts with prefix")
        void shouldFailNotStartsWith() {
            FieldMatcher matcher = FieldMatcher.fromValue("pan", Map.of("$startsWith", "4111"));
            FieldMatcher.MatchResult result = matcher.match("5500111111111111");

            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("EndsWith Match ($endsWith)")
    class EndsWithTests {

        @Test
        @DisplayName("should match when ends with suffix")
        void shouldMatchEndsWith() {
            FieldMatcher matcher = FieldMatcher.fromValue("responseCode", Map.of("$endsWith", "00"));
            FieldMatcher.MatchResult result = matcher.match("0100");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should fail when not ends with suffix")
        void shouldFailNotEndsWith() {
            FieldMatcher matcher = FieldMatcher.fromValue("responseCode", Map.of("$endsWith", "00"));
            FieldMatcher.MatchResult result = matcher.match("0199");

            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Value Parsing")
    class ValueParsingTests {

        @Test
        @DisplayName("should handle null expected value")
        void shouldHandleNullExpected() {
            FieldMatcher matcher = FieldMatcher.fromValue("field", null);
            FieldMatcher.MatchResult result = matcher.match(null);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should convert number to string")
        void shouldConvertNumberToString() {
            FieldMatcher matcher = FieldMatcher.fromValue("amount", 12345);
            FieldMatcher.MatchResult result = matcher.match("12345");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should handle map without valid operator")
        void shouldHandleMapWithoutOperator() {
            FieldMatcher matcher = FieldMatcher.fromValue("field", Map.of("unknown", "value"));

            // Should default to string representation
            assertNotNull(matcher);
            assertEquals(FieldMatcher.MatchOperator.EQ, matcher.getOperator());
        }
    }
}
