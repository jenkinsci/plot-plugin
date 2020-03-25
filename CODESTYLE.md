# Code style

[Checkstyle](http://checkstyle.sourceforge.net/) plugin is used to validate code style.
Check [checkstyle/checkstyle.xml](https://github.com/jenkinsci/plot-plugin/blob/master/checkstyle/checkstyle.xml) for more details.

## Few important notes about the style:
**Indentation**

- Use spaces (tabs banned)
- 1 indent = 4 spaces

**Field naming convention**

- "hungarian" notation is banned
- ALL_CAPS for static final fields
- camelCase for naming (`_` tolerable in test names)

**Imports**

- `*` imports are banned

**General**

- line length = `100`
- method declaration parameters = `Align when multiline`
- use interfaces only as types (no interface for constants)
