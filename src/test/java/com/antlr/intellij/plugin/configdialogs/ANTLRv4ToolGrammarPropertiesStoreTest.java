package com.antlr.intellij.plugin.configdialogs;

import com.antlr.plugin.configdialogs.ANTLRv4GrammarProperties;
import com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore;
import org.junit.Assert;
import org.junit.Test;

public class ANTLRv4ToolGrammarPropertiesStoreTest {

    private static final String MY_GRAMMAR_PATH = "/home/grammars/test/MyGrammar.java";

    @Test
    public void shouldReturnPropertiesForExactFile() {
        // given:
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();
        propertiesStore.add(createGrammarProperties("/home/grammars/test/NotMyGrammar.java"));
        ANTLRv4GrammarProperties myGrammarProperties = createGrammarProperties(MY_GRAMMAR_PATH);
        propertiesStore.add(myGrammarProperties);

        // when:
        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        // then:
        Assert.assertSame(grammarProperties, myGrammarProperties);
    }

    @Test
    public void shouldReturnDefaultPropertiesIfNoneDefined() {
        // given:
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();

        // when:
        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        // then:
        Assert.assertSame(grammarProperties, ANTLRv4ToolGrammarPropertiesStore.DEFAULT_GRAMMAR_PROPERTIES);
    }

    @Test
    public void shouldMatchPropertiesByWildcard() {
        // given:
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();
        propertiesStore.add(createGrammarProperties("*/main/*.java"));
        ANTLRv4GrammarProperties testGrammarProperties = createGrammarProperties("/home/*/test/*.java");
        propertiesStore.add(testGrammarProperties);

        // when:
        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        // then:
        Assert.assertSame(grammarProperties, testGrammarProperties);
    }

    @Test
    public void shouldPreferExactMatchOverWildcard() {
        // given:
        ANTLRv4ToolGrammarPropertiesStore propertiesStore = new ANTLRv4ToolGrammarPropertiesStore();
        propertiesStore.add(createGrammarProperties("/home/grammars/test/NotMyGrammar.java"));
        propertiesStore.add(createGrammarProperties("/home/*/test/*.java"));
        ANTLRv4GrammarProperties myGrammarProperties = createGrammarProperties(MY_GRAMMAR_PATH);
        propertiesStore.add(myGrammarProperties);

        // when:
        ANTLRv4GrammarProperties grammarProperties = propertiesStore.getGrammarProperties(MY_GRAMMAR_PATH);

        // then:
        Assert.assertSame(grammarProperties, myGrammarProperties);
    }

    private ANTLRv4GrammarProperties createGrammarProperties(String fileName) {
        ANTLRv4GrammarProperties antlRv4GrammarProperties = new ANTLRv4GrammarProperties();
        antlRv4GrammarProperties.fileName = fileName;
        return antlRv4GrammarProperties;
    }
}