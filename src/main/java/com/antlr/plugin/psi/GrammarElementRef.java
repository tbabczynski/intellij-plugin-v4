package com.antlr.plugin.psi;

import com.antlr.plugin.ANTLRv4TokenTypes;
import com.antlr.plugin.parser.ANTLRv4Lexer;
import com.antlr.plugin.resolve.ImportResolver;
import com.antlr.plugin.resolve.TokenVocabResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A reference to a grammar element (parser rule, lexer rule or lexical mode).
 */
public class GrammarElementRef extends PsiReferenceBase<GrammarElementRefNode> {

    private final String ruleName;

    public GrammarElementRef(GrammarElementRefNode idNode, String ruleName) {
        super(idNode, new TextRange(0, ruleName.length()));
        this.ruleName = ruleName;
    }

    /**
     * Using for completion. Returns list of rules and tokens; the prefix
     * of current element is used as filter by IDEA later.
     */
    @NotNull
    @Override
    public Object[] getVariants() {
        GrammarSpecNode grammar = PsiTreeUtil.getContextOfType(myElement, GrammarSpecNode.class);
        if (grammar == null) {
            return new Object[0];
        }
        // Search whole grammar so refs inside modes / tokens / channels also complete
        Collection<? extends RuleSpecNode> ruleSpecNodes =
                PsiTreeUtil.findChildrenOfAnyType(grammar,
                        ParserRuleSpecNode.class, LexerRuleSpecNode.class, ModeSpecNode.class,
                        TokenSpecNode.class, ChannelSpecNode.class);
        return ruleSpecNodes.toArray();
    }

    /**
     * Called upon jump to def for this rule ref
     */
    @Nullable
    @Override
    public PsiElement resolve() {
        PsiFile tokenVocabFile = TokenVocabResolver.resolveTokenVocabFile(getElement());

        if (tokenVocabFile != null) {
            return tokenVocabFile;
        }

        PsiFile importedFile = ImportResolver.resolveImportedFile(getElement());
        if (importedFile != null) {
            return importedFile;
        }

        GrammarSpecNode grammar = PsiTreeUtil.getContextOfType(getElement(), GrammarSpecNode.class);
        if (grammar == null) {
            return null;
        }
        PsiElement specNode = MyPsiUtils.findSpecNode(grammar, ruleName);

        if (specNode != null) {
            return specNode;
        }

        // Look for a rule defined in an imported grammar
        PsiFile containingFile = getElement().getContainingFile();
        if (containingFile != null) {
            specNode = ImportResolver.resolveInImportedFiles(containingFile, ruleName);
            if (specNode != null) {
                return specNode;
            }
        }

        // Look for a lexer rule in the tokenVocab file if it exists
        if (getElement() instanceof LexerRuleRefNode) {
            return TokenVocabResolver.resolveInTokenVocab(getElement(), ruleName);
        }

        return null;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        Project project = getElement().getProject();
        IElementType tokenType = getElement() instanceof ParserRuleRefNode
                ? ANTLRv4TokenTypes.TOKEN_ELEMENT_TYPES.get(ANTLRv4Lexer.RULE_REF)
                : ANTLRv4TokenTypes.TOKEN_ELEMENT_TYPES.get(ANTLRv4Lexer.TOKEN_REF);
        PsiElement replacement = MyPsiUtils.createLeafFromText(project,
                myElement.getContext(),
                newElementName,
                tokenType);
        if (replacement != null) {
            return myElement.replace(replacement);
        }
        return myElement;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        return getElement();
    }
}
