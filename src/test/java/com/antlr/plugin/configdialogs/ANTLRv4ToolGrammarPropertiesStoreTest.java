package com.antlr.plugin.configdialogs;

import org.junit.Assert;
import org.junit.Test;

public class ANTLRv4ToolGrammarPropertiesStoreTest {

    private static final String MY_GRAMMAR_PATH = "/home/grammars/test/MyGrammar.java";

    @Test
    public void shouldReturnPropertiesForExactFile() {
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();
        propertiesStore.add(createGrammarProperties("/home/grammars/test/NotMyGrammar.java"));
        ANTLRv4GrammarProperties myGrammarProperties = createGrammarProperties(MY_GRAMMAR_PATH);
        propertiesStore.add(myGrammarProperties);

        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        Assert.assertSame(grammarProperties, myGrammarProperties);
    }

    @Test
    public void shouldReturnDefaultPropertiesIfNoneDefined() {
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();

        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        Assert.assertSame(grammarProperties, ANTLRv4ToolGrammarPropertiesStore.DEFAULT_GRAMMAR_PROPERTIES);
    }

    @Test
    public void shouldMatchPropertiesByWildcard() {
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();
        propertiesStore.add(createGrammarProperties("*/main/*.java"));
        ANTLRv4GrammarProperties testGrammarProperties = createGrammarProperties("/home/*/test/*.java");
        propertiesStore.add(testGrammarProperties);

        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        Assert.assertSame(grammarProperties, testGrammarProperties);
    }

    @Test
    public void shouldPreferExactMatchOverWildcard() {
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();
        propertiesStore.add(createGrammarProperties("/home/grammars/test/NotMyGrammar.java"));
        propertiesStore.add(createGrammarProperties("/home/*/test/*.java"));
        ANTLRv4GrammarProperties myGrammarProperties = createGrammarProperties(MY_GRAMMAR_PATH);
        propertiesStore.add(myGrammarProperties);

        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        Assert.assertSame(grammarProperties, myGrammarProperties);
    }

    @Test
    public void shouldReturnLastExactDuplicate() {
        ANTLRv4ToolGrammarPropertiesStore store = new ANTLRv4ToolGrammarPropertiesStore();
        ANTLRv4GrammarProperties first = createGrammarProperties(MY_GRAMMAR_PATH);
        first.language = "Java";
        ANTLRv4GrammarProperties second = createGrammarProperties(MY_GRAMMAR_PATH);
        second.language = "Python3";
        store.add(first);
        store.add(second);

        Assert.assertSame(second, store.getGrammarProperties(MY_GRAMMAR_PATH));
        Assert.assertEquals("Python3", store.getGrammarProperties(MY_GRAMMAR_PATH).getLanguage());
    }

    @Test
    public void shouldDedupeKeepingLast() {
        ANTLRv4ToolGrammarPropertiesStore store = new ANTLRv4ToolGrammarPropertiesStore();
        ANTLRv4GrammarProperties first = createGrammarProperties(MY_GRAMMAR_PATH);
        first.language = "Java";
        ANTLRv4GrammarProperties second = createGrammarProperties(MY_GRAMMAR_PATH);
        second.language = "Python3";
        store.add(first);
        store.add(second);
        store.add(createGrammarProperties("/other/File.g4"));

        Assert.assertTrue(store.dedupeAll());
        Assert.assertFalse(store.dedupeAll());

        Assert.assertEquals(2, store.size());
        Assert.assertEquals("Python3", store.getGrammarProperties(MY_GRAMMAR_PATH).getLanguage());
    }

    @Test
    public void shouldTreatBackslashAndSlashKeysAsSame() {
        ANTLRv4ToolGrammarPropertiesStore store = new ANTLRv4ToolGrammarPropertiesStore();
        ANTLRv4GrammarProperties row = createGrammarProperties("$PROJECT_DIR$\\src\\A.g4");
        row.language = "Java";
        store.add(row);

        Assert.assertEquals("Java", store.getGrammarProperties("$PROJECT_DIR$/src/A.g4").getLanguage());
    }

    private ANTLRv4GrammarProperties createGrammarProperties(String fileName) {
        ANTLRv4GrammarProperties antlRv4GrammarProperties = new ANTLRv4GrammarProperties();
        antlRv4GrammarProperties.fileName = fileName;
        return antlRv4GrammarProperties;
    }
}
