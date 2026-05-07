# ESLint Configuration for Smart Ground UI

## Overview

This project is configured with **ESLint** and the **Vue 3 plugin** to automatically catch template errors, including unclosed tags like the one that caused the recent `App.vue` issue.

## What's Been Set Up

### 1. **ESLint Configuration** (`eslint.config.js`)
- Configured for Vue 3 SFCs (Single File Components)
- Catches template syntax errors, including unclosed tags
- Enforces code style consistency (indentation, quotes, semicolons)
- Configured with sensible defaults for Vue development

### 2. **Package Dependencies**
The following packages have been added to `package.json`:
- `eslint` - The linter itself
- `eslint-plugin-vue` - Vue 3 specific rules
- `@babel/eslint-parser` - Babel parser for JSX support
- `@eslint/js` - ESLint's recommended JavaScript rules

### 3. **NPM Scripts**
Two new scripts have been added to `package.json`:

```bash
# Fix linting issues automatically
npm run lint

# Check for linting issues without fixing
npm run lint:check
```

## Key Rules Enabled

### Vue Template Rules
- ✅ **Detects unclosed tags** - Prevents syntax errors
- ✅ **HTML closing bracket formatting** - Enforces consistent spacing
- ✅ **Component naming conventions** - Multi-word components
- ✅ **Attribute hyphenation** - Consistent prop naming
- ✅ **Max attributes per line** - Improves readability

### JavaScript Rules
- ✅ **Unused variable detection** - Catches dead code
- ✅ **Code style enforcement** - Consistent indentation (2 spaces), single quotes, semicolons
- ✅ **Linebreak style** - Enforces Unix line endings

## How to Use

### Before Committing
Run the linter to catch issues early:
```bash
npm run lint:check
```

### Auto-Fix Issues
The linter can automatically fix many issues:
```bash
npm run lint
```

### During Development
You can integrate ESLint with your editor:

**VS Code:**
1. Install the "ESLint" extension (by Microsoft)
2. The linter will run automatically on save
3. Red squiggles will appear for errors

**WebStorm/IntelliJ:**
1. ESLint is built-in, enable it in:
   - Settings → Languages & Frameworks → JavaScript → Linters → ESLint
2. Check "Automatic ESLint configuration"

## Preventing Similar Issues

### What This Catches
- ❌ `<style scoped>` without closing `</style>` tag
- ❌ `<template>` without closing `</template>` tag
- ❌ `<script>` without closing `</script>` tag
- ❌ Unclosed HTML elements inside templates

### What to Remember
1. Always ensure SFC blocks are properly closed:
   - `<script setup>` ... `</script>` ✓
   - `<template>` ... `</template>` ✓
   - `<style scoped>` ... `</style>` ✓

2. Run `npm run lint:check` before pushing code

3. Consider adding a pre-commit git hook (see below)

## Setting Up Git Hooks (Optional)

Create a pre-commit hook to automatically lint before commits:

**File: `.git/hooks/pre-commit`**
```bash
#!/bin/bash
npm run lint:check
if [ $? -ne 0 ]; then
  echo "ESLint check failed. Commit aborted."
  exit 1
fi
```

Make it executable:
```bash
chmod +x .git/hooks/pre-commit
```

## CI/CD Integration

If using CI/CD (GitHub Actions, GitLab CI, etc.), add a lint step:

```yaml
- name: Lint Vue files
  run: npm run lint:check
```

This ensures all code passes linting before being merged.

## Configuration Details

### File: `eslint.config.js`

The configuration covers:
- **JavaScript files** (.js, .jsx, .mjs, .cjs)
- **Vue SFC files** (.vue)
- **Ignores** dist/, node_modules/, .git/

### Rules Reference

| Rule | Level | Purpose |
|------|-------|---------|
| `vue/no-unused-vars` | warn | Detects unused template variables |
| `vue/html-indent` | warn | Enforces 2-space indentation |
| `no-unused-vars` | warn | Detects unused JS variables |
| `quotes` | warn | Enforces single quotes |
| `semi` | warn | Enforces semicolons |

All rules are set to `warn` level, so they don't fail the build but are highly visible in development.

## Troubleshooting

### ESLint not running
If `npm run lint:check` doesn't work, try:
```bash
npm install
npm run lint:check
```

### Many warnings on first run
This is normal! The configuration is stricter than your codebase might be. Either:
1. Run `npm run lint` to fix issues automatically
2. Adjust rule levels in `eslint.config.js` from `warn` to `off`

### Editor not showing ESLint errors
Make sure you have the ESLint extension installed and that your editor recognizes the `eslint.config.js` file.

---

**Last Updated:** April 2026  
**Project:** Smart Ground UI  
**ESLint Version:** 10.x  
**Vue Plugin Version:** Latest
