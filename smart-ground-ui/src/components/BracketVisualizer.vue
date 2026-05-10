<template>
  <div class="bracket-visualizer">
    <div class="bracket-container" ref="container">
      <svg :viewBox="`0 0 ${svgWidth} ${svgHeight}`" class="bracket-svg" preserveAspectRatio="xMinYMin meet">
        <!-- Verbindungslinien (müssen vor den Match-Boxen gezeichnet werden) -->
        <g class="connection-lines">
          <path
            v-for="(line, idx) in connectionLines"
            :key="`line-${idx}`"
            :d="line.path"
            class="line"
          />
        </g>

        <!-- Runden mit Matches -->
        <g v-for="(round, roundIdx) in rounds" :key="`round-${roundIdx}`" class="round">
          <text
            :x="roundX[roundIdx]"
            :y="20"
            class="round-label"
            text-anchor="middle"
          >
            {{ getRoundLabel(roundIdx + 1) }}
          </text>

          <g
            v-for="(match, matchIdx) in round"
            :key="`match-${roundIdx}-${matchIdx}`"
            class="match-group"
            @click="selectMatch(match)"
          >
            <!-- Match-Box -->
            <rect
              :x="roundX[roundIdx] - matchWidth / 2"
              :y="matchY(roundIdx, matchIdx)"
              :width="matchWidth"
              :height="matchHeight"
              :class="{
                'match-box': true,
                'match-pending': !match.winnerId && !match.isBye,
                'match-completed': match.winnerId,
                'match-bye': match.isBye,
                'match-selected': isSelectedMatch(match),
              }"
            />

            <!-- Gewinner-Stern -->
            <text
              v-if="match.winnerId"
              :x="roundX[roundIdx] + matchWidth / 2 - 8"
              :y="matchY(roundIdx, matchIdx) + 16"
              class="winner-star"
            >
              ⭐
            </text>

            <!-- Spieler 1 -->
            <text
              :x="roundX[roundIdx] - matchWidth / 2 + 8"
              :y="matchY(roundIdx, matchIdx) + 20"
              class="player-name"
              :class="{ 'player-winner': match.winnerId === match.contestant1Id }"
            >
              {{ getPlayerName(match.contestant1Id) || '—' }}
            </text>

            <!-- Score 1 -->
            <text
              v-if="match.score1 !== null && match.score1 !== undefined"
              :x="roundX[roundIdx] + 15"
              :y="matchY(roundIdx, matchIdx) + 20"
              class="score"
            >
              {{ match.score1 }}
            </text>

            <!-- Spieler 2 -->
            <text
              :x="roundX[roundIdx] - matchWidth / 2 + 8"
              :y="matchY(roundIdx, matchIdx) + 35"
              class="player-name"
              :class="{ 'player-winner': match.winnerId === match.contestant2Id }"
            >
              {{ getPlayerName(match.contestant2Id) || '—' }}
            </text>

            <!-- Score 2 -->
            <text
              v-if="match.score2 !== null && match.score2 !== undefined"
              :x="roundX[roundIdx] + 15"
              :y="matchY(roundIdx, matchIdx) + 35"
              class="score"
            >
              {{ match.score2 }}
            </text>
          </g>
        </g>
      </svg>
    </div>

    <!-- Legende -->
    <div class="bracket-legend">
      <div class="legend-item">
        <div class="legend-box pending"></div>
        <span>Ausstehend</span>
      </div>
      <div class="legend-item">
        <div class="legend-box completed"></div>
        <span>Gespielt</span>
      </div>
      <div class="legend-item">
        <div class="legend-box bye"></div>
        <span>Freilos</span>
      </div>
      <div class="legend-item">
        <div class="legend-box selected"></div>
        <span>Ausgewählt</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useBracketStore } from '@/stores/bracketStore.js';

