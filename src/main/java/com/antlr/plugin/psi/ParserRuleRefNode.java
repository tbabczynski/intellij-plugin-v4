package com.antlr.plugin.psi;

import com.antlr.plugin.parser.ANTLRv4Parser;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;

import static com.antlr.plugin.ANTLRv4TokenTypes.RULE_ELEMENT_TYPES;

public class ParserRuleRefNode extends GrammarElementRefNode {
    public ParserRuleRefNode(IElementType type, CharSequence text) {
        super(type, text);
    }

    @Override
    public PsiReference getReference() {
        if (isDeclaration() || isOptionName()) {
            return null;
        }
        return new GrammarElementRef(this, getText());
    }

    private boolean isDeclaration() {
        return getParent() instanceof ParserRuleSpecNode;
    }

    /**
     * Option names ({@code language}, {@code tokenVocab}, …) are {@code identifier}/{@code RULE_REF}
     * but must not resolve as rule references (upstream #653). Values under {@code optionValue}
     * (e.g. {@code tokenVocab=MyLexer}) still get references.
     */
    private boolean isOptionName() {
        PsiElement el = this;
        while (el != null) {
            ASTNode node = el.getNode();
            if (node != null) {
                IElementType type = node.getElementType();
                if (type == RULE_ELEMENT_TYPES.get(ANTLRv4Parser.RULE_optionValue)) {
                    return false;
                }
                if (type == RULE_ELEMENT_TYPES.get(ANTLRv4Parser.RULE_option)
                        || type == RULE_ELEMENT_TYPES.get(ANTLRv4Parser.RULE_elementOption)) {
                    return true;
                }
            }
            el = el.getParent();
        }
        return false;
    }
}
