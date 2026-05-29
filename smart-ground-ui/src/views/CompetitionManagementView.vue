<template>
  <div class="competition-management">
    <!-- Header -->
    <div class="view-header">
      <div>
        <h1 class="view-title">Wettkampf & Training</h1>
        <p class="view-subtitle">Verwalten Sie Trainings- und Turniersitzungen</p>
      </div>
      <Button variant="primary" @click="navigateTo('/competition/setup')">
        <Icons icon="plus" :size="14" />
        Neue Sitzung
      </Button>
    </div>

    <!-- Quick Stats -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue">
          <Icons icon="activity" :size="16" color="#4fc3f7" />
        </div>
        <div>
          <div class="stat-value">{{ activeSessionCount }}</div>
          <div class="stat-label">Aktive Sitzungen</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple">
          <Icons icon="award" :size="16" color="#9c27b0" />
        </div>
        <div>
          <div class="stat-value">{{ bracketTournamentCount }}</div>
          <div class="stat-label">Turniere</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green">
          <Icons icon="users" :size="16" color="#48bb78" />
        </div>
        <div>
          <div class="stat-value">{{ totalPlayerCount }}</div>
          <div class="stat-label">Schützen registriert</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--orange">
          <Icons icon="trending-up" :size="16" color="#ed8936" />
        </div>
        <div>
          <div class="stat-value">{{ templateCount }}</div>
          <div class="stat-label">Sitzungsvorlagen</div>
        </div>
      </div>
    </div>

    <!-- Management Sections -->
    <div class="management-grid">
      <!-- Training Sessions Section -->
      <div class="management-card">
        <div class="card-header">
          <h2 class="card-title">
            <Icons icon="activity" :size="18" />
            Trainings-Sitzungen
          </h2>
          <span class="badge badge-blue">{{ activeSessionCount }} aktiv</span>
        </div>
        <p class="card-description">
          Erstellen Sie Trainings- und Qualifikationssitzungen. Verwalten Sie Gruppen und Schießplätze in Echtzeit.
        </p>
        <div class="card-actions">
          <Button
            variant="secondary"
            size="compact"
            @click="navigateTo('/competition/setup')"
          >
            <Icons icon="play" :size="13" />
            Neue Sitzung
          </Button>
          <Button
            variant="secondary"
            size="compact"
            @click="navigateTo('/competition/templates')"
          >
            <Icons icon="file" :size="13" />
            Vorlagen
          </Button>
          <Button
            v-if="activeSessionCount > 0"
            variant="secondary"
            size="compact"
            @click="navigateTo('/competition/live')"
          >
            <Icons icon="eye" :size="13" />
            Live anzeigen
          </Button>
        </div>
      </div>

      <!-- Bracket Tournaments Section -->
      <div class="management-card">
        <div class="card-header">
          <h2 class="card-title">
            <Icons icon="award" :size="18" />
            Bracket-Turniere
          </h2>
          <span class="badge badge-purple">{{ bracketTournamentCount }} insgesamt</span>
        </div>
        <p class="card-description">
          Verwalten Sie Single- und Double-Elimination-Turniere. Schützen setzen und Match-Ergebnisse aufzeichnen.
        </p>
        <div class="card-actions">
          <Button
            variant="secondary"
            size="compact"
            @click="navigateTo('/competition/bracket')"
          >
            <Icons icon="layout" :size="13" />
            Bracket-Ansicht
          </Button>
          <Button
            variant="secondary"
            size="compact"
            @click="navigateTo('/competition/setup')"
          >
            <Icons icon="plus" :size="13" />
            Neues Turnier
          </Button>
        </div>
      </div>

      <!-- Schützenverwaltung Section -->
      <div class="management-card">
        <div class="card-header">
          <h2 class="card-title">
            <Icons icon="users" :size="18" />
            Schützenverwaltung
          </h2>
          <span class="badge badge-green">{{ totalPlayerCount }} Schützen</span>
        </div>
        <p class="card-description">
          Verwalten Sie Schützenregister, Gruppen und Registrierungen für Sitzungen.
        </p>
        <div class="card-actions">
          <Button
            variant="secondary"
            size="compact"
            @click="navigateTo('/player-setup')"
          >
            <Icons icon="users" :size="13" />
            Schützen verwalten
          </Button>
          <Button
            variant="secondary"
            size="compact"
            @click="navigateTo('/career-stats')"
          >
            <Icons icon="bar-chart" :size="13" />
            Statistiken
          </Button>
        </div>
      </div>

      <!-- Templates Section -->
      <div class="management-card">
        <div class="card-header">
          <h2 class="card-title">
            <Icons icon="file" :size="18" />
            Sitzungsvorlagen
          </h2>
          <span class="badge badge-orange">{{ templateCount }} verfügbar</span>
        </div>
        <p class="card-description">
          Erstellen und verwalten Sie wiederverwendbare Sitzungskonfigurationen für Training und Turniere.
        </p>
        <div class="card-actions">
          <Button
            variant="secondary"
            size="compact"
            @click="navigateTo('/competition/templates')"
          >
            <Icons icon="list" :size="13" />
            Alle Vorlagen
          </Button>
          <Button
            variant="secondary"
            size="compact"
            @click="createNewTemplate"
          >
            <Icons icon="plus" :size="13" />
            Neue Vorlage
          </Button>
        </div>
      </div>
    </div>

    <!-- Recent Activity -->
    <div class="recent-section">
      <h2 class="section-title">Kürzliche Aktivität</h2>
      <div class="activity-list">
        <div v-if="recentSessions.length === 0" class="empty-state">
          <p>Keine kürzlichen Sitzungen</p>
        </div>
        <div v-for="session in recentSessions" :key="session.id" class="activity-item">
          <div class="activity-icon" :class="[`status-${session.status.toLowerCase()}`]">
            <Icons
              :icon="getSessionIcon(session.status)"
              :size="14"
              color="currentColor"
            />
          </div>
          <div class="activity-info">
            <div class="activity-title">{{ session.name }}</div>
            <div class="activity-meta">{{ formatDate(session.createdAt) }} · {{ session.type }}</div>
          </div>
          <div class="activity-status">
            <span class="status-badge" :class="[`status-${session.status.toLowerCase()}`]">
              {{ formatStatus(session.status) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Reference -->
    <div class="quick-reference">
      <h2 class="section-title">Hilfe & Dokumentation</h2>
      <div class="reference-grid">
        <div class="reference-card">
          <h3>Trainings-Sitzung starten</h3>
          <ol>
            <li>Gehen Sie zu "Sitzungsvorlagen"</li>
            <li>Wählen Sie eine Trainingsvorlage</li>
            <li>Erstellen Sie eine neue Sitzung</li>
            <li>Registrieren Sie Gruppen an Plätzen</li>
          </ol>
        </div>
        <div class="reference-card">
          <h3>Bracket-Turnier erstellen</h3>
          <ol>
            <li>Erstellen Sie eine Turniersitzung</li>
            <li>Konfigurieren Sie den Bracket-Typ</li>
            <li>Schützen setzen (automatisch oder manuell)</li>
            <li>Starten Sie das Turnier</li>
            <li>Zeichnen Sie Match-Ergebnisse auf</li>
          </ol>
        </div>
        <div class="reference-card">
          <h3>Schützen hinzufügen</h3>
          <ol>
            <li>Gehen Sie zu "Schützenverwaltung"</li>
            <li>Erstellen Sie einen neuen Schützen</li>
            <li>Ordnen Sie ihn einer Gruppe zu</li>
            <li>Registrieren Sie ihn für Sitzungen</li>
          </ol>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useCompetitionStore } from '@/stores/competitionStore.js';
import { useBracketStore } from '@/stores/bracketStore.js';
import Button from '@/components/Button.vue';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const competitionStore = useCompetitionStore();
const bracketStore = useBracketStore();

const activeSessionCount = ref(0);
const bracketTournamentCount = ref(0);
const totalPlayerCount = ref(12); // Placeholder
const templateCount = ref(0);
const recentSessions = ref([]);

const navigateTo = (path) => {
  router.push(path);
};

const createNewTemplate = () => {
  router.push('/competition/templates');
};

const formatDate = (dateString) => {
  if (!dateString) return 'Unbekannt';
  const date = new Date(dateString);
  return date.toLocaleDateString('de-CH');
};

const formatStatus = (status) => {
  const statusMap = {
    ACTIVE: 'Aktiv',
    PAUSED: 'Unterbrochen',
    COMPLETED: 'Abgeschlossen',
    ABANDONED: 'Abgebrochen',
  };
  return statusMap[status] || status;
};

const getSessionIcon = (status) => {
  switch (status) {
    case 'ACTIVE':
      return 'activity';
    case 'PAUSED':
      return 'pause';
    case 'COMPLETED':
      return 'check-circle';
    case 'ABANDONED':
      return 'x-circle';
    default:
      return 'circle';
  }
};

onMounted(async () => {
  try {
    await competitionStore.loadTemplates();
    templateCount.value = competitionStore.templates.length;

    // Mock data for recent sessions (would be real data from API)
    recentSessions.value = [
      {
        id: '1',
        name: 'Trainings-Session A',
        type: 'Training',
        status: 'ACTIVE',
        createdAt: new Date().toISOString(),
      },
      {
        id: '2',
        name: 'Qualifikations-Runde',
        type: 'Qualifikation',
        status: 'COMPLETED',
        createdAt: new Date(Date.now() - 86400000).toISOString(),
      },
    ];
  } catch (err) {
    console.error('Failed to load competition data:', err);
  }
});
</script>

<style scoped>
.competition-management {
  padding: 24px;
  max-width: 1400px;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 28px;
  gap: 16px;
}

.view-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 4px 0;
}

