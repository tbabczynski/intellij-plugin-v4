package com.antlr.plugin.resolve;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.antlr.plugin.parser.ANTLRv4Parser;
import com.antlr.plugin.psi.GrammarElementRefNode;
import com.antlr.plugin.psi.GrammarSpecNode;
import com.antlr.plugin.psi.MyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.antlr.plugin.ANTLRv4TokenTypes.RULE_ELEMENT_TYPES;
import static com.antlr.plugin.resolve.TokenVocabResolver.findRelativeFile;


public class ImportResolver {

    public static PsiFile resolveImportedFile(GrammarElementRefNode reference) {
        PsiElement importStatement = PsiTreeUtil.findFirstParent(reference, ImportResolver::isImportStatement);

        if (importStatement != null) {
            // Always resolve to the RHS grammar name for import Alias=Foo
            String fileName = resolveImportFileName(importStatement);
            if (fileName != null) {
                return findRelativeFile(fileName, reference.getContainingFile());
            }
        }

        return null;
    }

    /**
     * Resolve the imported grammar file name from a {@code delegateGrammar} node.
     * Supports both {@code import Foo;} and {@code import Alias=Foo;}.
     */
    @Nullable
    static String resolveImportFileName(PsiElement delegateGrammar) {
        // Prefer the last identifier / rule-ref leaf (RHS of Alias=Name)
        PsiElement deepest = PsiTreeUtil.getDeepestLast(delegateGrammar);
        if (deepest != null && deepest != delegateGrammar) {
            return MyPsiUtils.stripQuotes(deepest.getText());
        }
        return MyPsiUtils.stripQuotes(delegateGrammar.getText());
    }

    private static boolean isImportStatement(PsiElement el) {
        ASTNode node = el.getNode();
        return node != null && node.getElementType() == RULE_ELEMENT_TYPES.get(ANTLRv4Parser.RULE_delegateGrammar);
    }

    public static PsiElement resolveInImportedFiles(@NotNull PsiFile grammarFile, @NotNull String ruleName) {
        return resolveInImportedFiles(grammarFile, ruleName, new ArrayList<>());
    }

    /** Directly imported grammar files (one level), for completion variants. */
    @NotNull
    public static List<PsiFile> collectImportedFiles(@NotNull PsiFile grammarFile) {
        DelegateGrammarsVisitor visitor = new DelegateGrammarsVisitor();
        grammarFile.accept(visitor);
        return new ArrayList<>(visitor.importedGrammars);
    }

    private static PsiElement resolveInImportedFiles(PsiFile grammarFile, String ruleName, List<PsiFile> visitedFiles) {
        for (PsiFile importedGrammar : collectImportedFiles(grammarFile)) {
            if (visitedFiles.contains(importedGrammar)) {
                continue;
            }
            visitedFiles.add(importedGrammar);

            GrammarSpecNode grammar = PsiTreeUtil.getChildOfType(importedGrammar, GrammarSpecNode.class);
            PsiElement specNode = MyPsiUtils.findSpecNode(grammar, ruleName);

            if (specNode != null) {
                return specNode;
            }

            // maybe the imported grammar also imports other grammars itself?
            specNode = resolveInImportedFiles(importedGrammar, ruleName, visitedFiles);
            if (specNode != null) {
                return specNode;
            }
        }

        return null;
    }

    private static class DelegateGrammarsVisitor extends PsiRecursiveElementVisitor {

        List<PsiFile> importedGrammars = new ArrayList<>();

        @Override
        public void visitElement(@NotNull PsiElement element) {
            if (isImportStatement(element)) {
                String fileName = resolveImportFileName(element);
                if (fileName != null) {
                    PsiFile importedGrammar = findRelativeFile(fileName, element.getContainingFile());

                    if (importedGrammar != null) {
                        importedGrammars.add(importedGrammar);
                    }
                }
            }
            super.visitElement(element);
        }
    }
}
