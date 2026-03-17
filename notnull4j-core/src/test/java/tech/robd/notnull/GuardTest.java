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

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for Guard.
 * <p>
 * Coverage:
 * - requireNotNull / checkNotNull with String and Supplier message variants
 * - require / check boolean predicate variants
 * - Correct exception types (IllegalArgumentException vs IllegalStateException)
 * - Correct exception messages including lazy supplier evaluation
 * - Happy paths returning the value / completing without exception
 * - Supplier laziness — supplier must not be called when condition passes
 * - Null message supplier behaviour
 */
public class GuardTest {

    // ==================================================================================
    // Test Utilities
    // ==================================================================================

    @SuppressWarnings("unchecked")
    private static <T> T forceNull() {
        return (T) null;
    }

    @Nested
    @DisplayName("Misc Tests")
    class MiscTests {

        @Test
        @DisplayName("Guard constructor is private")
        void testConstructorIsPrivate() throws Exception {
            java.lang.reflect.Constructor<Guard> constructor = Guard.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
            constructor.setAccessible(true);
            constructor.newInstance();
        }
    }

    // ==================================================================================
    // requireNotNull — null check, throws IllegalArgumentException (4xx / caller fault)
    // ==================================================================================

    @Nested
    @DisplayName("requireNotNull")
    class RequireNotNullTests {

        @Test
        @DisplayName("requireNotNull - non-null value returns value unchanged")
        void requireNotNull_nonNull_returnsValue() {
            String result = Guard.requireNotNull("hello", "must not be null");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("requireNotNull - non-null preserves reference identity")
        void requireNotNull_nonNull_sameReference() {
            List<String> list = List.of("a", "b");
            assertSame(list, Guard.requireNotNull(list, "must not be null"));
        }

        @Test
        @DisplayName("requireNotNull - null throws IllegalArgumentException")
        void requireNotNull_null_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.requireNotNull(forceNull(), "value must not be null"));
        }

