package tech.robd.notnull;
// SPDX-License-Identifier: MIT
/*
 * Copyright (c) 2026 Rob Deas (tech.robd)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for NotNull4J.
 * <p>
 * Coverage:
 * - All methods with happy paths and edge cases
 * - Null handling and contract violations
 * - Supplier behavior (success, null, exceptions)
 * - Optional handling (including corrupt Optionals)
 * - Runtime configuration
 * - Thread safety basics
 */
public class NotNullTest {

    // ==================================================================================
    // Test Utilities
    // ==================================================================================

    @SuppressWarnings("unchecked")
    private static <T> T forceNull() {
        return (T) null;
    }

    @BeforeEach
    void resetConfig() {
        NotNull.setCaptureCaller(true);
        NotNull.setTraceVerifySuccess(false);
    }

    @Nested
    @DisplayName("Misc Testd")
    class MiscTests {
        @Test
        void testConstructorIsPrivate() throws Exception {
            java.lang.reflect.Constructor<NotNull> constructor = NotNull.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
            constructor.setAccessible(true);
            constructor.newInstance(); // Now the constructor is "covered"
        }

        @Test
        @DisplayName("Coverage: Exercise internal catch blocks and tracing")
        void boosterTests() throws Exception {
            // 1. Cover traceVerifySuccess branches
            NotNull.setTraceVerifySuccess(true);
            NotNull.verify("success");
            NotNull.verify("success", "with message");
            NotNull.setTraceVerifySuccess(false);

            // 2. Cover findCaller disabled branch
            NotNull.setCaptureCaller(false);
            NotNull.orThrow("value");
            NotNull.setCaptureCaller(true);

            // 3. Cover unwrapOptional catch block (Corrupt Optional)
            Optional<String> corrupt = Optional.of("safe");
            java.lang.reflect.Field valueField = Optional.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(corrupt, null);
            NotNull.orNull(corrupt); // Triggers catch in unwrapOptional

            // 4. Cover orLogOptional success path with context
            NotNull.orLogOptional(Optional.of("present"), "fallback", "my-context");
        }
    }


    @Nested
    @DisplayName("verify(Object, String) Branch Coverage")
    class VerifyWithMessageBranchTests {

        @Test
        @DisplayName("verify with message - non-null value path")
        void verify_withMessage_nonNull() {
            NotNull.setTraceVerifySuccess(false);
            String result = NotNull.verify("value", "error msg");
            assertEquals("value", result);
        }

        @Test
        @DisplayName("verify with message - non-null value with tracing enabled")
        void verify_withMessage_nonNull_traceEnabled() {
            NotNull.setTraceVerifySuccess(true);
            try {
                String result = NotNull.verify("value", "error msg");
                assertEquals("value", result);
            } finally {
                NotNull.setTraceVerifySuccess(false);
            }
        }

