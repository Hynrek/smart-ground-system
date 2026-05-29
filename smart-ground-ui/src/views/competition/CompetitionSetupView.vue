<template>
  <div class="competition-setup">
    <div class="setup-header">
      <h1>Wettkampf-Setup</h1>
      <p v-if="competitionStore.selectedTemplate" class="template-name">
        Vorlage: {{ competitionStore.selectedTemplate.name }}
      </p>
    </div>

    <!-- Groups Section -->
    <div class="setup-section">
      <h2>Gruppen</h2>
      <button class="btn btn-primary" @click="showGroupForm = true">+ Gruppe hinzufügen</button>

      <div v-if="showGroupForm" class="group-form">
        <input v-model="newGroup.name" placeholder="Gruppenname" />
        <button class="btn btn-primary" @click="addGroup">Hinzufügen</button>
        <button class="btn btn-secondary" @click="showGroupForm = false">Abbrechen</button>
      </div>

      <div class="groups-list">
        <div v-for="group in groups" :key="group.id" class="group-item">
          <h4>{{ group.name }}</h4>
          <p>{{ group.members ? group.members.length : 0 }} Schützen</p>
          <button class="btn btn-small" @click="editGroup(group.id)">Bearbeiten</button>
          <button class="btn btn-small btn-danger" @click="deleteGroup(group.id)">Löschen</button>
        </div>
      </div>
    </div>

    <!-- Start Button -->
    <div class="setup-actions">
      <button class="btn btn-primary btn-large" :disabled="groups.length === 0" @click="startCompetition">
        Wettkampf starten
      </button>
      <router-link to="/competition" class="btn btn-secondary">Zurück</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useCompetitionStore } from '@/stores/competitionStore.js';

const router = useRouter();
const route = useRoute();
const competitionStore = useCompetitionStore();

const groups = ref([]);
const showGroupForm = ref(false);
const newGroup = ref({ name: '' });

onMounted(async () => {
  const templateId = route.params.templateId;
  if (!competitionStore.selectedTemplate || competitionStore.selectedTemplate.id !== templateId) {
    // Load templates if not already loaded
    if (competitionStore.templates.length === 0) {
      await competitionStore.loadTemplates();
    }
    const template = competitionStore.templates.find((t) => t.id === templateId);
    if (template) {
      competitionStore.selectTemplate(templateId);
    } else {
      router.push('/competition');
    }
  }
});

const addGroup = () => {
  if (newGroup.value.name.trim()) {
    groups.value.push({
      id: `group-${Date.now()}`,
      name: newGroup.value.name,
      members: [],
    });
    newGroup.value.name = '';
    showGroupForm.value = false;
  }
};

const editGroup = (groupId) => {
  // Route to group edit view
  router.push(`/competition/group/${groupId}/edit`);
};

const deleteGroup = (groupId) => {
  if (confirm('Gruppe wirklich löschen?')) {
    groups.value = groups.value.filter((g) => g.id !== groupId);
  }
};

const startCompetition = async () => {
  try {
    await competitionStore.initSession(
      competitionStore.selectedTemplate.id,
      groups.value
    );
    router.push(`/competition/live/${competitionStore.currentSession.id}`);
  } catch (err) {
    console.error('Failed to start competition:', err);
  }
};
</script>

<style scoped>
.competition-setup {
  padding: 2rem;
}

.setup-header h1 {
  margin-bottom: 0.5rem;
}

.template-name {
  color: #666;
  font-size: 0.9rem;
}

.setup-section {
  margin-top: 2rem;
  padding: 1.5rem;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.setup-section h2 {
  margin-top: 0;
}

.group-form {
  margin: 1rem 0;
  padding: 1rem;
  background: #f9f9f9;
  border-radius: 4px;
}

.group-form input {
  padding: 0.5rem;
  margin-right: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.groups-list {
  margin-top: 1rem;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.group-item {
  padding: 1rem;
  border: 1px solid #eee;
  border-radius: 4px;
  background: white;
}

.group-item h4 {
  margin: 0 0 0.5rem 0;
}

.group-item p {
  margin: 0 0 0.5rem 0;
  color: #666;
  font-size: 0.9rem;
}

.setup-actions {
  margin-top: 2rem;
  display: flex;
  gap: 1rem;
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

.btn-primary:hover:not(:disabled) {
  background: #1976D2;
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
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

.btn-small {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
}

.btn-large {
  padding: 0.75rem 1.5rem;
  font-size: 1rem;
}
</style>
