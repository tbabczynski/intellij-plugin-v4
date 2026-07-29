package com.antlr.plugin.structview;

import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.antlr.plugin.ANTLRv4FileRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class ANTLRv4StructureViewFactory implements PsiStructureViewFactory {
    /** fake a blank Treeview with a warning */
    public static class DummyViewTreeElement extends PsiTreeElementBase<PsiElement> {
        public DummyViewTreeElement(PsiElement psiElement) {
            super(psiElement);
        }

        @NotNull
        @Override
        public Collection<StructureViewTreeElement> getChildrenBase() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public String getPresentableText() {
            return "Sorry .g not supported (use .g4)";
        }
    }

    @Nullable
    @Override
    public StructureViewBuilder getStructureViewBuilder(final @NotNull PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @NotNull
            @Override
            public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                VirtualFile grammarFile = psiFile.getVirtualFile();
                if (grammarFile == null || !grammarFile.getName().endsWith(".g4")) {
                    return new StructureViewModelBase(psiFile, new DummyViewTreeElement(psiFile));
                }
                return new ANTLRv4StructureViewModel((ANTLRv4FileRoot) psiFile, editor);
            }
        };
    }
}
