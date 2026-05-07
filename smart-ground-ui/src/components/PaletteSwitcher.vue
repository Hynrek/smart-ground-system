<template>
  <div class="palette-switcher">
    <label for="palette-select">Palette:</label>
    <select
      id="palette-select"
      v-model="selectedPalette"
      @change="changePalette"
    >
      <optgroup label="Balanced Palettes">
        <option value="current">Current (Cyan)</option>
        <option value="professional">Professional (Teal)</option>
        <option value="modern">Modern (Blue)</option>
        <option value="gaming">Gaming (Neon)</option>
        <option value="vibrant">Vibrant (Orange)</option>
        <option value="minimal">Minimal (Clean)</option>
      </optgroup>
      <optgroup label="Green Variants">
        <option value="professional-green">Professional Green (Emerald)</option>
        <option value="vibrant-green">Vibrant Green (Bright)</option>
        <option value="eco-professional">Eco Professional (Forest)</option>
        <option value="active-green">Active Green (Neon)</option>
        <option value="sage-green">Sage Green (Soft)</option>
        <option value="forest-teal">Forest Teal (Nature)</option>
      </optgroup>
    </select>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const selectedPalette = ref('current');

const changePalette = (event) => {
  const palette = event.target.value;
  console.log('Palette changing to:', palette);
  selectedPalette.value = palette;
  document.documentElement.setAttribute('data-palette', palette);
  localStorage.setItem('smart-ground-palette', palette);
  console.log('Data attribute set:', document.documentElement.getAttribute('data-palette'));
};

onMounted(() => {
  const saved = localStorage.getItem('smart-ground-palette') || 'current';
  selectedPalette.value = saved;
  document.documentElement.setAttribute('data-palette', saved);
});
</script>

<style scoped>
.palette-switcher {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(10px);
  border-radius: 8px;
  padding: 12px 16px;
  color: white;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

label {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

select {
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid #555;
  background: #2a2a2a;
  color: white;
  font-size: 13px;
  cursor: pointer;
  font-weight: 500;
  min-width: 200px;
  max-width: 280px;
}

select:focus {
  outline: none;
  border-color: #4fc3f7;
  box-shadow: 0 0 0 2px rgba(79, 195, 247, 0.2);
}

select:hover {
  border-color: #4fc3f7;
  background: #323232;
}

select option {
  background: #1a1a2e;
  color: white;
  padding: 4px 8px;
}

select optgroup {
  background: #1a1a2e;
  color: #4fc3f7;
  font-weight: 600;
}
</style>
