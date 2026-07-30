package com.antlr.plugin.configdialogs;

import com.antlr.plugin.parsing.CaseChangingStrategy;
import com.antlr.plugin.parsing.RunANTLROnGrammarFile;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore.getGrammarProperties;
import static com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore.getOrCreateGrammarProperties;


/**
 * The UI that allows viewing/modifying grammar settings for a given grammar file.
 *
 * @see ANTLRv4ProjectSettings
 */
public class ConfigANTLRPerGrammar extends DialogWrapper {
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");

    private JPanel dialogContents;
    private JCheckBox generateParseTreeListenerCheckBox;
    private JCheckBox generateParseTreeVisitorCheckBox;
    private JTextField packageField;
    private TextFieldWithBrowseButton outputDirField;
    private TextFieldWithBrowseButton libDirField;
    private JTextField fileEncodingField;
    protected JCheckBox autoGenerateParsersCheckBox;
    protected JTextField languageField;
    private JComboBox<CaseChangingStrategy> caseTransformation;

    private Project project;

    private ConfigANTLRPerGrammar(final Project project) {
        super(project, false);
        this.project = project;
    }

    public static ConfigANTLRPerGrammar getDialogForm(final Project project, String qualFileName) {
        ConfigANTLRPerGrammar grammarFrom = new ConfigANTLRPerGrammar(project);
        grammarFrom.init();
        grammarFrom.initAntlrFields(project, qualFileName);
        return grammarFrom;
    }

    public static ConfigANTLRPerGrammar getProjectSettingsForm(final Project project, String qualFileName) {
        ConfigANTLRPerGrammar grammarFrom = new ConfigANTLRPerGrammar(project);
        grammarFrom.initAntlrFields(project, qualFileName);
        grammarFrom.generateParseTreeListenerCheckBox.setVisible(false);
        grammarFrom.generateParseTreeVisitorCheckBox.setVisible(false);
        grammarFrom.autoGenerateParsersCheckBox.setVisible(false);
        return grammarFrom;
    }

    private void initAntlrFields(Project project, String qualFileName) {
        this.project = project;
        FileChooserDescriptor dirChooser =
                FileChooserDescriptorFactory.createSingleFolderDescriptor();
        outputDirField.addBrowseFolderListener("Select Output Dir", null, project, dirChooser);
        outputDirField.setTextFieldPreferredWidth(50);

        dirChooser =
                FileChooserDescriptorFactory.createSingleFolderDescriptor();
        libDirField.addBrowseFolderListener("Select Lib Dir", null, project, dirChooser);
        libDirField.setTextFieldPreferredWidth(50);

        applyFieldHints();
        loadValues(project, qualFileName);
    }

    private void applyFieldHints() {
        setEmptyText(outputDirField.getTextField(), "$PROJECT_DIR$/gen  or  gen");
        setEmptyText(libDirField.getTextField(), "leave empty, or grammar folder");
        setEmptyText(packageField, "e.g. com.example  (optional)");
        setEmptyText(languageField, "Java");
        setEmptyText(fileEncodingField, "UTF-8");

        outputDirField.setToolTipText(
                "Where generated recognizers go. Default: gen (under the content root). "
                        + "Prefer $PROJECT_DIR$/gen for portable settings.");
        libDirField.setToolTipText(
                "Directory for import / tokenVocab lookups (-lib). Leave empty to use the .g4 file's folder. "
                        + "Must be an existing directory if set.");
        packageField.setToolTipText(
                "Optional -package / namespace for generated code. Use a legal identifier (e.g. com.test), or leave empty.");
        languageField.setToolTipText(
                "ANTLR target language (-Dlanguage). Use Java when the grammar's superClass is a Java base class.");
        fileEncodingField.setToolTipText("Grammar file encoding passed to the ANTLR tool (-encoding).");
    }

    private static void setEmptyText(JTextField field, String hint) {
        if (field instanceof JBTextField) {
            ((JBTextField) field).getEmptyText().setText(hint);
        } else {
            field.setToolTipText(hint);
        }
    }

