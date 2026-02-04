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
package tech.robd.notnull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime null-handling utilities with explicit policies.
 *
 * <p>Designed to complement JSpecify annotations by providing graceful, lazy,
 * fail-fast, and observable null-handling strategies during migration and
 * enforcement.
 *
 * <p>This is not a replacement for {@link Optional}; it is a small policy toolbox
 * for dealing with nullable inputs at runtime while keeping call sites readable.
 *
 * <p>Java baseline: 11+.
 *
 * <p>Caller capture is best-effort, disable-able, and never affects correctness.
 * It exists only to improve log detail.
 */
public final class NotNull {

    private static final Logger log = LoggerFactory.getLogger(NotNull.class);

    /**
     * Disable stack walking in environments where it is undesirable
     * (e.g. GraalVM native images) or when stack introspection overhead
     * should be avoided.
     *
     * <p>Initial value set via: -Dnotnull.captureCaller=true
     * <p>Can be changed at runtime via {@link #setCaptureCaller(boolean)}
     */
    private static volatile boolean captureCaller =
            Boolean.parseBoolean(System.getProperty("notnull.captureCaller", "true"));

    private static final StackWalker STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Enables "Audit Mode" for verification. When true, successful null-checks
     * are logged at TRACE level. This confirms the check was not optimized out.
     *
     * <p>Initial value set via: -Dnotnull.traceVerify=false
     */
    private static volatile boolean traceVerifySuccess =
            Boolean.parseBoolean(System.getProperty("notnull.traceVerify", "false"));

    /** @return current status of caller capture for logging. */
    public static boolean isCaptureCaller() {
        return captureCaller;
    }

    /** @param enabled dynamically toggle caller capture for logging. */
    public static void setCaptureCaller(boolean enabled) {
        captureCaller = enabled;
    }

    /** @return current status of successful verification logging. */
    public static boolean isTraceVerifySuccess() {
        return traceVerifySuccess;
    }

    /** @param enabled dynamically toggle successful verification logging. */
    public static void setTraceVerifySuccess(boolean enabled) {
        traceVerifySuccess = enabled;
    }

    private NotNull() {
        // no instances
    }

    // =====================================================================================
    // Normalisation helpers (iteration / concatenation safety)
    // =====================================================================================

    /** GRACEFUL — returns an unmodifiable empty list if null. */
    @NonNull
    public static <T> List<T> listOrEmpty(@Nullable List<T> list) {
        return (list != null) ? list : List.of();
    }

    /** GRACEFUL — returns an unmodifiable empty set if null. */
    @NonNull
    public static <T> Set<T> setOrEmpty(@Nullable Set<T> set) {
        return (set != null) ? set : Set.of();
    }

    /** GRACEFUL — returns an unmodifiable empty map if null. */
    @NonNull
    public static <K, V> Map<K, V> mapOrEmpty(@Nullable Map<K, V> map) {
        return (map != null) ? map : Map.of();
    }

    /** GRACEFUL — returns empty string if null. */
    @NonNull
    public static String stringOrEmpty(@Nullable String str) {
        return (str != null) ? str : "";
    }

    // =====================================================================================
    // Nullable value policies
    // =====================================================================================

