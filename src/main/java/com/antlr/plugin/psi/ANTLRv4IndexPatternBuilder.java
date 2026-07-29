package com.antlr.plugin.psi;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.antlr.plugin.ANTLRv4FileRoot;
import com.antlr.plugin.adaptors.ANTLRv4LexerAdaptor;
import com.antlr.plugin.parser.ANTLRv4Lexer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.antlr.plugin.ANTLRv4TokenTypes.getTokenElementType;


public class ANTLRv4IndexPatternBuilder implements IndexPatternBuilder {

    @Nullable
    @Override
    public Lexer getIndexingLexer(@NotNull PsiFile file) {
        if (file instanceof ANTLRv4FileRoot) {
            ANTLRv4Lexer lexer = new ANTLRv4Lexer(null);
            return new ANTLRv4LexerAdaptor(lexer);
        }
        return null;
    }

    @Nullable
    @Override
    public TokenSet getCommentTokenSet(@NotNull PsiFile file) {
        if (file instanceof ANTLRv4FileRoot) {
            return TokenSet.create(
                    getTokenElementType(ANTLRv4Lexer.LINE_COMMENT),
                    getTokenElementType(ANTLRv4Lexer.BLOCK_COMMENT)
            );
        }
        return null;
    }

    @Override
    public int getCommentStartDelta(IElementType tokenType) {
        if (tokenType == getTokenElementType(ANTLRv4Lexer.LINE_COMMENT)) {
            return 2;
        }
        if (tokenType == getTokenElementType(ANTLRv4Lexer.BLOCK_COMMENT)) {
            return 2;
        }
        return 0;
    }

    @Override
    public int getCommentEndDelta(IElementType tokenType) {
        if (tokenType == getTokenElementType(ANTLRv4Lexer.BLOCK_COMMENT)) {
            return 2;
        }
        return 0;
    }
}
