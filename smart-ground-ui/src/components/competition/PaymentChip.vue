<!-- src/components/competition/PaymentChip.vue -->
<template>
  <button
    class="payment-chip"
    :class="paid ? 'chip--paid' : 'chip--unpaid'"
    @click="emit('toggle')"
  >
    {{ paid ? 'Bezahlt' : 'Offen' }}
  </button>
</template>

<script setup>
defineProps({ paid: { type: Boolean, required: true } })
const emit = defineEmits(['toggle'])
</script>

<style scoped>
.payment-chip {
  font-size: 11px; font-weight: 700; font-family: inherit;
  border-radius: 8px; padding: 3px 10px;
  cursor: pointer; border: 1px solid transparent;
  transition: all 0.15s; white-space: nowrap;
}
.chip--paid {
  background: rgba(72,187,120,0.15);
  border-color: rgba(72,187,120,0.3);
  color: rgba(72,187,120,0.9);
}
.chip--unpaid {
  background: rgba(252,129,129,0.1);
  border-color: rgba(252,129,129,0.25);
  color: rgba(252,129,129,0.8);
}
</style>