    public void loadValues(Project project, String qualFileName) {
        this.project = project;
        ANTLRv4GrammarProperties grammarProperties = getGrammarProperties(project, qualFileName);
        if (grammarProperties != null) {
            autoGenerateParsersCheckBox.setSelected(grammarProperties.shouldAutoGenerateParser());
            outputDirField.setText(nullToEmpty(grammarProperties.getOutputDir()));
            libDirField.setText(nullToEmpty(grammarProperties.getLibDir()));
            String encoding = grammarProperties.getEncoding();
            if (encoding == null || encoding.isBlank()) {
                // #395: prefill from file/IDE charset when unset
                encoding = resolveDefaultEncoding(project, qualFileName);
            }
            fileEncodingField.setText(nullToEmpty(encoding));
            packageField.setText(nullToEmpty(grammarProperties.getPackage()));
            languageField.setText(nullToEmpty(grammarProperties.getLanguage()));
            caseTransformation.setSelectedItem(grammarProperties.getCaseChangingStrategy());
            generateParseTreeListenerCheckBox.setSelected(grammarProperties.shouldGenerateParseTreeListener());
            generateParseTreeVisitorCheckBox.setSelected(grammarProperties.shouldGenerateParseTreeVisitor());
        }
        applySensibleDefaultsIfBlank();
    }

