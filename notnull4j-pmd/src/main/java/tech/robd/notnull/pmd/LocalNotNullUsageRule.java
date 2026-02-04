package tech.robd.notnull.pmd;

import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.reporting.RuleContext; // Required for PMD 7 reporting
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LocalNotNullUsageRule extends AbstractJavaRule {

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "verify", "orThrow", "orGet", "orThrowOptional", "orGetOptional",
            "orDefault", "orDefaultOptional", "orLog", "orLogOptional",
            "listOrEmpty", "setOrEmpty", "mapOrEmpty", "stringOrEmpty"
    );

    private static final String NOTNULL_CLASS_FQN = "tech.robd.notnull.NotNull";
    private static final String NOTNULL_CLASS_SIMPLE = "NotNull";
    private static final String LOCAL_NOT_NULL_FQN = "tech.robd.notnull.LocalNotNull";
    private static final String LOCAL_NOT_NULL_SIMPLE = "LocalNotNull";

    // Track @LocalNotNull variables in current scope
    private final Map<String, ASTLocalVariableDeclaration> localNotNullVariables = new HashMap<>();

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        localNotNullVariables.clear(); // Reset state for each method
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTLocalVariableDeclaration node, Object data) {
        if (!hasLocalNotNullAnnotation(node)) {
            return super.visit(node, data);
        }

        // PMD 7: Use getVarIds() stream to handle variables
        ASTVariableId variableId = node.getVarIds().first();
        if (variableId == null) return super.visit(node, data);

        String variableName = variableId.getName();
        localNotNullVariables.put(variableName, node);

        // Check initializer via the declarator
        ASTVariableDeclarator declarator = node.descendants(ASTVariableDeclarator.class).first();
        if (declarator == null) return super.visit(node, data);

        ASTExpression initializer = declarator.getInitializer();
        if (initializer == null || !isAllowedInitializer(initializer)) {
            reportViolation(data, node, variableName);
        }

        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTAssignmentExpression node, Object data) {
        ASTExpression leftSide = node.getLeftOperand();

        // PMD 7: Specific node for variable access
        ASTVariableAccess variableAccess = leftSide.descendants(ASTVariableAccess.class).first();
        if (variableAccess == null) return super.visit(node, data);

        String name = variableAccess.getName();
        if (localNotNullVariables.containsKey(name)) {
            if (!isAllowedInitializer(node.getRightOperand())) {
                reportViolation(data, node, name);
            }
        }
        return super.visit(node, data);
    }

    /**
     * Check if variable has @LocalNotNull annotation in PMD 7.
     */
    private boolean hasLocalNotNullAnnotation(ASTLocalVariableDeclaration node) {
        // node.descendants() returns a NodeStream in PMD 7
        return node.descendants(ASTAnnotation.class)
                .any(a -> LOCAL_NOT_NULL_FQN.equals(a.getSimpleName())
                        || LOCAL_NOT_NULL_SIMPLE.equals(a.getSimpleName()));
    }

    /**
     * Missing Helper: Check if initializer is a whitelisted NotNull call
     */
    private boolean isAllowedInitializer(ASTExpression expr) {
        if (expr == null) return false;
        ASTMethodCall call = expr.descendants(ASTMethodCall.class).first();
        if (call == null) return false;

        if (!ALLOWED_METHODS.contains(call.getMethodName())) return false;

        ASTExpression qualifier = call.getQualifier();
        if (qualifier == null) return false;

        String text = String.valueOf(qualifier.getText());
        return NOTNULL_CLASS_FQN.equals(text) || NOTNULL_CLASS_SIMPLE.equals(text);
    }

    /**
     * Corrected PMD 7 Violation Reporting
     */
    private void reportViolation(Object data, JavaNode node, String varName) {
        // PMD 7 requires asCtx(data) or the new fluent builder
        RuleContext ctx = (RuleContext) data;
        ctx.addViolation(node, varName);
    }
}