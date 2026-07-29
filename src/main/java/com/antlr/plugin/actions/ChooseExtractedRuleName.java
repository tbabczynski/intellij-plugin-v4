package com.antlr.plugin.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ChooseExtractedRuleName extends DialogWrapper {
    private JBTextField nameField;
    private final String defaultName;
    /** Set only when the user confirms OK; null if cancelled. */
    public String ruleName;

    protected ChooseExtractedRuleName(@Nullable Project project) {
        this(project, "newRule");
    }

    protected ChooseExtractedRuleName(@Nullable Project project, @NotNull String defaultName) {
        super(project, true);
        this.defaultName = defaultName;
        init();
    }

    @Override
    protected void doOKAction() {
        ruleName = nameField.getText();
        super.doOKAction();
    }

    @Override
    protected JComponent createCenterPanel() {
        nameField = new JBTextField(defaultName);
        // getSize() is 0 before layout; use preferred height so the dialog is usable
        int height = Math.max(nameField.getPreferredSize().height, 24);
        nameField.setPreferredSize(new Dimension(250, height));
        setTitle("Name the extracted rule");
        nameField.selectAll();
        return nameField;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return nameField;
    }
}
