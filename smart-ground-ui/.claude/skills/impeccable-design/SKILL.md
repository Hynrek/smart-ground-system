---
name: Impeccable Design Language
description: Use Impeccable's design language to improve AI-generated designs and catch anti-patterns. Trigger when designing UI with AI, asking for design improvements, critiques, or quality checks. Includes 23 design commands (polish, audit, critique, animate, etc.) and detection of 24 design anti-patterns. Essential for avoiding AI design slop and creating production-ready interfaces. Use whenever you're iterating on designs with AI assistance, need to audit designs, or want to improve design quality.
compatibility: None
---

# Impeccable Design Language Skill

Impeccable (https://github.com/pbakaus/impeccable) is a design language created by Philipp Bakaus specifically to help AI produce better designs. It provides a shared vocabulary between you and AI tools (like Claude) for discussing and improving design.

## When to Use This Skill

Use this skill when:
- **Designing with AI assistance** — Working with Claude to create UI designs for Smart Ground or other projects
- **Asking for design improvements** — Need specific language to request polish, animation, contrast adjustments
- **Auditing designs** — Checking if your design has common anti-patterns or quality issues
- **Avoiding AI slop** — Preventing bad design patterns that AI commonly generates
- **Iterating on layouts** — Refining spacing, hierarchy, and component arrangement
- **Quality assurance** — Before implementing designs, verify they meet design quality standards

## The 23 Impeccable Design Commands

These are your shared vocabulary with AI. Use them explicitly when asking for design changes:

### Structural Commands
- **`audit`** — Examine design and identify issues, anti-patterns, or violations
- **`critique`** — Provide detailed feedback and suggestions for improvement
- **`distill`** — Simplify and remove unnecessary elements; focus on essentials

### Visual Refinement
- **`polish`** — Improve overall visual quality and details
- **`bolder`** — Increase visual weight, contrast, or emphasis
- **`quieter`** — Reduce visual noise, tone down colors, soften hierarchy

### Animation & Motion
- **`animate`** — Add or improve animations and transitions
- **`ease`** — Adjust easing functions and motion timing
- **`breathe`** — Add subtle motion and life to static elements

### Typography & Text
- **`shrink`** — Reduce text size or make typography more compact
- **`expand`** — Increase whitespace and breathing room
- **`legible`** — Improve readability and text contrast

### Color & Theme
- **`brighten`** — Increase brightness and lightness values
- **`darken`** — Decrease brightness for better contrast or mood
- **`colorize`** — Apply color strategically (use sparingly, intentionally)

### Component & Layout
- **`align`** — Fix alignment issues and improve grid consistency
- **`stack`** — Reorganize components in vertical/horizontal flow
- **`contain`** — Add appropriate boundaries, borders, or containers

### Specialized
- **`emphasize`** — Highlight important elements (CTAs, status, data)
- **`balance`** — Achieve visual equilibrium and symmetry
- **`group`** — Organize related elements into logical sections

## The 24 Anti-Patterns to Avoid

Impeccable detects and flags 24 common design issues. Avoid these in your Smart Ground UI:

### AI Slop (Bad AI Defaults)
1. **Side-tab borders** — Avoid unnecessary borders around tab navigation
2. **Purple gradients** — Don't use trendy but clashing purple color schemes
3. **Bounce easing** — Avoid overly bouncy animations (looks amateur)
4. **Dark glows** — Don't overuse dark shadows/glows as visual effects
5. **Unnecessary animations** — Remove animations that don't serve purpose

### Spacing & Layout
6. **Cramped padding** — Ensure sufficient whitespace inside containers
7. **Inconsistent margins** — Keep spacing units consistent
8. **Line length too long** — Keep readable text lines (50-75 characters max)
9. **Vertical rhythm issues** — Maintain consistent baseline spacing

### Typography
10. **Skipped headings** — Don't jump heading levels (h1 → h3)
11. **Poor text contrast** — Ensure readable color pairs (WCAG AA minimum)
12. **Too many font sizes** — Limit to 4-5 sizes max
13. **Inconsistent font weights** — Use weights strategically

### Interactive Elements
14. **Small touch targets** — Minimum 44×44px for fingers
15. **Unclear affordances** — Buttons should look clickable
16. **Missing hover states** — Provide visual feedback for interactions
17. **Disabled states unclear** — Make disabled elements obvious
18. **Poor focus states** — Ensure keyboard navigation is visible

### Hierarchy & Contrast
19. **Weak visual hierarchy** — Important elements should stand out
20. **Low contrast text** — Text must be readable against background
21. **Color-only indicators** — Don't rely on color alone for status
22. **Missing focus indicators** — Highlight focused elements

### Responsiveness & Accessibility
23. **Non-responsive layouts** — Design for all screen sizes
24. **Missing ARIA labels** — Provide context for screen readers

## Impeccable Commands for Smart Ground

### Device Status Display
```
/polish the device status card to make the online/offline state more obvious
/bolder the device name and MAC address for better hierarchy
/audit the status indicator to ensure sufficient contrast
```

### Control Buttons
```
/emphasize the primary action button (Send Command)
/expand the touch target area for better mobile usability
/critique the disabled state of the command button
```

### Data Tables
```
/distill the device list to show only essential columns
/align the table headers and data cells properly
/breathe by adding better spacing between rows
```

### Forms & Settings
```
/audit the form inputs for accessibility
/balance the layout of configuration sections
/legible the error messages and validation text
```

## Quality Checklist for Smart Ground UI

Before finalizing any component, run through this audit:

**Spacing & Layout**
- [ ] Padding inside containers is adequate (8px minimum, prefer 16px+)
- [ ] Margins between sections are consistent
- [ ] No elements have touch targets smaller than 44×44px
- [ ] Content doesn't exceed 80 characters per line (where applicable)

**Visual Hierarchy**
- [ ] Primary actions are visually dominant
- [ ] Secondary actions are visually subordinate
- [ ] Status indicators (online/offline) are immediately obvious
- [ ] Important information stands out from background

**Typography**
- [ ] Maximum 4-5 font sizes used
- [ ] Text contrast meets WCAG AA (4.5:1 minimum)
- [ ] Headings follow proper hierarchy (no skipped levels)
- [ ] Font weights are used intentionally

**Interactivity**
- [ ] All buttons have hover, active, and disabled states
- [ ] Keyboard focus is visible on all interactive elements
- [ ] Loading states are shown for async operations
- [ ] Error states provide clear feedback

**Smart Ground Specifics**
- [ ] Device status is immediately recognizable
- [ ] Commands are easy to understand and execute
- [ ] Network state (connecting, connected, offline) is clear
- [ ] Error messages are specific and actionable

## How to Use With Claude

When working with Claude to design Smart Ground UI:

**Good:**
```
"Please audit this device card design and catch any anti-patterns"
"Can you polish the device list and make the status more obvious?"
"Emphasize the Send Command button and expand its touch target"
```

**Vague (avoid):**
```
"Make this better" ← Claude won't know what you want
"Improve the design" ← Too broad, unclear direction
```

## Anti-Pattern Examples for Smart Ground

### ❌ Bad: Weak Status Indicator
- Small icon, low contrast, poor placement
- User can't immediately see device state

### ✅ Good: Clear Status Indicator
- Prominent color (green=online, red=offline, yellow=connecting)
- Large enough to be obvious at a glance
- Positioned consistently across all devices

### ❌ Bad: Cramped Device Controls
- Buttons too close together, 32×32px touch targets
- Hard to tap on mobile devices

### ✅ Good: Spacious Controls
- 44×44px minimum buttons
- 16px spacing between button groups
- Clear visual grouping

## Integration with Emil Kowalski's Philosophy

While Emil Kowalski teaches **why** good design matters, Impeccable teaches **how to catch and fix** design issues with AI. Use both:

1. **Emil's principles** → Guide your design thinking
2. **Impeccable's commands** → Execute refinements with AI
3. **Impeccable's audits** → Verify quality before implementation

## Resources

- **GitHub**: https://github.com/pbakaus/impeccable
- **CLI Tool**: Run Impeccable's standalone detector to check designs
- **Commands Reference**: All 23 commands with examples
- **Anti-patterns Guide**: Detailed explanation of each of the 24 issues

## Next Steps

1. **Learn the 23 commands** — Practice using them in design conversations
2. **Memorize the anti-patterns** — Know what to look for and avoid
3. **Audit existing designs** — Check your current UI against the checklist
4. **Iterate with commands** — Use Impeccable language when asking Claude for improvements
5. **Before launch** — Run a final audit using the 24-point checklist

---

**Key Takeaway:** Impeccable gives you and AI a shared language for design. The clearer and more specific your requests, the better the results. Use these commands intentionally when working with Claude on Smart Ground's UI.