    /** GRACEFUL — use default if null (common business logic). */
    @NonNull
    public static <T> T orDefault(
            @Nullable T value,
            @NonNull T defaultValue
    ) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        return (value != null) ? value : defaultValue;
    }

    /**
     * FAIL-FAST — use supplier if null.
     *
     * <p>Throws NPE if the supplier itself is null, returns null, or throws.
     * This ensures the {@code @NonNull} contract is never violated.
     *
     * <p>For graceful degradation during migration, use {@link #orLogGet} instead.
     *
     * @param value the potentially null value
     * @param supplier the non-null supplier providing a fallback
     * @param <T> the value type
     * @return the value or supplier result, guaranteed non-null
     * @throws NullPointerException if supplier is null, returns null, or throws
     */
    @NonNull
    public static <T> T orGet(
            @Nullable T value,
            @NonNull Supplier<@NonNull T> supplier
    ) {
        if (value != null) {
            return value;
        }

        Objects.requireNonNull(supplier, "supplier");

        T defaultValue;
        try {
            defaultValue = supplier.get();
        } catch (RuntimeException e) {
            StackTraceElement caller = findCaller(NotNull.class);
            log.error("NotNull4J: Supplier threw at {}", caller, e);
            throw new NullPointerException(
                    "NotNull4J: orGetOptional supplier threw exception at " + caller);
        }

        if (defaultValue == null) {
            StackTraceElement caller = findCaller(NotNull.class);
            log.error("NotNull4J: Supplier returned null at {}", caller);
            throw new NullPointerException(
                    "NotNull4J: orGetOptional supplier returned null at " + caller);
        }

        return defaultValue;
    }

    /**
     * LOG-AND-CONTINUE — attempts to get a fallback but returns null if supplier fails.
     *
     * <p>Use this during legacy migrations where a crash is worse than a null.
     * The {@code @Nullable} return type makes the contract honest: this method can return null.
     *
     * <p>Logs warnings when the supplier returns null and errors when it throws.
     *
     * @param value the potentially null value
     * @param supplier the non-null supplier providing a fallback (can return null)
     * @param context description of where this is used (appears in logs)
     * @param <T> the value type
     * @return the value, fallback, or null if both fail
     */
    @Nullable
    public static <T> T orLogGet(
            @Nullable T value,
            @NonNull Supplier<@Nullable T> supplier,
            @NonNull String context
    ) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(context, "context");

        if (value != null) {
            return value;
        }

        try {
            T result = supplier.get();
            if (result == null) {
                log.warn("NotNull4J: [{}] Supplier returned null at {}",
                        context, findCaller(NotNull.class));
            }
            return result;
        } catch (RuntimeException e) {
            log.error("NotNull4J: [{}] Supplier threw at {}",
                    context, findCaller(NotNull.class), e);
            return null;
        }
    }

    /** FAIL-FAST — throw if null (assertions / invariants). */
    @NonNull
    public static <T> T orThrow(@Nullable T value) {
        if (value == null) {
            log.error("NotNull.orThrowOptional() received null{}", callerSuffix(NotNull.class));
            throw new NullPointerException("NotNull.orThrowOptional() received null");
        }
        return value;
    }

    /** FAIL-FAST — throw if null with message. */
    @NonNull
    public static <T> T orThrow(
            @Nullable T value,
            @NonNull String message
    ) {
        Objects.requireNonNull(message, "message");
        if (value == null) {
            log.error("NotNull.orThrowOptional() failed{}: {}", callerSuffix(NotNull.class), message);
            throw new NullPointerException(message);
        }
        return value;
    }

    /**
     * DEFENSIVE — verify that a value is non-null at runtime.
     *
     * <p>Use this when assigning from {@code @NonNull} parameters, fields, or variables
     * to {@code @LocalNotNull} local variables. Even though static analysis says the value
     * is non-null, this performs a runtime check to defend against:
     * <ul>
     *   <li>Reflection abuse that bypasses null-safety</li>
     *   <li>Serialization/deserialization bugs</li>
     *   <li>Bugs in third-party libraries</li>
     *   <li>Raw types bypassing generics</li>
     * </ul>
     *
     * <p>This is essentially {@link #orThrow(Object)} but with documentation that explains
     * its specific use case: defensive verification of supposedly non-null values.
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * public void processUser(@NonNull User user) {
     *     // Even though 'user' is @NonNull, verify at assignment
     *     final @LocalNotNull User verifiedUser = NotNull.verify(user);
     *
     *     // Now guaranteed non-null both statically and at runtime
     * }
     *
     * public void reassignVariable() {
     *     @LocalNotNull String name = NotNull.orThrowOptional(service.getName());
     *
     *     // Later, assign from another @NonNull source
     *     @NonNull String updatedName = getUpdatedName();
     *     name = NotNull.verify(updatedName);  // Runtime check before assignment
     * }
     * }</pre>
     *
     * <h2>Why Not Direct Assignment?</h2>
     * <p>PMD rules prevent direct assignment from {@code @NonNull} to {@code @LocalNotNull}:
     * <pre>{@code
     * @NonNull String param = ...;
     * @LocalNotNull String local = param;  // PMD violation!
     * @LocalNotNull String local = NotNull.verify(param);  // OK
     * }</pre>
     *
     * <p>This forces an explicit runtime check, ensuring that even if the {@code @NonNull}
     * contract was violated (via reflection, bugs, etc.), the {@code @LocalNotNull} variable
     * is truly non-null.
     *
     * @param value the supposedly non-null value to verify
     * @param <T> the value type
     * @return the verified non-null value
     * @throws NullPointerException if the value is null (contract violation)
     */
    @NonNull
    public static <T> T verify(@NonNull T value) {
        if (value == null) {
            log.error("NotNull.verify() received null (contract violation!){}",
                    callerSuffix(NotNull.class));
            throw new NullPointerException(
                    "NotNull.verify() received null - @NonNull contract was violated");
        }
        // Programmatic "Heartbeat" check
        if (traceVerifySuccess && log.isTraceEnabled()) {
            log.trace("NotNull.verify() confirmed non-null{}", callerSuffix(NotNull.class));
        }

        return value;
    }

    /**
     * DEFENSIVE — verify that a value is non-null at runtime with custom message.
     *
     * <p>Same as {@link #verify(Object)} but with a custom error message.
     *
     * @param value the supposedly non-null value to verify
     * @param message the error message if null
     * @param <T> the value type
     * @return the verified non-null value
     * @throws NullPointerException if the value is null
     */
    @NonNull
    public static <T> T verify(@NonNull T value, @NonNull String message) {
        Objects.requireNonNull(message, "message");
        if (value == null) {
            log.error("NotNull.verify() failed (contract violation!){}: {}",
                    callerSuffix(NotNull.class), message);
            throw new NullPointerException(message);
        }
        // Programmatic "Heartbeat" check
        if (traceVerifySuccess && log.isTraceEnabled()) {
            log.trace("NotNull.verify() confirmed non-null{}", callerSuffix(NotNull.class));
        }
        return value;
    }

    /** LOG-AND-CONTINUE — warn but don't crash (migration / monitoring). */
    @NonNull
    public static <T> T orLog(
            @Nullable T value,
            @NonNull T defaultValue
    ) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        if (value == null) {
            log.warn("Unexpected null in NotNull.orLogOptional(){} - using default",
                    callerSuffix(NotNull.class));
            return defaultValue;
        }
        return value;
    }

    /** LOG-AND-CONTINUE — warn with explicit context. */
    @NonNull
    public static <T> T orLog(
            @Nullable T value,
            @NonNull T defaultValue,
            @NonNull String context
    ) {
        Objects.requireNonNull(context, "context");
        if (value == null) {
            log.warn("Unexpected null in NotNull.orLogOptional() (context={}){} - using default",
                    context, callerSuffix(NotNull.class));
            return defaultValue;
        }
        return value;
    }

    // =====================================================================================
    // Optional policies (same intent, different input type)
    //
    // Optional must not contain null by contract, but corrupt Optionals can appear
    // via raw types, reflection, or deserialization. We normalize:
    //   empty OR corrupt -> null (absent)
    // =====================================================================================

    /** GRACEFUL — use default if Optional is empty (or corrupt). */
    @NonNull
    public static <T> T orDefaultOptional(
            @NonNull Optional<? extends T> opt,
            @NonNull T defaultValue
    ) {
        Objects.requireNonNull(opt, "opt");
        Objects.requireNonNull(defaultValue, "defaultValue");
        T value = unwrapOptional(opt);
        return (value != null) ? value : defaultValue;
    }

    /**
     * FAIL-FAST — use supplier if Optional is empty (or corrupt).
     *
     * <p>Throws NPE if the supplier is null, returns null, or throws.
     * This ensures the {@code @NonNull} contract is never violated.
     *
     * @param opt the optional to unwrap
     * @param defaultSupplier the non-null supplier providing a fallback
     * @param <T> the value type
     * @return the optional value or supplier result, guaranteed non-null
     * @throws NullPointerException if supplier is null, returns null, or throws
     */
    @NonNull
    public static <T> T orGetOptional(
            @NonNull Optional<? extends T> opt,
            @NonNull Supplier<@NonNull T> defaultSupplier
    ) {
        Objects.requireNonNull(opt, "opt");
        Objects.requireNonNull(defaultSupplier, "defaultSupplier");

        T value = unwrapOptional(opt);
        if (value != null) {
            return value;
        }

        T defaultValue;
        try {
            defaultValue = defaultSupplier.get();
        } catch (RuntimeException e) {
            StackTraceElement caller = findCaller(NotNull.class);
            log.error("NotNull4J: Supplier threw at {} in orGetOptional(Optional)", caller, e);
            throw new NullPointerException(
                    "NotNull4J: orGetOptional(Optional) supplier threw exception at " + caller);
        }

        if (defaultValue == null) {
            StackTraceElement caller = findCaller(NotNull.class);
            log.error("NotNull4J: Supplier returned null at {} in orGetOptional(Optional)", caller);
            throw new NullPointerException(
                    "NotNull4J: orGetOptional(Optional) supplier returned null at " + caller);
        }

        return defaultValue;
    }

    /** FAIL-FAST — throw if Optional is empty (or corrupt). */
    @NonNull
    public static <T> T orThrowOptional(@NonNull Optional<? extends T> opt) {
        Objects.requireNonNull(opt, "opt");
        T value = unwrapOptional(opt);
        if (value == null) {
            log.error("NotNull.orThrowOptional(Optional) received empty/corrupt Optional{}",
                    callerSuffix(NotNull.class));
            throw new NullPointerException(
                    "NotNull.orThrowOptional(Optional) received empty/corrupt Optional");
        }
        return value;
    }

    /** FAIL-FAST — throw if Optional is empty (or corrupt) with message. */
    @NonNull
    public static <T> T orThrowOptional(
            @NonNull Optional<? extends T> opt,
            @NonNull String message
    ) {
        Objects.requireNonNull(opt, "opt");
        Objects.requireNonNull(message, "message");
        T value = unwrapOptional(opt);
        if (value == null) {
            log.error("NotNull.orThrowOptional(Optional) failed{}: {}",
                    callerSuffix(NotNull.class), message);
            throw new NullPointerException(message);
        }
        return value;
    }

    /** LOG-AND-CONTINUE — warn if Optional is empty (or corrupt). */
    @NonNull
    public static <T> T orLogOptional(
            @NonNull Optional<? extends T> opt,
            @NonNull T defaultValue
    ) {
        Objects.requireNonNull(opt, "opt");
        Objects.requireNonNull(defaultValue, "defaultValue");
        T value = unwrapOptional(opt);
        if (value == null) {
            log.warn("Unexpected empty/corrupt Optional in NotNull.orLogOptional(){} - using default",
                    callerSuffix(NotNull.class));
            return defaultValue;
        }
        return value;
    }

    /** LOG-AND-CONTINUE — warn with explicit context. */
    @NonNull
    public static <T> T orLogOptional(
            @NonNull Optional<? extends T> opt,
            @NonNull T defaultValue,
            @NonNull String context
    ) {
        Objects.requireNonNull(opt, "opt");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(context, "context");
        T value = unwrapOptional(opt);
        if (value == null) {
            log.warn(
                    "Unexpected empty/corrupt Optional in NotNull.orLogOptional() (context={}){} - using default",
                    context, callerSuffix(NotNull.class));
            return defaultValue;
        }
        return value;
    }

    // =====================================================================================
    // Bridging helpers
    // =====================================================================================

    /** Convert a nullable value to Optional. */
    @NonNull
    public static <T> Optional<T> optional(@Nullable T value) {
        return Optional.ofNullable(value);
    }

    /** Convert an Optional to a nullable value (escape hatch). */
    @Nullable
    public static <T> T orNull(@NonNull Optional<? extends T> opt) {
        Objects.requireNonNull(opt, "opt");
        return unwrapOptional(opt);
    }

    // =====================================================================================
    // Internals
    // =====================================================================================

    /**
     * Normalize Optional into a nullable value:
     * <ul>
     *   <li>empty → null</li>
     *   <li>present → value</li>
     *   <li>corrupt Optional → null (logged)</li>
     * </ul>
     */
    @Nullable
    private static <T> T unwrapOptional(@NonNull Optional<? extends T> opt) {
        try {
            return opt.orElse(null);
        } catch (RuntimeException e) {
            log.error("Corrupt Optional encountered in NotNull; treating as empty{} (optionalType={})",
                    callerSuffix(NotNull.class),
                    opt.getClass().getName(),
                    e);
            return null;
        }
    }

    /**
     * Returns a formatted suffix like " at: com.example.Foo.bar(Foo.java:12)"
     * or an empty string if caller capture is disabled or unavailable.
     */
    @NonNull
    private static String callerSuffix(@NonNull Class<?> utilityClass) {
        StackTraceElement caller = findCaller(utilityClass);
        return (caller != null) ? " at: " + caller : "";
    }

    /**
     * Best-effort caller detection:
     * <ul>
     *   <li>returns null if disabled via property</li>
     *   <li>returns null if stack walking is unavailable (e.g. native images)</li>
     *   <li>never affects correctness</li>
     * </ul>
     */
    @Nullable
    private static StackTraceElement findCaller(@NonNull Class<?> utilityClass) {
        if (!captureCaller) {
            return null;
        }
        try {
            return STACK_WALKER.walk(s -> s
                    .filter(f -> f.getDeclaringClass() != utilityClass)
                    .findFirst()
                    .map(StackWalker.StackFrame::toStackTraceElement)
                    .orElse(null));
        } catch (Throwable t) {
            // Best-effort only
            return null;
        }
    }
}