.view-subtitle {
  font-size: 14px;
  color: #718096;
  margin: 0;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}

.stat-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
  transition: all 0.2s;
}

.stat-card:hover {
  border-color: #cbd5e0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon--blue {
  background: rgba(79, 195, 247, 0.1);
}

.stat-icon--purple {
  background: rgba(156, 39, 176, 0.1);
}

.stat-icon--green {
  background: rgba(72, 187, 120, 0.1);
}

.stat-icon--orange {
  background: rgba(237, 137, 54, 0.1);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
}

.stat-label {
  font-size: 12px;
  color: #718096;
  font-weight: 500;
}

/* Management Grid */
.management-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.management-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
  transition: all 0.2s;
}

.management-card:hover {
  border-color: #cbd5e0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 4px;
  white-space: nowrap;
}

.badge-blue {
  background: rgba(79, 195, 247, 0.1);
  color: #0277bd;
}

.badge-purple {
  background: rgba(156, 39, 176, 0.1);
  color: #6a1b9a;
}

.badge-green {
  background: rgba(72, 187, 120, 0.1);
  color: #1b5e20;
}

.badge-orange {
  background: rgba(237, 137, 54, 0.1);
  color: #e65100;
}

.card-description {
  font-size: 13px;
  color: #718096;
  margin: 0 0 16px 0;
  line-height: 1.5;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* Recent Activity */
.recent-section {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 32px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 16px 0;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #a0aec0;
  font-size: 14px;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #f7f8fc;
  border-radius: 6px;
  transition: all 0.2s;
}

.activity-item:hover {
  background: #eef2f8;
}

.activity-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-weight: 600;
}

