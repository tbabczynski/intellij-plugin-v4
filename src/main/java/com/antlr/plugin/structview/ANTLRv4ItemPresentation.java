package com.antlr.plugin.structview;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.antlr.plugin.ANTLRv4FileRoot;
import com.antlr.plugin.ANTLRv4TokenTypes;
import com.antlr.plugin.parser.ANTLRv4Parser;
import com.antlr.plugin.psi.GrammarElementRefNode;
import com.antlr.plugin.psi.GrammarSpecNode;
import com.antlr.plugin.psi.ModeSpecNode;
import com.antlr.plugin.psi.MyPsiUtils;
import com.antlr.plugin.psi.RuleSpecNode;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ANTLRv4ItemPresentation implements ItemPresentation {
    protected final PsiElement element;

    protected ANTLRv4ItemPresentation(PsiElement element) {
        this.element = element;
    }

    @Nullable
    public String getLocationString() {
        return null;
    }

    @Override
    public String getPresentableText() {
        if (element instanceof ANTLRv4FileRoot) {
            GrammarSpecNode node = PsiTreeUtil.findChildOfType(element, GrammarSpecNode.class);
            if(node!=null){
                PsiElement id = MyPsiUtils.findChildOfType(node, ANTLRv4TokenTypes.RULE_ELEMENT_TYPES.get(ANTLRv4Parser.RULE_identifier));
                if (id != null) {
                    return id.getText();
                }
            }
            return "<n/a>";
        }
        if (element instanceof ModeSpecNode mode) {
            GrammarElementRefNode modeId = mode.getNameIdentifier();
            if (modeId != null) {
                return modeId.getName();
            }
            return "<n/a>";
        }
        if (element instanceof RuleSpecNode ruleSpec) {
            return ruleSpec.getName();
        }
        ASTNode node = element.getNode();
        return node != null ? node.getText() : "<n/a>";
    }

    @Nullable
    public Icon getIcon(boolean open) {
        return element.getIcon(0);
    }
}
