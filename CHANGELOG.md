<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# ANTLR v4 Grammar Changelog

## [Unreleased]

### Fixed
- Drop forced `StoreUtil.saveSettings` flush for grammar properties; rely on `PersistentStateComponent.getState()` (same pattern as other plugins; fixes IJ 2025.3+ binary incompatibility)
- Resolve plugin version on demand instead of in `ApplicationInfo` class init (avoids PluginDetailsService during `<clinit>`)

## [2026.2.0]
### Changed
- Configure dialog language hint uses `Python3` (Python2 unsupported since ANTLR 4.13.2)
- Resolve plugin version/enablement via reflection (`PluginDescriptorUtil`) instead of internal `PluginManagerCore.getPlugin` (IJ 2026.2+)
- Rename plugin display name to **ANTLR v4 Grammar** (plugin id `com.my.antlr.tool` unchanged); tool windows `ANTLR Preview` / `ANTLR Console`
- Replace ANTLR `TreeViewer` / vendored Batik with custom Swing `ParseTreeGraphView` for Parse tree preview (PNG + lightweight SVG export)
- Clicking a Parse tree node highlights the matching span in the preview input (same as Hierarchy)
- Pure lexer grammars can preview a synthetic Tokens tree (tokenize-only path)
- Scroll from Source also selects/scrolls the Parse tree canvas; toggling Build Tree/Hierarchy off clears that view

### Fixed
- Per-grammar Configure settings: exact `fileName` match updates in place (no duplicate rows); load keeps the first row if XML was dirty
- Preview: `SafeLexerInterpreter` tolerates empty-stack `popMode` instead of crashing IDE (#163/#164/#181–#184/#209–#211); last-resort catch around preview parse
- Annotator/Tool failures log as warn (not `LOG.error`) so bad grammars stop filing false plugin crash reports (#247); harden load/parse background tasks; keep `antlr-runtime` explicit (#203); tab-switch never `saveDocument` (#248/#264)
- Fork crash reports: soft-fail ANTLR `process`/`importVocab` (#177/#204/#215/#247); clamp preview highlight offsets (#214); Registry-safe parse-tree export (#251); disposed-safe grammar props (#137); null-safe brace matcher / TokenTypes init (#207/#253); clearer coexistence with the official ANTLR plugin (#223)
- Share Configure ANTLR settings between companion `XParser.g4` / `XLexer.g4` (upstream #548)
- Guard `isGrammarStale` when grammar has no parent VFS path (upstream #722)
- Hierarchy empty preview uses a placeholder root instead of null model (upstream #494)
- Null-safe Inline Rule reparse; error-report View action uses `ActionUpdateThread.BGT`
- Defer grammar load after editor restore and reload empty-but-nonempty-on-disk `.g4` documents (#265)
- Stop subclassing `WriteCommandAction` (final in IJ 2026.2; upstream #741)
- Option names (`tokenVocab`, `language`, …) are not rule references (upstream #653)
- Expand Hierarchy ancestors when selecting from preview input click (upstream #726)
- Persist grammar config keys/paths with `$PROJECT_DIR$` macros; migrate from `misc.xml` (upstream #420/#568)
- Autogen-on-save: include project-base grammars and associated parsers after lexer save (upstream #697)
- Preview banner when grammar has actions/predicates (upstream #523/#732)
- Gate annotator preview refresh on document modification stamp (upstream #702)
- Profiler opens the owning grammar file for imported decisions (upstream #372)
- Suppress spurious preview `extraneous input '<EOF>'` errors (upstream #324)
- Encoding falls back to file/platform charset when unset (upstream #395)
- Test Rule forces interactive Input mode (upstream #644)
- Load companion/`tokenVocab` lexer via configured `-lib` and relative paths (same as PSI resolve)
- Completion variants include rules from `import` / `tokenVocab` grammars
- Use SVG icons that ship with the plugin (PNG paths were missing)
- Guard Profiler decision index and division-by-zero in stats; Ctrl/Alt-click preview before parse completes
- Guard Lookahead ambig dialog index; null-safe Inline/Uniquify rule text extraction
- Resolve `tokenVocab` / `import` via configured `-lib` and relative paths; treat import/tokenVocab/companion lexer as autogen stale deps
- Wire Highlight Source toggle; dispose-safe Preview `clearTabs`; null-safe Hierarchy selection / folding brace nodes
- Write lexer `.tokens` to the configured `-o` dir (set Tool `-o` / `haveOutputDir`); null-safe `grammarFileSavedEvent(project)`
- Compile Java sources as UTF-8; stabilize GrammarElementRef / Issue540 / CreateRuleFix tests
- Fix Profiler token-index -1 crash (#260); Ctrl-hover NPE when `g` is null (#261); parse-tree ruleIndex OOB (#262)
- Avoid saving documents on editor tab switch (EDT freeze #259); bound grammar parse await; harden profiler/input highlighters
- Fix listener lifecycle leaks (MessageBus / project close / multi-project VFS filtering)
- Fix external annotator severity fall-through and reduce EDT/read-lock pressure
- Fix bare `-o`/`-lib` ANTLR CLI args and restore autogen-on-save for parser grammars
- Fix preview refresh race, overlapping parse cancellation, and file-close cleanup
- Fix completion/resolve/rename for modes, quoted tokenVocab, and parser rule rename
- Align live template contexts (`ANTLR_OUTSIDE-Tool`) with `user.xml`; keep `-Tool` suffix to avoid plugin clashes
- Fix `@header` package detection regex and autogen staleness path (use grammar dir, not libDir)
- Fix Test Rule tool-window race; remove BAD_LEXER_GRAMMAR wildcard association
- Fix Structure View editor autoscroll / null-safe mode children; brace matcher uses LBRACE/RBRACE
- Fix Inline/Uniquify TOKEN_REF lookup in parser rules; import Alias=Foo navigation
- Always force Generate Parser action; lexer extract defaults to `NEW_RULE`
- Align CI Gradle properties with the Kotlin build script; align Gradle wrapper version
- Move tests to `com.antlr.plugin` package

## [2025.2.2]
### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/13?closed=1)
- Adapted to 2025.3


## [2025.2.1]
### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/8?closed=1)
- Adapted to 2025.2



## [2024.2.0]
### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/8?closed=1)
- Adapted to 2024.3


## [2024.1.9]
### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/7?closed=1)
- Adapted to 2024.2

## [2024.1.8]
### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/6?closed=1)
-


## [2024.1.7]
### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/5?closed=1)
-



## [2024.1.6]
### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/4?closed=1)
-

## [2024.1.5]

### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/2?closed=1)
-




## [2024.1.4]

### Fixed
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/2?closed=1)
-



## [2024.1.3]

### Fixed
- Failed to get the plug-in version number
- [Fix some bugs](https://github.com/mbtsp/intellij-plugin-v4/milestone/1?closed=1)
- 



## [2024.1.2]

### Fixed
- Unable to file a bug
-  RunANTLROnGrammarFile.getContentRoot   Read access is allowed from inside read-action
- The project is opened, the build script is executed for the first time, and there is no log output in the console window

## [2024.1.1]

### Fixed
- Cannot invoke "java.util.List.get(int)" because "this.tokenElementTypes" is null
- NoClassDefFoundError: org/apache/batik/svggen/SVGGraphics2DIOException
- got 'java.lang.ExceptionInInitializerError' when i enabled the antlr plugin and restart IDEA
- Cannot invoke "com.intellij.openapi.editor.Editor.getScrollingModel()" because "inputEditor" is null
- 
### Changed
- 
