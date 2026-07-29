package com.antlr.plugin.structview;

import com.antlr.plugin.ANTLRv4FileRoot;
import com.antlr.plugin.psi.LexerRuleRefNode;
import com.antlr.plugin.psi.LexerRuleSpecNode;
import com.antlr.plugin.psi.ModeSpecNode;
import com.antlr.plugin.psi.ParserRuleRefNode;
import com.antlr.plugin.psi.ParserRuleSpecNode;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.SorterUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class ANTLRv4StructureViewModel
        extends TextEditorBasedStructureViewModel
        implements StructureViewModel.ElementInfoProvider {
    private static final Sorter PARSER_LEXER_RULE_SORTER = new Sorter() {
        public Comparator<?> getComparator() {
            return (o1, o2) -> {
                String s1 = SorterUtil.getStringPresentation(o1);
                String s2 = SorterUtil.getStringPresentation(o2);
                if (s1.isEmpty() || s2.isEmpty()) {
                    return s1.compareTo(s2);
                }
                // flip case of char 0 so it puts parser rules first
                if (Character.isLowerCase(s1.charAt(0))) {
                    s1 = Character.toUpperCase(s1.charAt(0)) + s1.substring(1);
                } else {
                    s1 = Character.toLowerCase(s1.charAt(0)) + s1.substring(1);
                }
                if (Character.isLowerCase(s2.charAt(0))) {
                    s2 = Character.toUpperCase(s2.charAt(0)) + s2.substring(1);
                } else {
                    s2 = Character.toLowerCase(s2.charAt(0)) + s2.substring(1);
                }
                return s1.compareTo(s2);
            };
        }

        public boolean isVisible() {
            return true;
        }

        @NotNull
        public ActionPresentation getPresentation() {
            String name = "Sort by rule type";
            return new ActionPresentationData(name, name, AllIcons.ObjectBrowser.SortByType);
        }

        @NotNull
        public String getName() {
            return "PARSER_LEXER_RULE_SORTER";
        }
    };

    private final ANTLRv4FileRoot rootElement;

    public ANTLRv4StructureViewModel(@NotNull ANTLRv4FileRoot rootElement, @Nullable Editor editor) {
        super(editor, rootElement);
        this.rootElement = rootElement;
    }

    @NotNull
    @Override
    public StructureViewTreeElement getRoot() {
        return new ANTLRv4StructureViewElement(rootElement);
    }

    @NotNull
    public Sorter[] getSorters() {
        return new Sorter[]{PARSER_LEXER_RULE_SORTER, Sorter.ALPHA_SORTER};
    }

    @Override
    protected PsiFile getPsiFile() {
        return rootElement;
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        Object value = element.getValue();
        return value instanceof ANTLRv4FileRoot || value instanceof ModeSpecNode;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        Object value = element.getValue();
        return value instanceof ParserRuleSpecNode
                || value instanceof LexerRuleSpecNode
                || value instanceof ParserRuleRefNode
                || value instanceof LexerRuleRefNode;
    }

    @NotNull
    @Override
    protected Class<?>[] getSuitableClasses() {
        return new Class[]{
                ANTLRv4FileRoot.class,
                ModeSpecNode.class,
                LexerRuleSpecNode.class,
                ParserRuleSpecNode.class,
                LexerRuleRefNode.class,
                ParserRuleRefNode.class
        };
    }
}
