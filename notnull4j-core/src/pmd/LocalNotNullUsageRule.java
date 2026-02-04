package tech.robd.notnull.pmd;

import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * PMD rule enforcing @LocalNotNull usage constraints.
 * 
 * <p>Ensures that:
 * <ul>
 *   <li>@LocalNotNull variables are initialized with runtime-safe NotNull.* methods</li>
 *   <li>@LocalNotNull variables are only reassigned using runtime-safe NotNull.* methods</li>
 *   <li>Only whitelisted NotNull methods that provide runtime guarantees are allowed</li>
 * </ul>
 * 
 * <p>Example violations:
 * <pre>
 * // ❌ Uninitialized
 * final @LocalNotNull String name;
 * 
 * // ❌ Direct assignment (no runtime check)
 * final @LocalNotNull String name = getValue();
 * 
 * // ❌ Unsafe method (returns @Nullable)
 * final @LocalNotNull String name = NotNull.orNull(opt);
 * 
 * // ✅ Correct usage
 * final @LocalNotNull String name = NotNull.orThrow(getValue());
 * </pre>
 */
public class LocalNotNullUsageRule extends AbstractJavaRule {
    
    /**
     * Whitelisted NotNull methods that provide RUNTIME null-safety guarantees.
     * 
     * <p>These methods either:
     * <ul>
     *   <li>Throw an exception if they would return null (verify, orThrow, orGet)</li>
     *   <li>Verify their parameters at runtime (orDefault, orLog check defaultValue)</li>
     *   <li>Return non-null values by construction (listOrEmpty, stringOrEmpty, etc.)</li>
     * </ul>
     * 
     * <p>All methods enforce their @NonNull contracts at runtime, not just compile-time.
     */
    private static final Set<String> ALLOWED_METHODS = Set.of(
            // Fail-fast methods (throw if value is null)
            "verify",
            "orThrow",
            "orGet",
            "orThrowOptional",
            "orGetOptional",
            
            // Graceful methods (throw if defaultValue is null, runtime-verified)
            "orDefault",
            "orDefaultOptional",
            "orLog",
            "orLogOptional",
            
            // Collection normalizers (return @NonNull empty collections by construction)
            "listOrEmpty",
            "setOrEmpty",
            "mapOrEmpty",
            "stringOrEmpty"
            
            // Excluded: orLogGet (returns @Nullable)
            // Excluded: orNull (returns @Nullable)
            // Excluded: optional (returns Optional, not T)
    );
    
    private static final String NOTNULL_CLASS_FQN = "tech.robd.notnull.NotNull";
    private static final String NOTNULL_CLASS_SIMPLE = "NotNull";
    private static final String LOCAL_NOT_NULL_FQN = "tech.robd.notnull.LocalNotNull";
    private static final String LOCAL_NOT_NULL_SIMPLE = "LocalNotNull";
    
    // Track @LocalNotNull variables in current scope
    private final Map<String, ASTLocalVariableDeclaration> localNotNullVariables = new HashMap<>();
    
    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        // Clear state for each method
        localNotNullVariables.clear();
        return super.visit(node, data);
    }
    
    @Override
    public Object visit(ASTLocalVariableDeclaration node, Object data) {
        if (!hasLocalNotNullAnnotation(node)) {
            return super.visit(node, data);
        }
        
        ASTVariableDeclarator declarator = node.getFirstDescendantOfType(ASTVariableDeclarator.class);
        if (declarator == null) {
            return super.visit(node, data);
        }
        
        // Track this variable for reassignment checks
        String variableName = declarator.getName();
        localNotNullVariables.put(variableName, node);
        
        ASTExpression initializer = declarator.getInitializer();
        if (initializer == null) {
            addViolationWithMessage(data, node,
                    "@LocalNotNull variable '" + variableName + 
                    "' must be initialized using NotNull safe methods (verify, orThrow, orGet, orDefault, orLog, etc.)");
            return data;
        }
        
        if (!isAllowedInitializer(initializer)) {
            addViolationWithMessage(data, node,
                    "@LocalNotNull variable '" + variableName + 
                    "' must be assigned using allowed NotNull methods: " + ALLOWED_METHODS);
        }
        
        return super.visit(node, data);
    }
    
    @Override
    public Object visit(ASTAssignmentExpression node, Object data) {
        // Check if we're reassigning a @LocalNotNull variable
        ASTExpression leftSide = node.getLeftOperand();
        if (leftSide == null) {
            return super.visit(node, data);
        }
        
        // Get variable name from left side
        ASTName nameNode = leftSide.getFirstDescendantOfType(ASTName.class);
        if (nameNode == null) {
            return super.visit(node, data);
        }
        
        String variableName = nameNode.getImage();
        
        // Check if this is a @LocalNotNull variable
        if (!localNotNullVariables.containsKey(variableName)) {
            return super.visit(node, data);
        }
        
        // Check right side - must be allowed NotNull method
        ASTExpression rightSide = node.getRightOperand();
        if (!isAllowedInitializer(rightSide)) {
            addViolationWithMessage(data, node,
                    "@LocalNotNull variable '" + variableName + 
                    "' can only be reassigned using allowed NotNull methods: " + ALLOWED_METHODS);
        }
        
        return super.visit(node, data);
    }
    
    /**
     * Check if variable has @LocalNotNull annotation.
     */
    private boolean hasLocalNotNullAnnotation(ASTLocalVariableDeclaration node) {
        ASTAnnotation annotation = node.getFirstDescendantOfType(ASTAnnotation.class);
        if (annotation == null) {
            return false;
        }
        
        String annotationName = annotation.getAnnotationName();
        return LOCAL_NOT_NULL_FQN.equals(annotationName) 
            || LOCAL_NOT_NULL_SIMPLE.equals(annotationName);
    }
    
    /**
     * Check if initializer expression is an allowed NotNull method call.
     */
    private boolean isAllowedInitializer(ASTExpression initializer) {
        ASTMethodCall call = initializer.getFirstDescendantOfType(ASTMethodCall.class);
        return call != null && isAllowedNotNullCall(call);
    }
    
    /**
     * Check if method call is on NotNull class with allowed method name.
     */
    private boolean isAllowedNotNullCall(ASTMethodCall call) {
        String methodName = call.getMethodName();
        if (!ALLOWED_METHODS.contains(methodName)) {
            return false;
        }
        
        // Check if it's a static method call on NotNull class
        ASTPrimaryExpression primary = call.getFirstParentOfType(ASTPrimaryExpression.class);
        if (primary == null) {
            return false;
        }
        
        ASTName qualifier = primary.getFirstDescendantOfType(ASTName.class);
        if (qualifier == null) {
            return false;
        }
        
        String qualifiedName = qualifier.getImage();
        
        // Accept both fully qualified and simple class name
        // e.g., tech.robd.util.NotNull.verify() or NotNull.verify()
        return NOTNULL_CLASS_FQN.equals(qualifiedName) 
            || qualifiedName.endsWith("." + NOTNULL_CLASS_SIMPLE + "." + methodName)
            || qualifiedName.equals(NOTNULL_CLASS_SIMPLE);
    }
}
