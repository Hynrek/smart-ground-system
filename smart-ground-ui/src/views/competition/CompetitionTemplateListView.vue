<template>
  <div class="competition-template-list">
    <div class="view-header">
      <h1>Wettkampf-Vorlagen</h1>
      <router-link to="/competition/template/new" class="btn btn-primary">
        + Neue Vorlage
      </router-link>
    </div>

    <!-- Filter -->
    <div class="filter-section">
      <select v-model="selectedType" class="filter-select">
        <option value="">Alle Typen</option>
        <option value="COMPETITION">Wettkampf</option>
        <option value="TRAINING">Training</option>
      </select>
    </div>

    <!-- Template Grid -->
    <div v-if="isLoading" class="loading-spinner">Lade Vorlagen...</div>
    <div v-else-if="filteredTemplates.length === 0" class="empty-state">
      <p>Keine Vorlagen gefunden</p>
    </div>
    <div v-else class="template-grid">
      <div v-for="template in filteredTemplates" :key="template.id" class="template-card">
        <div class="card-header">
          <h3>{{ template.name }}</h3>
          <span class="badge" :class="template.type.toLowerCase()">{{ template.type }}</span>
        </div>
        <div class="card-body">
          <p v-if="template.maxGroups">Maximale Gruppen: {{ template.maxGroups }}</p>
          <p v-if="template.bracketType">Bracket: {{ template.bracketType }}</p>
          <p v-if="template.defaultTiebreaker">Tiebreaker: {{ template.defaultTiebreaker }}</p>
        </div>
        <div class="card-actions">
          <button class="btn btn-primary" @click="selectTemplate(template)">
            Starten
          </button>
          <router-link :to="`/competition/template/${template.id}/edit`" class="btn btn-secondary">
            Bearbeiten
          </router-link>
          <button class="btn btn-danger" @click="deleteTemplateConfirm(template.id)">
            Löschen
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useCompetitionStore } from '@/stores/competitionStore.js';

const router = useRouter();
const competitionStore = useCompetitionStore();

const selectedType = ref('');
const isLoading = ref(false);

const filteredTemplates = computed(() => {
  if (!selectedType.value) {
    return competitionStore.templates;
  }
  return competitionStore.templates.filter((t) => t.type === selectedType.value);
});

onMounted(async () => {
  isLoading.value = true;
  await competitionStore.loadTemplates();
  isLoading.value = false;
});

const selectTemplate = (template) => {
  competitionStore.selectTemplate(template.id);
  router.push(`/competition/setup/${template.id}`);
};

const deleteTemplateConfirm = async (templateId) => {
  if (confirm('Vorlage wirklich löschen?')) {
    await competitionStore.deleteTemplate(templateId);
  }
};
</script>

<style scoped>
.competition-template-list {
  padding: 2rem;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.filter-section {
  margin-bottom: 2rem;
}

.filter-select {
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
}

.template-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
  background: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #eee;
}

.card-header h3 {
  margin: 0;
  font-size: 1.1rem;
}

.badge {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: bold;
  text-transform: uppercase;
}

.badge.competition {
  background: #4CAF50;
  color: white;
}

.badge.training {
  background: #2196F3;
  color: white;
}

.card-body {
  margin-bottom: 1rem;
}

.card-body p {
  margin: 0.5rem 0;
  font-size: 0.9rem;
  color: #666;
}

.card-actions {
  display: flex;
  gap: 0.5rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  text-decoration: none;
  display: inline-block;
}

.btn-primary {
  background: #2196F3;
  color: white;
}

.btn-primary:hover {
  background: #1976D2;
}

.btn-secondary {
  background: #9E9E9E;
  color: white;
}

.btn-secondary:hover {
  background: #757575;
}

.btn-danger {
  background: #f44336;
  color: white;
}

.btn-danger:hover {
  background: #da190b;
}

.empty-state,
.loading-spinner {
  text-align: center;
  padding: 3rem;
  color: #999;
}
</style>