        @Test
        @DisplayName("requireNotNull - null exception carries correct message")
        void requireNotNull_null_correctMessage() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Guard.requireNotNull(forceNull(), "userId must not be null"));
            assertEquals("userId must not be null", ex.getMessage());
        }

        @Test
        @DisplayName("requireNotNull - does NOT throw IllegalStateException")
        void requireNotNull_throwsIllegalArgument_notIllegalState() {
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.requireNotNull(forceNull(), "msg"));
            // Confirm it is not IllegalStateException
            try {
                Guard.requireNotNull(forceNull(), "msg");
            } catch (IllegalStateException e) {
                fail("Should not throw IllegalStateException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        @Test
        @DisplayName("requireNotNull - works with generic types")
        void requireNotNull_generics() {
            Integer value = Guard.requireNotNull(42, "must not be null");
            assertEquals(42, value);
        }

        // --- Supplier overload ---

        @Test
        @DisplayName("requireNotNull(Supplier) - non-null value returns value, supplier not called")
        void requireNotNull_supplier_nonNull_supplierNotCalled() {
            boolean[] called = {false};
            String result = Guard.requireNotNull("present", () -> {
                called[0] = true;
                return "should not be called";
            });
            assertEquals("present", result);
            assertFalse(called[0], "Supplier must not be called when value is non-null");
        }

        @Test
        @DisplayName("requireNotNull(Supplier) - null throws IllegalArgumentException")
        void requireNotNull_supplier_null_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.requireNotNull(forceNull(), () -> "lazy message"));
        }

        @Test
        @DisplayName("requireNotNull(Supplier) - null uses lazy message")
        void requireNotNull_supplier_null_usesSupplierMessage() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Guard.requireNotNull(forceNull(), () -> "computed: userId=" + 42));
            assertEquals("computed: userId=42", ex.getMessage());
        }

        @Test
        @DisplayName("requireNotNull(Supplier) - supplier only called on failure")
        void requireNotNull_supplier_calledOnlyOnFailure() {
            int[] callCount = {0};
            Supplier<String> countingSupplier = () -> {
                callCount[0]++;
                return "message";
            };

            // Should not call supplier
            Guard.requireNotNull("value", countingSupplier);
            assertEquals(0, callCount[0]);

            // Should call supplier exactly once
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.requireNotNull(forceNull(), countingSupplier));
            assertEquals(1, callCount[0]);
        }
    }

    // ==================================================================================
    // checkNotNull — null check, throws IllegalStateException (5xx / our fault)
    // ==================================================================================

    @Nested
    @DisplayName("checkNotNull")
    class CheckNotNullTests {

        @Test
        @DisplayName("checkNotNull - non-null value returns value unchanged")
        void checkNotNull_nonNull_returnsValue() {
            String result = Guard.checkNotNull("hello", "should be set");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("checkNotNull - non-null preserves reference identity")
        void checkNotNull_nonNull_sameReference() {
            List<String> list = List.of("x");
            assertSame(list, Guard.checkNotNull(list, "should be set"));
        }

        @Test
        @DisplayName("checkNotNull - null throws IllegalStateException")
        void checkNotNull_null_throwsIllegalStateException() {
            assertThrows(IllegalStateException.class, () ->
                    Guard.checkNotNull(forceNull(), "cache should be initialised"));
        }

        @Test
        @DisplayName("checkNotNull - null exception carries correct message")
        void checkNotNull_null_correctMessage() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    Guard.checkNotNull(forceNull(), "userCache should have been initialised by startup"));
            assertEquals("userCache should have been initialised by startup", ex.getMessage());
        }

        @Test
        @DisplayName("checkNotNull - does NOT throw IllegalArgumentException")
        void checkNotNull_throwsIllegalState_notIllegalArgument() {
            try {
                Guard.checkNotNull(forceNull(), "msg");
            } catch (IllegalArgumentException e) {
                fail("Should not throw IllegalArgumentException");
            } catch (IllegalStateException e) {
                // expected
            }
        }

        @Test
        @DisplayName("checkNotNull - works with generic types")
        void checkNotNull_generics() {
            Integer value = Guard.checkNotNull(99, "must not be null");
            assertEquals(99, value);
        }

        // --- Supplier overload ---

        @Test
        @DisplayName("checkNotNull(Supplier) - non-null value returns value, supplier not called")
        void checkNotNull_supplier_nonNull_supplierNotCalled() {
            boolean[] called = {false};
            String result = Guard.checkNotNull("present", () -> {
                called[0] = true;
                return "should not be called";
            });
            assertEquals("present", result);
            assertFalse(called[0], "Supplier must not be called when value is non-null");
        }

        @Test
        @DisplayName("checkNotNull(Supplier) - null throws IllegalStateException")
        void checkNotNull_supplier_null_throwsIllegalStateException() {
            assertThrows(IllegalStateException.class, () ->
                    Guard.checkNotNull(forceNull(), () -> "lazy state message"));
        }

        @Test
        @DisplayName("checkNotNull(Supplier) - null uses lazy message")
        void checkNotNull_supplier_null_usesSupplierMessage() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    Guard.checkNotNull(forceNull(), () -> "pool size=" + 0));
            assertEquals("pool size=0", ex.getMessage());
        }

        @Test
        @DisplayName("checkNotNull(Supplier) - supplier only called on failure")
        void checkNotNull_supplier_calledOnlyOnFailure() {
            int[] callCount = {0};
            Supplier<String> countingSupplier = () -> {
                callCount[0]++;
                return "state message";
            };

            Guard.checkNotNull("value", countingSupplier);
            assertEquals(0, callCount[0]);

            assertThrows(IllegalStateException.class, () ->
                    Guard.checkNotNull(forceNull(), countingSupplier));
            assertEquals(1, callCount[0]);
        }
    }

    // ==================================================================================
    // require — boolean predicate, throws IllegalArgumentException (4xx / caller fault)
    // ==================================================================================

    @Nested
    @DisplayName("require")
    class RequireTests {

        @Test
        @DisplayName("require - true condition completes without exception")
        void require_true_noException() {
            assertDoesNotThrow(() -> Guard.require(true, "should not throw"));
        }

        @Test
        @DisplayName("require - false throws IllegalArgumentException")
        void require_false_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(false, "age must be non-negative"));
        }

        @Test
        @DisplayName("require - false exception carries correct message")
        void require_false_correctMessage() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(false, "pageSize must be positive"));
            assertEquals("pageSize must be positive", ex.getMessage());
        }

        @Test
        @DisplayName("require - does NOT throw IllegalStateException")
        void require_throwsIllegalArgument_notIllegalState() {
            try {
                Guard.require(false, "msg");
            } catch (IllegalStateException e) {
                fail("Should not throw IllegalStateException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        @Test
        @DisplayName("require - typical boundary validation pattern")
        void require_boundaryValidation() {
            int pageSize = 10;
            int maxBatch = 100;

            assertDoesNotThrow(() -> Guard.require(pageSize > 0, "pageSize must be positive"));
            assertDoesNotThrow(() -> Guard.require(pageSize <= maxBatch, "pageSize too large"));

            assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(-1 > 0, "pageSize must be positive"));
        }

        // --- Supplier overload ---

        @Test
        @DisplayName("require(Supplier) - true condition, supplier not called")
        void require_supplier_true_supplierNotCalled() {
            boolean[] called = {false};
            assertDoesNotThrow(() -> Guard.require(true, () -> {
                called[0] = true;
                return "should not be called";
            }));
            assertFalse(called[0], "Supplier must not be called when condition is true");
        }

        @Test
        @DisplayName("require(Supplier) - false throws IllegalArgumentException")
        void require_supplier_false_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(false, () -> "lazy require message"));
        }

        @Test
        @DisplayName("require(Supplier) - false uses lazy message")
        void require_supplier_false_usesSupplierMessage() {
            int size = 150;
            int max = 100;
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(size <= max, () -> "batch too large: " + size + ", max=" + max));
            assertEquals("batch too large: 150, max=100", ex.getMessage());
        }

        @Test
        @DisplayName("require(Supplier) - supplier only called on failure")
        void require_supplier_calledOnlyOnFailure() {
            int[] callCount = {0};
            Supplier<String> countingSupplier = () -> {
                callCount[0]++;
                return "message";
            };

            assertDoesNotThrow(() -> Guard.require(true, countingSupplier));
            assertEquals(0, callCount[0]);

            assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(false, countingSupplier));
            assertEquals(1, callCount[0]);
        }
    }

    // ==================================================================================
    // check — boolean predicate, throws IllegalStateException (5xx / our fault)
    // ==================================================================================

    @Nested
    @DisplayName("check")
    class CheckTests {

        @Test
        @DisplayName("check - true condition completes without exception")
        void check_true_noException() {
            assertDoesNotThrow(() -> Guard.check(true, "should not throw"));
        }

        @Test
        @DisplayName("check - false throws IllegalStateException")
        void check_false_throwsIllegalStateException() {
            assertThrows(IllegalStateException.class, () ->
                    Guard.check(false, "connection should be open"));
        }

        @Test
        @DisplayName("check - false exception carries correct message")
        void check_false_correctMessage() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    Guard.check(false, "scheduler must be running before tasks can be submitted"));
            assertEquals("scheduler must be running before tasks can be submitted", ex.getMessage());
        }

        @Test
        @DisplayName("check - does NOT throw IllegalArgumentException")
        void check_throwsIllegalState_notIllegalArgument() {
            try {
                Guard.check(false, "msg");
            } catch (IllegalArgumentException e) {
                fail("Should not throw IllegalArgumentException");
            } catch (IllegalStateException e) {
                // expected
            }
        }

        @Test
        @DisplayName("check - typical internal state assertion pattern")
        void check_internalStateAssertion() {
            boolean connectionOpen = true;
            assertDoesNotThrow(() -> Guard.check(connectionOpen, "connection should be open"));

            boolean connectionClosed = false;
            assertThrows(IllegalStateException.class, () ->
                    Guard.check(connectionClosed, "connection should be open at this point"));
        }

        // --- Supplier overload ---

        @Test
        @DisplayName("check(Supplier) - true condition, supplier not called")
        void check_supplier_true_supplierNotCalled() {
            boolean[] called = {false};
            assertDoesNotThrow(() -> Guard.check(true, () -> {
                called[0] = true;
                return "should not be called";
            }));
            assertFalse(called[0], "Supplier must not be called when condition is true");
        }

        @Test
        @DisplayName("check(Supplier) - false throws IllegalStateException")
        void check_supplier_false_throwsIllegalStateException() {
            assertThrows(IllegalStateException.class, () ->
                    Guard.check(false, () -> "lazy check message"));
        }

        @Test
        @DisplayName("check(Supplier) - false uses lazy message")
        void check_supplier_false_usesSupplierMessage() {
            String state = "UNINITIALISED";
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    Guard.check(false, () -> "cache not ready — state=" + state));
            assertEquals("cache not ready — state=UNINITIALISED", ex.getMessage());
        }

        @Test
        @DisplayName("check(Supplier) - supplier only called on failure")
        void check_supplier_calledOnlyOnFailure() {
            int[] callCount = {0};
            Supplier<String> countingSupplier = () -> {
                callCount[0]++;
                return "state message";
            };

            assertDoesNotThrow(() -> Guard.check(true, countingSupplier));
            assertEquals(0, callCount[0]);

            assertThrows(IllegalStateException.class, () ->
                    Guard.check(false, countingSupplier));
            assertEquals(1, callCount[0]);
        }
    }

    // ==================================================================================
    // Exception type distinction — the core value of the library
    // ==================================================================================

    @Nested
    @DisplayName("Exception type distinction")
    class ExceptionTypeDistinctionTests {

        @Test
        @DisplayName("require and requireNotNull both throw IllegalArgumentException")
        void requireFamily_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(false, "caller fault"));
            assertThrows(IllegalArgumentException.class, () ->
                    Guard.requireNotNull(forceNull(), "caller fault"));
        }

        @Test
        @DisplayName("check and checkNotNull both throw IllegalStateException")
        void checkFamily_throwsIllegalStateException() {
            assertThrows(IllegalStateException.class, () ->
                    Guard.check(false, "our fault"));
            assertThrows(IllegalStateException.class, () ->
                    Guard.checkNotNull(forceNull(), "our fault"));
        }

        @Test
        @DisplayName("require does not throw IllegalStateException")
        void require_neverThrowsIllegalStateException() {
            Exception ex = assertThrows(Exception.class, () ->
                    Guard.require(false, "msg"));
            assertFalse(ex instanceof IllegalStateException,
                    "require must not throw IllegalStateException");
        }

        @Test
        @DisplayName("check does not throw IllegalArgumentException")
        void check_neverThrowsIllegalArgumentException() {
            Exception ex = assertThrows(Exception.class, () ->
                    Guard.check(false, "msg"));
            assertFalse(ex instanceof IllegalArgumentException,
                    "check must not throw IllegalArgumentException");
        }

        @Test
        @DisplayName("IllegalArgumentException is catchable as IllegalArgumentException")
        void requireException_catchableAsIllegalArgument() {
            try {
                Guard.requireNotNull(forceNull(), "msg");
                fail("Expected exception");
            } catch (IllegalArgumentException e) {
                assertEquals("msg", e.getMessage());
            }
        }

        @Test
        @DisplayName("IllegalStateException is catchable as IllegalStateException")
        void checkException_catchableAsIllegalState() {
            try {
                Guard.checkNotNull(forceNull(), "msg");
                fail("Expected exception");
            } catch (IllegalStateException e) {
                assertEquals("msg", e.getMessage());
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
        @DisplayName("requireNotNull - empty string is non-null and returned")
        void requireNotNull_emptyString_returned() {
            assertEquals("", Guard.requireNotNull("", "must not be null"));
        }

        @Test
        @DisplayName("checkNotNull - empty string is non-null and returned")
        void checkNotNull_emptyString_returned() {
            assertEquals("", Guard.checkNotNull("", "must not be set"));
        }

        @Test
        @DisplayName("require - can be used for multiple field validation in sequence")
        void require_multipleFieldValidation() {
            String name = "Rob";
            int age = 25;
            int pageSize = 10;

            assertDoesNotThrow(() -> {
                Guard.requireNotNull(name, "name must not be null");
                Guard.require(age >= 0, "age must be non-negative");
                Guard.require(pageSize > 0 && pageSize <= 100, "pageSize must be between 1 and 100");
            });
        }

        @Test
        @DisplayName("requireNotNull and checkNotNull return value for chaining")
        void returnValue_enablesChaining() {
            String result = Guard.requireNotNull(
                    Guard.checkNotNull("value", "internal check"),
                    "caller check"
            );
            assertEquals("value", result);
        }

        @Test
        @DisplayName("require with compound boolean expression")
        void require_compoundCondition() {
            int start = 5;
            int end = 10;
            assertDoesNotThrow(() ->
                    Guard.require(start >= 0 && end > start, "invalid range"));

            assertThrows(IllegalArgumentException.class, () ->
                    Guard.require(start >= 0 && end < start, "end must be after start"));
        }
    }
}