.activity-icon.status-active {
  background: rgba(72, 187, 120, 0.15);
  color: #22863a;
}

.activity-icon.status-completed {
  background: rgba(79, 195, 247, 0.15);
  color: #0277bd;
}

.activity-icon.status-paused {
  background: rgba(237, 137, 54, 0.15);
  color: #e65100;
}

.activity-icon.status-abandoned {
  background: rgba(244, 67, 54, 0.15);
  color: #c62828;
}

.activity-info {
  flex: 1;
}

.activity-title {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a2e;
}

.activity-meta {
  font-size: 12px;
  color: #718096;
  margin-top: 2px;
}

.activity-status {
  flex-shrink: 0;
}

.status-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 3px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.status-badge.status-active {
  background: rgba(72, 187, 120, 0.2);
  color: #22863a;
}

.status-badge.status-completed {
  background: rgba(79, 195, 247, 0.2);
  color: #0277bd;
}

.status-badge.status-paused {
  background: rgba(237, 137, 54, 0.2);
  color: #e65100;
}

.status-badge.status-abandoned {
  background: rgba(244, 67, 54, 0.2);
  color: #c62828;
}

/* Quick Reference */
.quick-reference {
  background: #f7f8fc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
}

.reference-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
}

.reference-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 16px;
}

.reference-card h3 {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 12px 0;
}

.reference-card ol {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: #4a5568;
  line-height: 1.8;
}

.reference-card li {
  margin-bottom: 6px;
}

@media (max-width: 768px) {
  .competition-management {
    padding: 16px;
  }

  .view-header {
    flex-direction: column;
  }

  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }

  .management-grid {
    grid-template-columns: 1fr;
  }

  .reference-grid {
    grid-template-columns: 1fr;
  }
}
</style>
