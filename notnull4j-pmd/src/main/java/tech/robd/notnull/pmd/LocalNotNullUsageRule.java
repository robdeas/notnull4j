package tech.robd.notnull.pmd;
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
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.reporting.RuleContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LocalNotNullUsageRule extends AbstractJavaRule {

    // Methods on NotNull that return @NonNull
    private static final Set<String> NOTNULL_ALLOWED_METHODS = Set.of(
            "verify", "orThrow", "orGet", "orThrowOptional", "orGetOptional",
            "orDefault", "orDefaultOptional", "orLog", "orLogOptional",
            "listOrEmpty", "setOrEmpty", "mapOrEmpty", "stringOrEmpty"
    );

    // Methods on Guard that return @NonNull — require/check boolean variants
    // are void and cannot appear as initialisers, so only the notNull variants
    // are listed here
    private static final Set<String> GUARD_ALLOWED_METHODS = Set.of(
            "requireNotNull", "checkNotNull"
    );

    private static final String NOTNULL_CLASS_FQN    = "tech.robd.notnull.NotNull";
    private static final String NOTNULL_CLASS_SIMPLE  = "NotNull";
    private static final String GUARD_CLASS_FQN       = "tech.robd.notnull4j.Guard";
    private static final String GUARD_CLASS_SIMPLE     = "Guard";
    private static final String LOCAL_NOT_NULL_FQN    = "tech.robd.notnull.LocalNotNull";
    private static final String LOCAL_NOT_NULL_SIMPLE  = "LocalNotNull";

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

        ASTVariableId variableId = node.getVarIds().first();
        if (variableId == null) return super.visit(node, data);

        String variableName = variableId.getName();
        localNotNullVariables.put(variableName, node);

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
     * Check if variable has @LocalNotNull annotation.
     */
    private boolean hasLocalNotNullAnnotation(ASTLocalVariableDeclaration node) {
        return node.descendants(ASTAnnotation.class)
                .any(a -> LOCAL_NOT_NULL_FQN.equals(a.getSimpleName())
                        || LOCAL_NOT_NULL_SIMPLE.equals(a.getSimpleName()));
    }

    /**
     * Check if the initializer expression is a whitelisted call from either
     * NotNull or Guard that is guaranteed to return @NonNull.
     */
    private boolean isAllowedInitializer(ASTExpression expr) {
        if (expr == null) return false;
        ASTMethodCall call = expr.descendants(ASTMethodCall.class).first();
        if (call == null) return false;

        ASTExpression qualifier = call.getQualifier();
        if (qualifier == null) return false;

        String qualifierText = String.valueOf(qualifier.getText());
        String methodName = call.getMethodName();

        if (isNotNullClass(qualifierText)) {
            return NOTNULL_ALLOWED_METHODS.contains(methodName);
        }

        if (isGuardClass(qualifierText)) {
            return GUARD_ALLOWED_METHODS.contains(methodName);
        }

        return false;
    }

    private boolean isNotNullClass(String qualifierText) {
        return NOTNULL_CLASS_FQN.equals(qualifierText)
                || NOTNULL_CLASS_SIMPLE.equals(qualifierText);
    }

    private boolean isGuardClass(String qualifierText) {
        return GUARD_CLASS_FQN.equals(qualifierText)
                || GUARD_CLASS_SIMPLE.equals(qualifierText);
    }

    /**
     * PMD 7 violation reporting.
     */
    private void reportViolation(Object data, JavaNode node, String varName) {
        RuleContext ctx = (RuleContext) data;
        ctx.addViolation(node, varName);
    }
}