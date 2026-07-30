package com.antlr.plugin.editor;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.antlr.plugin.ANTLRv4TokenTypes.getTokenElementType;
import static com.antlr.plugin.parser.ANTLRv4Lexer.*;


public class ANTLRv4BraceMatcher implements PairedBraceMatcher {

    @NotNull
    @Override
    public BracePair[] getPairs() {
        // #207/#253: TOKEN_ELEMENT_TYPES may be empty during early class-init / rainbow-brackets
        List<BracePair> pairs = new ArrayList<>(5);
        addPair(pairs, LPAREN, RPAREN, false);
        addPair(pairs, LBRACE, RBRACE, true);
        addPair(pairs, BEGIN_ACTION, END_ACTION, false);
        addPair(pairs, BEGIN_ARGUMENT, END_ARGUMENT, false);
        addPair(pairs, LT, GT, false);
        return pairs.toArray(BracePair[]::new);
    }

    private static void addPair(List<BracePair> pairs, int left, int right, boolean structural) {
        TokenIElementType l = getTokenElementType(left);
        TokenIElementType r = getTokenElementType(right);
        if (l != null && r != null) {
            pairs.add(new BracePair(l, r, structural));
        }
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