const props = defineProps({
  selectedMatch: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['selectMatch']);

const bracketStore = useBracketStore();
const container = ref(null);

// ── SVG Dimensionen ──
const matchWidth = 120;
const matchHeight = 55;
const roundSpacing = 160;
const matchSpacing = 80;

const svgWidth = computed(() => {
  const numRounds = Object.keys(bracketStore.matchesByRound).length;
  return numRounds * roundSpacing + 100;
});

const svgHeight = computed(() => {
  const maxMatches = Math.max(...Object.values(bracketStore.matchesByRound).map(r => r.length || 0));
  return Math.max(maxMatches * matchSpacing + 100, 400);
});

// ── Runden-Struktur ──
const rounds = computed(() => {
  const rds = [];
  const keys = Object.keys(bracketStore.matchesByRound)
    .map(k => parseInt(k))
    .sort((a, b) => a - b);

  for (const round of keys) {
    rds.push(bracketStore.matchesByRound[round] || []);
  }

  return rds;
});

const roundX = computed(() => {
  const xs = [];
  for (let i = 0; i < rounds.value.length; i++) {
    xs.push(80 + i * roundSpacing);
  }
  return xs;
});

// ── Match-Positionierung ──
const matchY = (roundIdx, matchIdx) => {
  const round = rounds.value[roundIdx];
  const totalMatches = round.length;
  const spacing = (svgHeight.value - 100) / Math.max(totalMatches, 1);
  return 60 + matchIdx * spacing;
};

// ── Verbindungslinien (Bracket-Struktur) ──
const connectionLines = computed(() => {
  const lines = [];

  // Verbinde jeden Winner zum nächsten Match
  for (let roundIdx = 0; roundIdx < rounds.value.length - 1; roundIdx++) {
    const round = rounds.value[roundIdx];
    const nextRound = rounds.value[roundIdx + 1];

    for (let matchIdx = 0; matchIdx < round.length; matchIdx++) {
      const match = round[matchIdx];
      const nextMatchIdx = Math.floor(matchIdx / 2);

      if (nextMatchIdx < nextRound.length) {
        const y1 = matchY(roundIdx, matchIdx) + matchHeight / 2;
        const y2 = matchY(roundIdx + 1, nextMatchIdx) + matchHeight / 2;
        const x1 = roundX.value[roundIdx] + matchWidth / 2;
        const x2 = roundX.value[roundIdx + 1] - matchWidth / 2;
        const midX = (x1 + x2) / 2;

        // Kubische Bezier-Kurve für sanfte Linien
        const path = `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`;
        lines.push({ path });
      }
    }
  }

  return lines;
});

// ── Helper-Funktionen ──
const getRoundLabel = (roundNum) => {
  if (roundNum === rounds.value.length) {
    return 'Finale';
  }
  return `Runde ${roundNum}`;
};

const getPlayerName = (playerId) => {
  if (!playerId) return null;
  const player = bracketStore.seededPlayers.find(p => p.playerId === playerId);
  return player ? player.displayName : null;
};

const isSelectedMatch = (match) => {
  return props.selectedMatch && props.selectedMatch.matchNumber === match.matchNumber;
};

const selectMatch = (match) => {
  emit('selectMatch', match);
};
</script>

<style scoped>
.bracket-visualizer {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #f9f9f9;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
}

.bracket-container {
  flex: 1;
  overflow: auto;
  padding: 1rem;
  background: white;
}

.bracket-svg {
  display: block;
  width: 100%;
  height: auto;
  min-height: 400px;
}

/* Verbindungslinien */
.connection-lines .line {
  stroke: #2196F3;
  stroke-width: 2;
  fill: none;
  opacity: 0.6;
}

/* Runden-Labels */
.round-label {
  font-weight: bold;
  font-size: 12px;
  fill: #333;
}

/* Match-Boxen */
.match-box {
  stroke-width: 2;
  cursor: pointer;
  transition: all 0.2s;
  rx: 4;
}

.match-pending {
  fill: #f0f0f0;
  stroke: #ccc;
}

.match-pending:hover {
  fill: #e8e8e8;
  stroke: #999;
}

.match-completed {
  fill: #e8f5e9;
  stroke: #4CAF50;
}

.match-completed:hover {
  fill: #c8e6c9;
}

.match-bye {
  fill: #f5f5f5;
  stroke: #bbb;
  opacity: 0.7;
}

.match-selected {
  fill: #bbdefb;
  stroke: #2196F3;
  stroke-width: 3;
}

/* Gewinner-Stern */
.winner-star {
  font-size: 16px;
  fill: #FFD700;
  text-anchor: end;
  pointer-events: none;
}

/* Spieler-Namen */
.player-name {
  font-size: 11px;
  fill: #333;
  pointer-events: none;
  font-weight: 500;
}

.player-winner {
  fill: #2196F3;
  font-weight: bold;
}

/* Scores */
.score {
  font-size: 12px;
  fill: #666;
  text-anchor: start;
  font-weight: bold;
  pointer-events: none;
}

/* Legende */
.bracket-legend {
  display: flex;
  gap: 2rem;
  padding: 1rem;
  background: #fafafa;
  border-top: 1px solid #ddd;
  flex-wrap: wrap;
  font-size: 0.9rem;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.legend-box {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: 2px solid;
}

.legend-box.pending {
  background: #f0f0f0;
  border-color: #ccc;
}

.legend-box.completed {
  background: #e8f5e9;
  border-color: #4CAF50;
}

.legend-box.bye {
  background: #f5f5f5;
  border-color: #bbb;
  opacity: 0.7;
}

.legend-box.selected {
  background: #bbdefb;
  border-color: #2196F3;
}

/* Responsive */
@media (max-width: 768px) {
  .bracket-legend {
    gap: 1rem;
  }

  .legend-item {
    font-size: 0.85rem;
  }
}
</style>
