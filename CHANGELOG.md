<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# ANTLR v4(New) Changelog

## [Unreleased]


##[2026.2.0]
### Changed
- Replace ANTLR `TreeViewer` / vendored Batik with custom Swing `ParseTreeGraphView` for Parse tree preview (PNG + lightweight SVG export)
- Clicking a Parse tree node highlights the matching span in the preview input (same as Hierarchy)

### Fixed
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
