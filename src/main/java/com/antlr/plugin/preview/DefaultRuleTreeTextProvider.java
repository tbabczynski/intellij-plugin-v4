package com.antlr.plugin.preview;

import org.antlr.v4.gui.TreeTextProvider;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.runtime.tree.Trees;

import java.util.Collections;
import java.util.List;

/** Local replacement for {@code TreeViewer.DefaultTreeTextProvider}. */
public class DefaultRuleTreeTextProvider implements TreeTextProvider {
    private final List<String> ruleNames;

    public DefaultRuleTreeTextProvider(List<String> ruleNames) {
        this.ruleNames = ruleNames != null ? ruleNames : Collections.emptyList();
    }

    @Override
    public String getText(Tree node) {
        return Trees.getNodeText(node, ruleNames);
    }
}
