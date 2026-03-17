package tech.robd.notnull;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Precondition and state-assertion guards, mirroring Kotlin's {@code require},
 * {@code check}, {@code requireNotNull}, and {@code checkNotNull} functions.
 *
 * <p>This class provides two families of methods that share a single semantic split:
 *
 * <ul>
 *   <li><strong>require*</strong> — the condition or value is the <em>caller's responsibility</em>.
 *       Failure means the caller passed bad input → throws {@link IllegalArgumentException} → 4xx.</li>
 *   <li><strong>check*</strong> — the condition or value is <em>our responsibility</em>.
 *       Failure means we have a bug or misconfiguration → throws {@link IllegalStateException} → 5xx.</li>
 * </ul>
 *
 * <p>This mirrors both Kotlin's standard library and idiomatic Java (Guava's
 * {@code Preconditions.checkArgument} / {@code Preconditions.checkState}), throwing
 * standard exception types directly rather than custom subclasses. The key addition
 * over {@link java.util.Objects#requireNonNull} is that the fault distinction is
 * explicit in the method name rather than left to the caller to choose the right
 * exception type.
 *
 * <h2>Null-specific guards — narrowing @Nullable to @NonNull</h2>
 * <p>{@link #requireNotNull} and {@link #checkNotNull} accept a {@code @Nullable T}
 * and return {@code @NonNull T}, actively narrowing the type for JSpecify-aware
 * static analysers and IDEs — not just a runtime check:
 *
 * <pre>{@code
 * @LocalNotNull String name  = Guard.requireNotNull(dto.getName(), "name must not be null");
 * @LocalNotNull Connection c = Guard.checkNotNull(pool.acquire(), "connection pool exhausted");
 * }</pre>
 *
 * <h2>Boolean predicate guards</h2>
 * <p>{@link #require} and {@link #check} validate arbitrary conditions at service
 * boundaries where multiple fields need checking:
 *
 * <pre>{@code
 * Guard.require(age >= 0, "age must be non-negative");
 * Guard.require(ids.size() <= MAX, () -> "too many ids: " + ids.size() + ", max=" + MAX);
 * Guard.check(connection.isOpen(), "connection should be open at this point in the lifecycle");
 * }</pre>
 *
 * <h2>Message laziness</h2>
 * <p>All methods have overloads accepting a {@link Supplier}{@code <String>} so that
 * expensive message construction is only evaluated on failure — matching Kotlin's
 * {@code require(condition) { "msg" }} lambda syntax.
 */
public final class Guard {

    private Guard() {
        // utility class
    }

    // -------------------------------------------------------------------------
    // requireNotNull — null check, caller's fault, throws IllegalArgumentException (4xx)
    // -------------------------------------------------------------------------

    /**
     * REQUIRE — asserts that {@code value} is not null because the caller supplied it.
     *
     * <p>Accepts {@code @Nullable T} and returns {@code @NonNull T}, narrowing the type
     * for JSpecify-aware static analysers. A null here is the <em>caller's fault</em>;
     * the resulting {@link IllegalArgumentException} maps naturally to an HTTP 4xx response.
     *
     * <pre>{@code
     * @LocalNotNull String userId = Guard.requireNotNull(request.getUserId(), "userId must not be null");
     * }</pre>
     *
     * @param <T>     the type of the value
     * @param value   the potentially-null value to validate
     * @param message the detail message used if {@code value} is null
     * @return {@code value}, guaranteed non-null
     * @throws IllegalArgumentException if {@code value} is null
     */
    @NonNull
    public static <T> T requireNotNull(@Nullable T value, @NonNull String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * REQUIRE — asserts that {@code value} is not null because the caller supplied it.
     *
     * <p>The {@code messageSupplier} is only evaluated when {@code value} is null,
     * avoiding unnecessary string construction in the happy path.
     *
     * <pre>{@code
     * @LocalNotNull String userId = Guard.requireNotNull(
     *     request.getUserId(),
     *     () -> "userId must not be null, request=" + request.getId()
     * );
     * }</pre>
     *
     * @param <T>             the type of the value
     * @param value           the potentially-null value to validate
     * @param messageSupplier lazy supplier for the detail message
     * @return {@code value}, guaranteed non-null
     * @throws IllegalArgumentException if {@code value} is null
     */
    @NonNull
    public static <T> T requireNotNull(@Nullable T value, @NonNull Supplier<String> messageSupplier) {
        if (value == null) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return value;
    }

    // -------------------------------------------------------------------------
    // checkNotNull — null check, our fault, throws IllegalStateException (5xx)
    // -------------------------------------------------------------------------

    /**
     * CHECK — asserts that {@code value} is not null as an internal state invariant.
     *
     * <p>Accepts {@code @Nullable T} and returns {@code @NonNull T}, narrowing the type
     * for JSpecify-aware static analysers. A null here is <em>our bug</em>; the resulting
     * {@link IllegalStateException} maps naturally to an HTTP 5xx response.
     *
     * <pre>{@code
     * @LocalNotNull UserCache cache = Guard.checkNotNull(userCache, "userCache should have been initialised by startup");
     * }</pre>
     *
     * @param <T>     the type of the value
     * @param value   the potentially-null value to assert
     * @param message the detail message used if {@code value} is null
     * @return {@code value}, guaranteed non-null
     * @throws IllegalStateException if {@code value} is null
     */
    @NonNull
    public static <T> T checkNotNull(@Nullable T value, @NonNull String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    /**
     * CHECK — asserts that {@code value} is not null as an internal state invariant.
     *
     * <p>The {@code messageSupplier} is only evaluated when {@code value} is null,
     * avoiding unnecessary string construction in the happy path.
     *
     * <pre>{@code
     * @LocalNotNull Connection conn = Guard.checkNotNull(
     *     pool.peek(),
     *     () -> "connection pool empty — pool size=" + pool.capacity()
     * );
     * }</pre>
     *
     * @param <T>             the type of the value
     * @param value           the potentially-null value to assert
     * @param messageSupplier lazy supplier for the detail message
     * @return {@code value}, guaranteed non-null
     * @throws IllegalStateException if {@code value} is null
     */
    @NonNull
    public static <T> T checkNotNull(@Nullable T value, @NonNull Supplier<String> messageSupplier) {
        if (value == null) {
            throw new IllegalStateException(messageSupplier.get());
        }
        return value;
    }

    // -------------------------------------------------------------------------
    // require — boolean predicate, caller's fault, throws IllegalArgumentException (4xx)
    // -------------------------------------------------------------------------

    /**
     * REQUIRE — asserts that {@code condition} is true because a caller-supplied
     * value or argument must satisfy it.
     *
     * <p>A false condition here is the <em>caller's fault</em>; the resulting
     * {@link IllegalArgumentException} maps naturally to an HTTP 4xx response.
     *
     * <pre>{@code
     * Guard.require(pageSize > 0, "pageSize must be positive");
     * Guard.require(username.length() <= 50, "username exceeds maximum length of 50");
     * }</pre>
     *
     * @param condition the condition that must be true
     * @param message   the detail message used if {@code condition} is false
     * @throws IllegalArgumentException if {@code condition} is false
     */
    public static void require(boolean condition, @NonNull String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * REQUIRE — asserts that {@code condition} is true because a caller-supplied
     * value or argument must satisfy it.
     *
     * <p>The {@code messageSupplier} is only evaluated when {@code condition} is
     * false, avoiding unnecessary string construction in the happy path.
     *
     * <pre>{@code
     * Guard.require(
     *     ids.size() <= MAX_BATCH,
     *     () -> "batch too large: " + ids.size() + ", max=" + MAX_BATCH
     * );
     * }</pre>
     *
     * @param condition       the condition that must be true
     * @param messageSupplier lazy supplier for the detail message
     * @throws IllegalArgumentException if {@code condition} is false
     */
    public static void require(boolean condition, @NonNull Supplier<String> messageSupplier) {
        if (!condition) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
    }

    // -------------------------------------------------------------------------
    // check — boolean predicate, our fault, throws IllegalStateException (5xx)
    // -------------------------------------------------------------------------

    /**
     * CHECK — asserts that {@code condition} is true as an internal state invariant.
     *
     * <p>A false condition here is <em>our bug</em>; the resulting
     * {@link IllegalStateException} maps naturally to an HTTP 5xx response.
     *
     * <pre>{@code
     * Guard.check(connection.isOpen(), "connection should be open at this point in the lifecycle");
     * Guard.check(scheduler.isRunning(), "scheduler must be running before tasks can be submitted");
     * }</pre>
     *
     * @param condition the condition that must be true
     * @param message   the detail message used if {@code condition} is false
     * @throws IllegalStateException if {@code condition} is false
     */
    public static void check(boolean condition, @NonNull String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * CHECK — asserts that {@code condition} is true as an internal state invariant.
     *
     * <p>The {@code messageSupplier} is only evaluated when {@code condition} is
     * false, avoiding unnecessary string construction in the happy path.
     *
     * <pre>{@code
     * Guard.check(
     *     cache.isReady(),
     *     () -> "cache not ready — state=" + cache.getState() + ", uptime=" + cache.getUptime()
     * );
     * }</pre>
     *
     * @param condition       the condition that must be true
     * @param messageSupplier lazy supplier for the detail message
     * @throws IllegalStateException if {@code condition} is false
     */
    public static void check(boolean condition, @NonNull Supplier<String> messageSupplier) {
        if (!condition) {
            throw new IllegalStateException(messageSupplier.get());
        }
    }
}