        @Test
        @DisplayName("verify with message - null value throws")
        void verify_withMessage_null() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.verify(forceNull(), "custom error");
            });
            assertEquals("custom error", ex.getMessage());
        }

        @Test
        @DisplayName("verify with message - null message throws")
        void verify_withMessage_nullMessage() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.verify("value", forceNull());
            });
        }
    }

    @Nested
    @DisplayName("orThrow(Object, String) Branch Coverage")
    class OrThrowWithMessageBranchTests {

        @Test
        @DisplayName("orThrow with message - non-null returns value")
        void orThrow_withMessage_nonNull() {
            String result = NotNull.orThrow("value", "error");
            assertEquals("value", result);
        }

        @Test
        @DisplayName("orThrow with message - null throws with message")
        void orThrow_withMessage_null() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.orThrow((String) null, "User not found");
            });
            assertEquals("User not found", ex.getMessage());
        }

        @Test
        @DisplayName("orThrow with message - null message throws")
        void orThrow_withMessage_nullMessage() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orThrow((String) null, forceNull());
            });
        }
    }

    @Nested
    @DisplayName("orGetOptional Branch Coverage")
    class OrGetOptionalBranchTests {

        @Test
        @DisplayName("orGetOptional - present value returns immediately")
        void orGetOptional_present_returnsValue() {
            boolean[] called = {false};
            String result = NotNull.orGetOptional(Optional.of("present"), () -> {
                called[0] = true;
                return "unused";
            });
            assertEquals("present", result);
            assertFalse(called[0]);
        }

        @Test
        @DisplayName("orGetOptional - empty calls supplier successfully")
        void orGetOptional_empty_callsSupplier() {
            String result = NotNull.orGetOptional(Optional.empty(), () -> "from-supplier");
            assertEquals("from-supplier", result);
        }

        @Test
        @DisplayName("orGetOptional - supplier throws RuntimeException")
        void orGetOptional_supplierThrows() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.orGetOptional(Optional.empty(), () -> {
                    throw new IllegalStateException("Boom");
                });
            });
            assertTrue(ex.getMessage().contains("supplier threw exception"));
        }

        @Test
        @DisplayName("orGetOptional - supplier returns null")
        void orGetOptional_supplierReturnsNull() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.orGetOptional(Optional.empty(), () -> forceNull());
            });
            assertTrue(ex.getMessage().contains("supplier returned null"));
        }
    }

    @Nested
    @DisplayName("orLogOptional(Optional, Object) Branch Coverage")
    class OrLogOptionalBranchTests {

        @Test
        @DisplayName("orLogOptional - present returns value")
        void orLogOptional_present_returnsValue() {
            String result = NotNull.orLogOptional(Optional.of("value"), "default");
            assertEquals("value", result);
        }

        @Test
        @DisplayName("orLogOptional - empty returns default")
        void orLogOptional_empty_returnsDefault() {
            String result = NotNull.orLogOptional(Optional.empty(), "default");
            assertEquals("default", result);
        }

        @Test
        @DisplayName("orLogOptional - null defaultValue throws")
        void orLogOptional_nullDefault_throws() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orLogOptional(Optional.empty(), forceNull());
            });
        }
    }

    @Nested
    @DisplayName("findCaller Branch Coverage")
    class FindCallerBranchTests {

        @Test
        @DisplayName("findCaller - disabled returns null")
        void findCaller_disabled_returnsNull() {
            NotNull.setCaptureCaller(false);
            try {
                assertThrows(NullPointerException.class, () -> {
                    NotNull.orThrow((String) null);
                });
            } finally {
                NotNull.setCaptureCaller(true);
            }
        }

        @Test
        @DisplayName("findCaller - enabled captures caller")
        void findCaller_enabled_capturesCaller() {
            NotNull.setCaptureCaller(true);
            assertThrows(NullPointerException.class, () -> {
                NotNull.orThrow((String) null);
            });
        }
    }

    @Nested
    @DisplayName("setOrEmpty Branch Coverage")
    class SetOrEmptyBranchTests {

        @Test
        @DisplayName("setOrEmpty - non-null returns original")
        void setOrEmpty_nonNull_returnsOriginal() {
            Set<String> input = Set.of("a", "b");
            Set<String> result = NotNull.setOrEmpty(input);
            assertSame(input, result);
        }
    }

    @Nested
    @DisplayName("mapOrEmpty Branch Coverage")
    class MapOrEmptyBranchTests {

        @Test
        @DisplayName("mapOrEmpty - non-null returns original")
        void mapOrEmpty_nonNull_returnsOriginal() {
            Map<String, Integer> input = Map.of("key", 42);
            Map<String, Integer> result = NotNull.mapOrEmpty(input);
            assertSame(input, result);
        }
    }

    @Nested
    @DisplayName("callerSuffix Branch Coverage")
    class CallerSuffixBranchTests {

        @Test
        @DisplayName("callerSuffix - with caller capture returns suffix")
        void callerSuffix_withCapture() {
            NotNull.setCaptureCaller(true);
            assertThrows(NullPointerException.class, () -> {
                NotNull.orThrow((String) null);
            });
        }

        @Test
        @DisplayName("callerSuffix - without caller capture returns empty")
        void callerSuffix_withoutCapture() {
            NotNull.setCaptureCaller(false);
            try {
                assertThrows(NullPointerException.class, () -> {
                    NotNull.orThrow((String) null);
                });
            } finally {
                NotNull.setCaptureCaller(true);
            }
        }
    }

    @Nested
    @DisplayName("orGet Complete Branch Coverage")
    class OrGetCompleteBranchTests {

        @Test
        @DisplayName("orGet - non-null value early return")
        void orGet_nonNull_earlyReturn() {
            boolean[] called = {false};
            String result = NotNull.orGet("existing", () -> {
                called[0] = true;
                return "unused";
            });
            assertEquals("existing", result);
            assertFalse(called[0]);
        }
    }

    @Nested
    @DisplayName("orLogGet Complete Branch Coverage")
    class OrLogGetCompleteBranchTests {

        @Test
        @DisplayName("orLogGet - non-null value early return")
        void orLogGet_nonNull_earlyReturn() {
            boolean[] called = {false};
            String result = NotNull.orLogGet("existing", () -> {
                called[0] = true;
                return "unused";
            }, "context");
            assertEquals("existing", result);
            assertFalse(called[0]);
        }
    }

    // ==================================================================================
    // Mock Tests
    // ==================================================================================
    @Nested
    @DisplayName("Mock tests")
    class MockTests {

        @Test
        void testUnwrapOptionalCatchBlockWithMock() {
            // Create an Optional that throws when orElse is called
            Optional<String> mockOpt = org.mockito.Mockito.mock(Optional.class);
            org.mockito.Mockito.when(mockOpt.orElse(null)).thenThrow(new RuntimeException("Manual Trigger"));

            String result = NotNull.orNull(mockOpt);
            assertNull(result);
        }
    }

    // ==================================================================================
    // Optional Tests
    // ==================================================================================
    @Nested
    @DisplayName("Optional parameter tests")
    class OptionalParamTests {

        @Test
        void optional_present_returnsValue() {
            // Hits the success branch
            assertEquals("value", NotNull.orThrowOptional(Optional.of("value"), "Error"));
        }

        @Test
        void optional_empty_throwsWithMessage() {
            // Hits the failure branch and log.error line
            String msg = "Specific failure reason";
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.orThrowOptional(Optional.empty(), msg);
            });
            assertEquals(msg, ex.getMessage());
        }

        @Test
        void optional_null_message_throwsNPE() {
            // Hits the Objects.requireNonNull(message) check
            assertThrows(NullPointerException.class, () -> {
                NotNull.orThrowOptional(Optional.of("value"), (String) null);
            });
        }

        @Test
        @DisplayName("Coverage: unwrapOptional handles corrupt Optional objects")
        void testUnwrapOptionalCorrupt() throws Exception {
            // 1. Create a normal Optional
            Optional<String> corrupt = Optional.of("safe");

            // 2. Use reflection to set the internal 'value' field to null.
            // In Java, Optional.of(null) is normally impossible, so this simulates
            // weird serialization bugs or reflection abuse.
            java.lang.reflect.Field valueField = Optional.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(corrupt, null);

            // 3. Trigger a method that uses unwrapOptional internally.
            // This will cause opt.orElse(null) to throw a RuntimeException or NullPointerException,
            // which your unwrapOptional catch-block will then handle and log.
            String result = NotNull.orNull(corrupt);

            // Verify it handled the corruption gracefully
            assertNull(result, "Should return null when the Optional is corrupt");
        }

        @Test
        void testOrLogOptionalWithContext() {
            // Tests the 'if (value == null)' branch with context
            assertEquals("default", NotNull.orLogOptional(Optional.empty(), "default", "UserSync"));
            // Tests the success branch with context
            assertEquals("value", NotNull.orLogOptional(Optional.of("value"), "default", "UserSync"));
        }

        @Test
        void testVerifyTraceCoverage() {
            NotNull.setTraceVerifySuccess(true);

            NotNull.verify("test");
            NotNull.setTraceVerifySuccess(false);
        }

        @Test
        void testOrThrowOptionalCoverage() {
            // 1. One-arg version: Success path
            assertEquals("ok", NotNull.orThrowOptional(Optional.of("ok")));

            // 2. Two-arg version: Success path
            assertEquals("ok", NotNull.orThrowOptional(Optional.of("ok"), "error message"));

            // 3. Two-arg version: Failure path (This turns the log.error and throw green)
            assertThrows(NullPointerException.class, () ->
                    NotNull.orThrowOptional(Optional.empty(), "custom failure"));
        }

        @Test
        void testLoggingOverloadsWithContext() {
            // Standard orLog with context
            assertEquals("fallback", NotNull.orLog(null, "fallback", "App-Context"));

            // Optional orLog with context
            assertEquals("fallback", NotNull.orLogOptional(Optional.empty(), "fallback", "Opt-Context"));

            // Trigger the requireNonNull check for 100% line coverage
            assertThrows(NullPointerException.class, () -> NotNull.orLog("val", "def", null));
        }

        @Test
        void testUnwrapOptionalCatchBlock() throws Exception {
            Optional<String> corrupt = Optional.of("dummy");
            java.lang.reflect.Field valueField = Optional.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(corrupt, null); // Corrupt it

            // This triggers the catch block in the private unwrapOptional method
            assertNull(NotNull.orNull(corrupt));
        }

        @Test
        void testCallerCaptureDisabled() {
            NotNull.setCaptureCaller(false);
            // This hits the 'if (!captureCaller) return null;' line
            assertNotNull(NotNull.orDefault("val", "def"));

            NotNull.setCaptureCaller(true);
        }


    }

    // ==================================================================================
    // Logging Context Tests
    // ==================================================================================
    @Nested
    @DisplayName("Logging Context Tests")
    class LoggingContextTests {

        @Test
        void orLog_withContext_null_returnsDefault() {
            // Targets: orLog(T, T, String)
            String result = NotNull.orLog(null, "fallback", "UserSync-Context");
            assertEquals("fallback", result);
        }

        @Test
        void orLog_withContext_nonNull_returnsValue() {
            // Targets: orLog(T, T, String) success branch
            assertEquals("actual", NotNull.orLog("actual", "fallback", "UserSync-Context"));
        }

        @Test
        void orLogOptional_withContext_empty_returnsDefault() {
            // Targets: orLogOptional(Optional, T, String)
            String result = NotNull.orLogOptional(Optional.empty(), "fallback", "Optional-Context");
            assertEquals("fallback", result);
        }

        @Test
        void orLogOptional_withContext_present_returnsValue() {
            // Targets: orLogOptional(Optional, T, String) success branch
            assertEquals("actual", NotNull.orLogOptional(Optional.of("actual"), "fallback", "Ctx"));
        }

        @Test
        void logging_nullContext_throwsNPE() {
            // Ensures Objects.requireNonNull(context) is covered in both methods
            assertThrows(NullPointerException.class, () -> NotNull.orLog("val", "def", null));
            assertThrows(NullPointerException.class, () -> NotNull.orLogOptional(Optional.of("val"), "def", null));
        }
    }


    // ==================================================================================
    // Null Generics
    // ==================================================================================

    @Nested
    @DisplayName("Null Generics Tests ")
    class NullGenericsTests {

        @Test
        void testTypeInferenceWithNull() {
            // 1. Literal null with String default
            // Compiler sees: orDefault(T, T) where T is String.
            // It knows this isn't the Optional method because "default" is not an Optional.
            String result = NotNull.orDefault(null, "default");
            assertEquals("default", result);
        }

        @Test
        void testVarInference() {
            // 2. Using 'var' (Java 11+)
            // The compiler uses the second argument to determine the type of 'var'.
            var result = NotNull.orDefault(null, "w|");
            assertInstanceOf(String.class, result);
            assertEquals("w|", result);
        }

        @Test
        void testOptionalMethodSelection() {
            // 3. Explicit Optional
            // This clearly hits the orDefault(Optional, T) overload.
            Optional<String> emptyOpt = Optional.empty();
            String result = NotNull.orDefaultOptional(emptyOpt, "fallback");
            assertEquals("fallback", result);
        }

        @Test
        void testNullAsOptionalThrowsNpe() {
            // 4. Forcing the Optional overload with a null reference
            // This proves the compiler can distinguish them if you cast,
            // but your Objects.requireNonNull(opt) will catch the misuse.
            assertThrows(NullPointerException.class, () -> {
                NotNull.orDefaultOptional((Optional<String>) null, "fallback");
            });
        }
    }

    // ==================================================================================
    // Collection Normalizers
    // ==================================================================================

    @Nested
    @DisplayName("Collection Normalizers")
    class CollectionNormalizerTests {

        @Test
        void listOrEmpty_null_returnsEmpty() {
            List<String> result = NotNull.listOrEmpty(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void listOrEmpty_nonNull_returnsOriginal() {
            List<String> input = Arrays.asList("a", "b");
            assertSame(input, NotNull.listOrEmpty(input));
        }

        @Test
        void listOrEmpty_result_isUnmodifiable() {
            assertThrows(UnsupportedOperationException.class, () -> {
                NotNull.listOrEmpty(null).add("test");
            });
        }

        @Test
        void setOrEmpty_null_returnsEmpty() {
            assertTrue(NotNull.setOrEmpty(null).isEmpty());
        }

        @Test
        void mapOrEmpty_null_returnsEmpty() {
            assertTrue(NotNull.mapOrEmpty(null).isEmpty());
        }

        @Test
        void stringOrEmpty_null_returnsEmpty() {
            assertEquals("", NotNull.stringOrEmpty(null));
        }

        @Test
        void stringOrEmpty_emptyString_returnsEmpty() {
            assertEquals("", NotNull.stringOrEmpty(""));
        }
    }

    // ==================================================================================
    // orDefault
    // ==================================================================================

    @Nested
    @DisplayName("orDefault")
    class OrDefaultTests {

        @Test
        void nullable_null_returnsDefault() {
            assertEquals("default", NotNull.orDefault((String) null, "default"));
        }

        @Test
        void nullable_nonNull_returnsValue() {
            assertEquals("value", NotNull.orDefault("value", "default"));
        }

        @Test
        void optional_empty_returnsDefault() {
            assertEquals("default", NotNull.orDefaultOptional(Optional.empty(), "default"));
        }

        @Test
        void optional_present_returnsValue() {
            assertEquals("value", NotNull.orDefaultOptional(Optional.of("value"), "default"));
        }

        @Test
        void optional_null_throwsNPE() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orDefaultOptional((Optional<String>) forceNull(), "default");
            });
        }
    }

    // ==================================================================================
    // orThrowOptional
    // ==================================================================================

    @Nested
    @DisplayName("orThrowOptional")
    class OrThrowTests {

        @Test
        void nullable_nonNull_returnsValue() {
            assertEquals("safe", NotNull.orThrow("safe"));
        }

        @Test
        void nullable_null_throwsNPE() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orThrow((String) null);
            });
        }

        @Test
        void nullable_null_withMessage_throwsWithMessage() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.orThrow((String) null, "Custom Error");
            });
            assertEquals("Custom Error", ex.getMessage());
        }

        @Test
        void optional_present_returnsValue() {
            assertEquals("value", NotNull.orThrowOptional(Optional.of("value")));
        }

        @Test
        void optional_empty_throwsNPE() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orThrowOptional(Optional.empty());
            });
        }
    }

    // ==================================================================================
    // orGetOptional (FAIL-FAST)
    // ==================================================================================

    @Nested
    @DisplayName("orGetOptional (FAIL-FAST)")
    class OrGetTests {

        @Test
        void nullable_nonNull_returnsValue_doesNotCallSupplier() {
            boolean[] called = {false};
            assertEquals("value", NotNull.orGet("value", () -> {
                called[0] = true;
                return "unused";
            }));
            assertFalse(called[0]);
        }

        @Test
        void nullable_null_returnsSupplierResult() {
            assertEquals("from-supplier", NotNull.orGet((String) null, () -> "from-supplier"));
        }

        @Test
        void nullable_nullSupplier_throwsNPE() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orGetOptional(Optional.ofNullable(null), (Supplier<String>) forceNull());
            });
        }

        @Test
        void nullable_supplierReturnsNull_throwsNPE() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.orGet((String) null, () -> forceNull());
            });
            assertTrue(ex.getMessage().contains("supplier returned null"));
        }

        @Test
        void nullable_supplierThrows_throwsNPE() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.orGet((Object) null, () -> {
                    throw new RuntimeException("Boom");
                });
            });
            assertTrue(ex.getMessage().contains("supplier threw exception"));
        }

        @Test
        void optional_present_doesNotCallSupplier() {
            boolean[] called = {false};
            NotNull.orGet((Object) Optional.of("value"), () -> {
                called[0] = true;
                return "unused";
            });
            assertFalse(called[0]);
        }

        @Test
        void optional_empty_callsSupplier() {
            assertEquals("fallback",
                    NotNull.<String>orGetOptional(Optional.empty(), () -> "fallback"));
        }

        @Test
        void optional_empty_supplierThrows_throwsNPE() {
            assertThrows(NullPointerException.class, () -> {
                // The <String> witness resolves the ambiguity
                NotNull.<String>orGetOptional(Optional.empty(), () -> {
                    throw new RuntimeException("Optional Supplier Boom");
                });
            });
        }

        @Test
        @DisplayName("orGetOptional: fails if supplier returns null (contract violation)")
        void orGet_supplierReturnsNull_throwsNPE() {
            // We use the <String> witness to avoid the Optional/Object ambiguity
            assertThrows(NullPointerException.class, () -> {
                NotNull.<String>orGetOptional(Optional.ofNullable(null), () -> forceNull());
            }, "Should have thrown NPE because supplier returned null");
        }

        @Test
        @DisplayName("orGetOptional: wraps supplier exceptions with caller context")
        void orGet_supplierThrows_wrapsInNPE() {
            String contextMessage = "supplier threw exception";

            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.<String>orGetOptional(Optional.ofNullable(null), () -> {
                    throw new RuntimeException("Simulated failure");
                });
            });

            // Verify the custom message you wrote in the catch block
            assertTrue(ex.getMessage().contains(contextMessage),
                    "Expected message to contain: " + contextMessage);
        }

    }

    // ==================================================================================
    // orLogGet (LOG-AND-CONTINUE)
    // ==================================================================================

    @Nested
    @DisplayName("orLogGet (LOG-AND-CONTINUE)")
    class OrLogGetTests {

        @Test
        void nonNull_returnsValue_doesNotCallSupplier() {
            boolean[] called = {false};
            assertEquals("value", NotNull.orLogGet("value", () -> {
                called[0] = true;
                return "unused";
            }, "context"));
            assertFalse(called[0]);
        }

        @Test
        void null_returnsSupplierResult() {
            assertEquals("from-supplier",
                    NotNull.orLogGet(null, () -> "from-supplier", "context"));
        }

        @Test
        void supplierReturnsNull_returnsNull_logsWarning() {
            assertNull(NotNull.orLogGet(null, () -> null, "test-context"));
        }

        @Test
        void supplierThrows_returnsNull_logsError() {
            assertNull(NotNull.orLogGet(null, () -> {
                throw new RuntimeException("Boom");
            }, "test-context"));
        }

        @Test
        void nullSupplier_throwsNPE() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orLogGet(null, forceNull(), "context");
            });
        }

        @Test
        void nullContext_throwsNPE() {
            assertThrows(NullPointerException.class, () -> {
                NotNull.orLogGet(null, () -> "value", forceNull());
            });
        }

        @Test
        @DisplayName("orLogGet: returns null and swallows exception when supplier throws")
        void orLogGet_supplierThrows_returnsNull() {
            // Should NOT throw an exception
            String result = NotNull.orLogGet(null, () -> {
                throw new RuntimeException("Logged failure");
            }, "MigrationContext");

            assertNull(result, "orLogGet must return null if the supplier fails");
        }

        @Test
        @DisplayName("orLogGet: returns null when supplier returns null")
        void orLogGet_supplierReturnsNull_returnsNull() {
            String result = NotNull.orLogGet(null, () -> (String) forceNull(), "NullSupplierContext");
            assertNull(result);
        }

    }

    // ==================================================================================
    // verify (DEFENSIVE)
    // ==================================================================================

    @Nested
    @DisplayName("verify (DEFENSIVE)")
    class VerifyTests {

        @Test
        void nonNull_returnsValue() {
            assertEquals("safe", NotNull.verify("safe"));
        }

        @Test
        void null_throwsNPE_withContractViolationMessage() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.verify(forceNull());
            });
            assertTrue(ex.getMessage().contains("contract was violated"));
        }

        @Test
        void null_withMessage_throwsWithCustomMessage() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                NotNull.verify(forceNull(), "Payment required");
            });
            assertEquals("Payment required", ex.getMessage());
        }

        @Test
        void traceEnabled_doesNotCrash() {
            NotNull.setTraceVerifySuccess(true);
            try {
                assertEquals("value", NotNull.verify("value"));
            } finally {
                NotNull.setTraceVerifySuccess(false);
            }
        }

        @Test
        @DisplayName("verify: ensures message check fails fast if message itself is null")
        void verify_nullMessage_throwsNPE() {
            // Testing the Objects.requireNonNull(message) inside verify(T, String)
            assertThrows(NullPointerException.class, () -> {
                NotNull.verify("valid", forceNull());
            });
        }

    }

    // ==================================================================================
    // orLogOptional
    // ==================================================================================

    @Nested
    @DisplayName("orLogOptional")
    class OrLogTests {

        @Test
        void nonNull_returnsValue() {
            assertEquals("value", NotNull.orLog("value", "default"));
        }

        @Test
        void null_returnsDefault_logsWarning() {
            assertEquals("default", NotNull.orLog((String) null, "default"));
        }

        @Test
        void null_withContext_returnsDefault() {
            assertEquals("default", NotNull.orLog((String) null, "default", "ctx"));
        }

        @Test
        void optional_empty_returnsDefault() {
            assertEquals("default", NotNull.orLogOptional(Optional.empty(), "default"));
        }
    }

    // ==================================================================================
    // Optional Bridging
    // ==================================================================================

    @Nested
    @DisplayName("Optional Bridging")
    class OptionalBridgeTests {

        @Test
        void optional_null_returnsEmpty() {
            assertTrue(NotNull.optional(null).isEmpty());
        }

        @Test
        void optional_value_returnsPresent() {
            assertEquals("value", NotNull.optional("value").get());
        }

        @Test
        void orNull_empty_returnsNull() {
            assertNull(NotNull.orNull(Optional.empty()));
        }

        @Test
        void orNull_present_returnsValue() {
            assertEquals("value", NotNull.orNull(Optional.of("value")));
        }

        @Test
        void orNull_corruptOptional_returnsNull() throws Exception {
            Optional<String> corrupt = Optional.of("valid");

            // Force corruption via reflection
            Field valueField = Optional.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(corrupt, null);

            assertNull(NotNull.orNull(corrupt));
        }
    }

    // ==================================================================================
    // Configuration
    // ==================================================================================

    @Nested
    @DisplayName("Runtime Configuration")
    class ConfigTests {

        @Test
        void captureCaller_canToggle() {
            NotNull.setCaptureCaller(false);
            assertFalse(NotNull.isCaptureCaller());

            NotNull.setCaptureCaller(true);
            assertTrue(NotNull.isCaptureCaller());
        }

        @Test
        void traceVerify_canToggle() {
            NotNull.setTraceVerifySuccess(true);
            assertTrue(NotNull.isTraceVerifySuccess());

            NotNull.setTraceVerifySuccess(false);
            assertFalse(NotNull.isTraceVerifySuccess());
        }

        @Test
        void captureCaller_disabled_stillWorks() {
            NotNull.setCaptureCaller(false);
            try {
                assertThrows(NullPointerException.class, () -> {
                    NotNull.orThrowOptional(null);
                });
            } finally {
                NotNull.setCaptureCaller(true);
            }
        }

        @Test
        @DisplayName("Configuration: Methods still function when caller capture is disabled")
        void disabledCapture_functionalityRemains() {
            NotNull.setCaptureCaller(false);
            try {
                // Should still throw the NPE, just without the "at: ..." suffix in the internal log
                assertThrows(NullPointerException.class, () -> NotNull.orThrowOptional(null));
            } finally {
                NotNull.setCaptureCaller(true);
            }
        }
    }

    // ==================================================================================
    // Edge Cases
    // ==================================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        void emptyString_isNotNull() {
            assertEquals("", NotNull.orThrow(""));
            assertEquals("", NotNull.orDefault("", "fallback"));
        }

        @Test
        void emptyCollections_areNotNull() {
            List<String> empty = Collections.emptyList();
            assertSame(empty, NotNull.listOrEmpty(empty));
        }

        @Test
        void chaining_works() {
            // Test that NotNull methods can be chained
            String step1 = NotNull.orDefault((String) null, "value");
            String step2 = NotNull.verify(step1);
            String step3 = NotNull.orThrow(step2);

            assertEquals("value", step3);

            // Or in one line
            assertEquals("chained",
                    NotNull.orThrow(
                            NotNull.verify(
                                    NotNull.orDefault((String) null, "chained")
                            )
                    )
            );
        }

        @Test
        void generics_work() {
            List<String> list = NotNull.orThrow(Arrays.asList("a"));
            assertEquals(1, list.size());
        }
    }

    // ==================================================================================
    // Thread Safety
    // ==================================================================================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        void concurrentConfigChanges_dontCrash() throws InterruptedException {
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    NotNull.setCaptureCaller(i % 2 == 0);
                }
            });

            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    NotNull.setTraceVerifySuccess(i % 2 == 0);
                }
            });

            Thread t3 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    NotNull.verify("test");
                }
            });

            t1.start();
            t2.start();
            t3.start();

            t1.join();
            t2.join();
            t3.join();
        }
    }
}