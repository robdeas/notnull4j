# NotNull4J PMD Rules

PMD rules for enforcing `@LocalNotNull` usage in NotNull4J.

## Overview

The `LocalNotNullUsageRule` ensures that variables annotated with `@LocalNotNull` are only assigned via NotNull methods that provide runtime null-safety guarantees.

## Installation

### Maven

Add to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-pmd-plugin</artifactId>
            <version>3.21.0</version>
            <configuration>
                <rulesets>
                    <ruleset>tech/robd/notnull/pmd/notnull4j-ruleset.xml</ruleset>
                </rulesets>
            </configuration>
            <dependencies>
                <dependency>
                    <groupId>tech.robd</groupId>
                    <artifactId>notnull4j-pmd</artifactId>
                    <version>${notnull4j.version}</version>
                </dependency>
            </dependencies>
        </plugin>
    </plugins>
</build>
```

### Gradle

Add to your `build.gradle`:

```gradle
plugins {
    id 'pmd'
}

pmd {
    toolVersion = '7.21.0'
    ruleSetFiles = files("config/pmd/notnull4j-ruleset.xml")
}

dependencies {
    pmd 'tech.robd:notnull4j-pmd:${notnull4jVersion}'
}
```

## Rule: LocalNotNullUsageRule

**Priority:** 1 (Highest)

**Category:** Error Prone

### Description

Enforces that `@LocalNotNull` variables are initialized and reassigned only via NotNull methods that provide runtime null-safety guarantees.

### Rationale

The `@LocalNotNull` annotation indicates that a local variable is guaranteed to be non-null at runtime. To maintain this guarantee, the variable must only be assigned via methods that verify this at runtime, not just compile-time.

### Allowed Methods

**Fail-fast** (throw if value is null):
- `verify(T)`
- `verify(T, String)`
- `orThrow(T)`
- `orThrow(T, String)`
- `orGet(T, Supplier<T>)`
- `orThrowOptional(Optional<T>)`
- `orThrowOptional(Optional<T>, String)`
- `orGetOptional(Optional<T>, Supplier<T>)`

**Graceful** (use default, throw if default is null):
- `orDefault(T, T)`
- `orDefaultOptional(Optional<T>, T)`
- `orLog(T, T)`
- `orLog(T, T, String)`
- `orLogOptional(Optional<T>, T)`
- `orLogOptional(Optional<T>, T, String)`

**Normalizers** (return non-null by construction):
- `listOrEmpty(List<T>)`
- `setOrEmpty(Set<T>)`
- `mapOrEmpty(Map<K,V>)`
- `stringOrEmpty(String)`

### Not Allowed

Methods that return `@Nullable`:
- `orLogGet(T, Supplier<T>, String)` - returns `@Nullable`
- `orNull(Optional<T>)` - returns `@Nullable`
- `optional(T)` - returns `Optional<T>`, not `T`

### Examples

#### ❌ Violations

```java
// Uninitialized
final @LocalNotNull String name;

// Direct assignment (no runtime check)
final @LocalNotNull String name = getValue();

// Unsafe method (returns @Nullable)
final @LocalNotNull String name = NotNull.orNull(opt);

// Wrong method (returns @Nullable)
final @LocalNotNull String name = NotNull.orLogGet(value, () -> "fallback", "ctx");

// Unsafe reassignment
final @LocalNotNull String name = NotNull.verify(getValue());
name = getOtherValue();  // ❌ No runtime check
```

#### ✅ Correct Usage

```java
// Fail-fast
final @LocalNotNull String name = NotNull.verify(getValue());
final @LocalNotNull String name = NotNull.orThrow(getValue(), "Name required");

// Graceful with default
final @LocalNotNull String name = NotNull.orDefault(getValue(), "Anonymous");
final @LocalNotNull String email = NotNull.orLog(getEmail(), "noreply@example.com");

// Collection normalizers
final @LocalNotNull List<String> items = NotNull.listOrEmpty(getItems());
final @LocalNotNull String text = NotNull.stringOrEmpty(getText());

// Safe reassignment
final @LocalNotNull String name = NotNull.verify(getValue());
name = NotNull.orDefault(getUpdated(), "fallback");
```

## Why This Rule Matters

### 1. Runtime Safety

Without this rule, `@LocalNotNull` is just documentation:

```java
// Compiles but breaks runtime guarantee
final @LocalNotNull String name = getValue();  // Might be null!
```

With this rule:

```java
// Enforced: must use runtime-safe method
final @LocalNotNull String name = NotNull.verify(getValue());  // ✅ Verified
```

### 2. Defense in Depth

Even with JSpecify annotations, runtime checks catch:
- Reflection abuse
- Serialization bugs
- Library bugs that bypass static analysis
- Raw types that defeat generics

### 3. Team Consistency

Prevents developers from accidentally breaking the null-safety contract:

```java
// Someone adds this line months later:
name = someMethodThatReturnsNull();  // ❌ Caught by PMD
```

## Configuration

### Suppressing Violations

If you have a legitimate reason to bypass the rule:

```java
@SuppressWarnings("PMD.LocalNotNullUsageRule")
final @LocalNotNull String name = legacyMethod();
```

**Use sparingly** - you're defeating the purpose of `@LocalNotNull`.

### Custom Ruleset

Copy `notnull4j-ruleset.xml` and modify:

```xml
<rule ref="tech.robd.notnull.pmd.LocalNotNullUsageRule">
    <priority>2</priority>  <!-- Lower priority -->
</rule>
```

## Integration with IDEs

### IntelliJ IDEA

1. Install PMD plugin
2. Settings → Tools → PMD
3. Add ruleset: `notnull4j-ruleset.xml`
4. Enable "Run PMD on save"

### Eclipse

1. Install PMD plugin
2. Window → Preferences → PMD
3. Add ruleset
4. Enable in project properties

### VS Code

1. Install "vscode-pmd" extension
2. Configure ruleset in `.vscode/settings.json`:

```json
{
  "pmd.rulesets": ["notnull4j-ruleset.xml"]
}
```

## CI/CD Integration

### Fail Build on Violations

**Maven:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Gradle:**

```gradle
pmd {
    ignoreFailures = false
}
```

## Troubleshooting

### "Rule not found"

Ensure the PMD plugin dependency is added and the ruleset path is correct.

### "Too many violations"

Start with suppressing existing violations, then enforce for new code:

```java
@SuppressWarnings("PMD.LocalNotNullUsageRule")
```

### False Positives

Report issues at: https://github.com/yourusername/notnull4j/issues

## License

MIT License - Same as NotNull4J
