<script setup>
/**
 * Smart Ground logo. Inline SVG using currentColor so it inherits the
 * surrounding text color. Defaults to the active palette's --sg-brand
 * (recolors per palette); set `color` on a parent to override, e.g.
 * white on the dark sidebar. Monoline chip mark with a solid center core.
 *
 * Props:
 *   variant: 'full' (icon + wordmark + tagline, monochrome) |
 *            'reversed' (full lockup, dark-surface two-tone: "SMART" and the
 *            mark outline stay currentColor, "GROUND" and the mark's core
 *            render in the palette accent) | 'mark' (icon only)
 *   size:    height in px (default 40)
 */
defineProps({
  variant: { type: String, default: 'full' },
  size: { type: [Number, String], default: 40 },
})
</script>

<template>
  <!-- ICON ONLY -->
  <svg
    v-if="variant === 'mark'"
    :height="size"
    viewBox="0 0 120 120"
    fill="none"
    role="img"
    aria-label="Smart Ground"
    class="sg-logo"
  >
    <g stroke="currentColor" stroke-width="5" stroke-linecap="round">
      <line x1="45" y1="18" x2="45" y2="34" /><line x1="60" y1="18" x2="60" y2="34" /><line x1="75" y1="18" x2="75" y2="34" />
      <line x1="45" y1="86" x2="45" y2="102" /><line x1="60" y1="86" x2="60" y2="102" /><line x1="75" y1="86" x2="75" y2="102" />
      <line x1="18" y1="45" x2="34" y2="45" /><line x1="18" y1="60" x2="34" y2="60" /><line x1="18" y1="75" x2="34" y2="75" />
      <line x1="86" y1="45" x2="102" y2="45" /><line x1="86" y1="60" x2="102" y2="60" /><line x1="86" y1="75" x2="102" y2="75" />
    </g>
    <rect x="34" y="34" width="52" height="52" rx="7" fill="none" stroke="currentColor" stroke-width="6" />
    <rect x="49" y="49" width="22" height="22" rx="4" fill="currentColor" />
  </svg>

  <!-- FULL LOCKUP: REVERSED (dark-surface two-tone) -->
  <svg
    v-else-if="variant === 'reversed'"
    :height="size"
    viewBox="0 0 520 140"
    fill="none"
    role="img"
    aria-label="Smart Ground - Grounded in Innovation"
    class="sg-logo"
  >
    <g transform="translate(10,10)">
      <g stroke="currentColor" stroke-width="5" stroke-linecap="round">
        <line x1="45" y1="18" x2="45" y2="34" /><line x1="60" y1="18" x2="60" y2="34" /><line x1="75" y1="18" x2="75" y2="34" />
        <line x1="45" y1="86" x2="45" y2="102" /><line x1="60" y1="86" x2="60" y2="102" /><line x1="75" y1="86" x2="75" y2="102" />
        <line x1="18" y1="45" x2="34" y2="45" /><line x1="18" y1="60" x2="34" y2="60" /><line x1="18" y1="75" x2="34" y2="75" />
        <line x1="86" y1="45" x2="102" y2="45" /><line x1="86" y1="60" x2="102" y2="60" /><line x1="86" y1="75" x2="102" y2="75" />
      </g>
      <rect x="34" y="34" width="52" height="52" rx="7" fill="none" stroke="currentColor" stroke-width="6" />
      <rect x="49" y="49" width="22" height="22" rx="4" fill="var(--sg-accent)" />
    </g>
    <text x="150" y="58" font-family="'DM Sans', system-ui, sans-serif" font-weight="700" font-size="40" letter-spacing="8" fill="currentColor">SMART</text>
    <text x="150" y="103" font-family="'DM Sans', system-ui, sans-serif" font-weight="700" font-size="40" letter-spacing="8" fill="var(--sg-accent)">GROUND</text>
    <text x="152" y="128" font-family="'DM Sans', system-ui, sans-serif" font-weight="400" font-size="15" letter-spacing="1.5" fill="currentColor" opacity="0.75">Grounded in Innovation</text>
  </svg>

  <!-- FULL LOCKUP -->
  <svg
    v-else
    :height="size"
    viewBox="0 0 520 140"
    fill="none"
    role="img"
    aria-label="Smart Ground - Grounded in Innovation"
    class="sg-logo"
  >
    <g transform="translate(10,10)">
      <g stroke="currentColor" stroke-width="5" stroke-linecap="round">
        <line x1="45" y1="18" x2="45" y2="34" /><line x1="60" y1="18" x2="60" y2="34" /><line x1="75" y1="18" x2="75" y2="34" />
        <line x1="45" y1="86" x2="45" y2="102" /><line x1="60" y1="86" x2="60" y2="102" /><line x1="75" y1="86" x2="75" y2="102" />
        <line x1="18" y1="45" x2="34" y2="45" /><line x1="18" y1="60" x2="34" y2="60" /><line x1="18" y1="75" x2="34" y2="75" />
        <line x1="86" y1="45" x2="102" y2="45" /><line x1="86" y1="60" x2="102" y2="60" /><line x1="86" y1="75" x2="102" y2="75" />
      </g>
      <rect x="34" y="34" width="52" height="52" rx="7" fill="none" stroke="currentColor" stroke-width="6" />
      <rect x="49" y="49" width="22" height="22" rx="4" fill="currentColor" />
    </g>
    <text x="150" y="58" font-family="'DM Sans', system-ui, sans-serif" font-weight="700" font-size="40" letter-spacing="8" fill="currentColor">SMART</text>
    <text x="150" y="103" font-family="'DM Sans', system-ui, sans-serif" font-weight="700" font-size="40" letter-spacing="8" fill="currentColor">GROUND</text>
    <text x="152" y="128" font-family="'DM Sans', system-ui, sans-serif" font-weight="400" font-size="15" letter-spacing="1.5" fill="currentColor" opacity="0.55">Grounded in Innovation</text>
  </svg>
</template>

<style scoped>
.sg-logo {
  display: block;
  width: auto;
  color: var(--sg-brand);
}
</style>