    /**
     * When saved values are blank, show the same defaults generation already uses so the dialog
     * is not an empty trap for first-time / migrated settings.
     */
    private void applySensibleDefaultsIfBlank() {
        if (getOutputDirText().isBlank()) {
            outputDirField.setText(RunANTLROnGrammarFile.OUTPUT_DIR_NAME);
        }
        if (getLanguageText().isBlank()) {
            languageField.setText("Java");
        }
        if (getFileEncodingText().isBlank()) {
            fileEncodingField.setText("UTF-8");
        }
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    public void saveValues(Project project, String qualFileName) {
        // #420: persist portable $PROJECT_DIR$ keys/paths
        String key = ANTLRv4ToolGrammarPropertiesStore.collapseGrammarKey(project, qualFileName);
        ANTLRv4GrammarProperties grammarProperties = getOrCreateGrammarProperties(project, key);
        if (grammarProperties == null) {
            return;
        }
        grammarProperties.autoGen = autoGenerateParsersCheckBox.isSelected();
        grammarProperties.outputDir = ANTLRv4ToolGrammarPropertiesStore.collapsePath(project, getOutputDirText().trim());
        String lib = getLibDirText().trim();
        grammarProperties.libDir = lib.isEmpty()
                ? ""
                : ANTLRv4ToolGrammarPropertiesStore.collapsePath(project, lib);
        grammarProperties.encoding = getFileEncodingText().trim();
        grammarProperties.pkg = getPackageFieldText().trim();
        grammarProperties.language = getLanguageText().trim();
        grammarProperties.caseChangingStrategy = getCaseChangingStrategy();
        grammarProperties.generateListener = generateParseTreeListenerCheckBox.isSelected();
        grammarProperties.generateVisitor = generateParseTreeVisitorCheckBox.isSelected();
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        return validateSettings();
    }

    /**
     * Shared by the Configure dialog and project Settings page.
     *
     * @return first problem, or {@code null} if OK
     */
    @Nullable
    public ValidationInfo validateSettings() {
        String lib = getLibDirText().trim();
        if (!lib.isEmpty()) {
            File libDir = resolveMaybeRelativeDir(project, lib);
            if (libDir == null || !libDir.isDirectory()) {
                return new ValidationInfo(
                        "Imported grammars location must be an existing directory, or leave empty "
                                + "(uses the .g4 folder). Current value: " + lib,
                        libDirField);
            }
        }

        String pkg = getPackageFieldText().trim();
        if (!pkg.isEmpty() && !PACKAGE_PATTERN.matcher(pkg).matches()) {
            return new ValidationInfo(
                    "Package/namespace must be a legal identifier (e.g. com.test), or leave empty.",
                    packageField);
        }

        String language = getLanguageText().trim();
        if (!language.isEmpty() && !language.matches("[A-Za-z][A-Za-z0-9_]*")) {
            return new ValidationInfo(
                    "Language should be an ANTLR target name (e.g. Java, Python3, CSharp).",
                    languageField);
        }

        return null;
    }

    /** For {@link ANTLRv4ProjectSettings#apply()}. */
    public void applyValidated(Project project, String qualFileName) throws ConfigurationException {
        ValidationInfo info = validateSettings();
        if (info != null) {
            throw new ConfigurationException(info.message);
        }
        saveValues(project, qualFileName);
    }

    @Nullable
    static File resolveMaybeRelativeDir(@Nullable Project project, @NotNull String path) {
        if (path.isBlank()) {
            return null;
        }
        String expanded = path;
        if (project != null && !project.isDisposed()) {
            expanded = PathMacroManager.getInstance(project).expandPath(path);
        }
        File f = new File(expanded);
        if (!f.isAbsolute() && project != null && project.getBasePath() != null) {
            f = new File(project.getBasePath(), expanded);
        }
        return f;
    }

    private static String resolveDefaultEncoding(Project project, String qualFileName) {
        try {
            String expanded = PathMacroManager.getInstance(project).expandPath(qualFileName);
            VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(expanded);
            if (vf != null) {
                return vf.getCharset().name();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Charset.defaultCharset().name();
    }

    boolean isModified(ANTLRv4GrammarProperties originalProperties) {
        return !Objects.equals(originalProperties.getOutputDir(), getOutputDirText())
                || !Objects.equals(originalProperties.getLibDir(), getLibDirText())
                || !Objects.equals(originalProperties.getEncoding(), getFileEncodingText())
                || !Objects.equals(originalProperties.getPackage(), getPackageFieldText())
                || !Objects.equals(originalProperties.getLanguage(), getLanguageText())
                || !Objects.equals(originalProperties.caseChangingStrategy, getCaseChangingStrategy());
    }

    String getLanguageText() {
        return languageField.getText();
    }

    String getPackageFieldText() {
        return packageField.getText();
    }

    String getFileEncodingText() {
        return fileEncodingField.getText();
    }

    String getLibDirText() {
        return libDirField.getText();
    }

    String getOutputDirText() {
        return outputDirField.getText();
    }

    private CaseChangingStrategy getCaseChangingStrategy() {
        return (CaseChangingStrategy) caseTransformation.getSelectedItem();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return dialogContents;
    }

    @Override
    public String toString() {
        return "ConfigANTLRPerGrammar{" +
                " generateParseTreeListenerCheckBox=" + generateParseTreeListenerCheckBox +
                ", generateParseTreeVisitorCheckBox=" + generateParseTreeVisitorCheckBox +
                ", packageField=" + packageField +
                ", outputDirField=" + outputDirField +
                ", libDirField=" + libDirField +
                '}';
    }

    private void createUIComponents() {
        caseTransformation = new ComboBox<>(CaseChangingStrategy.values());
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        dialogContents = new JPanel();
        dialogContents.setLayout(new GridLayoutManager(10, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setText("Location of imported grammars");
        dialogContents.add(label1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label2 = new JLabel();
        label2.setText("Grammar file encoding; e.g., euc-jp");
        dialogContents.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fileEncodingField = new JBTextField();
        dialogContents.add(fileEncodingField, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        generateParseTreeVisitorCheckBox = new JCheckBox();
        generateParseTreeVisitorCheckBox.setText("generate parse tree visitor");
        dialogContents.add(generateParseTreeVisitorCheckBox, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        generateParseTreeListenerCheckBox = new JCheckBox();
        generateParseTreeListenerCheckBox.setSelected(true);
        generateParseTreeListenerCheckBox.setText("generate parse tree listener (default)");
        dialogContents.add(generateParseTreeListenerCheckBox, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Package/namespace for the generated code");
        dialogContents.add(label3, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        packageField = new JBTextField();
        dialogContents.add(packageField, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Output directory where all output is generated");
        dialogContents.add(label4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        outputDirField = new TextFieldWithBrowseButton();
        dialogContents.add(outputDirField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        libDirField = new TextFieldWithBrowseButton();
        dialogContents.add(libDirField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        autoGenerateParsersCheckBox = new JCheckBox();
        autoGenerateParsersCheckBox.setText("Auto-generate parsers upon save");
        dialogContents.add(autoGenerateParsersCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        // ANTLR 4.13.2+ dropped Python2; keep examples aligned with bundled Tool targets
        label5.setText("Language (e.g., Java, Python3, CSharp, ...)");
        dialogContents.add(label5, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        languageField = new JBTextField();
        dialogContents.add(languageField, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer1 = new Spacer();
        dialogContents.add(spacer1, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Case transformation in the Preview window");
        dialogContents.add(label6, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        dialogContents.add(caseTransformation, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return dialogContents;
    }
}
