<template>
  <div class="qr-scan-overlay" @click.self="$emit('close')">
    <div class="qr-scan-modal">
      <h3 class="qr-scan-title">Schütze per QR-Code</h3>
      <video ref="videoEl" class="qr-scan-video" muted playsinline />
      <p v-if="error" class="qr-scan-error" role="alert">{{ error }}</p>
      <p v-else class="qr-scan-hint">QR-Code aus dem Profil vor die Kamera halten</p>
      <button class="qr-scan-cancel" @click="$emit('close')">Abbrechen</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import QrScanner from 'qr-scanner'
import { useProfileStore } from '@/stores/profileStore.js'
import { parseCheckinPayload } from '@/constants/qr.js'

const emit = defineEmits(['close', 'resolved'])
const profileStore = useProfileStore()

const videoEl = ref(null)
const error = ref('')
let scanner = null
let busy = false
let lastToken = ''
let alive = true

const handleScan = async (data) => {
  const token = parseCheckinPayload(data)
  if (!token) return // foreign QR code — ignore, keep scanning
  if (busy || token === lastToken) return
  busy = true
  lastToken = token
  error.value = ''
  try {
    const user = await profileStore.resolveCheckinToken(token)
    if (!alive) return // modal was closed while the request was in flight
    emit('resolved', user)
  } catch (e) {
    if (!alive) return
    error.value = e?.status === 404 ? 'Code ungültig' : 'Verbindungsfehler — nochmals versuchen'
    lastToken = '' // allow retrying the same code after an error
  } finally {
    busy = false
  }
}

onMounted(async () => {
  try {
    scanner = new QrScanner(videoEl.value, (result) => handleScan(result.data), {
      returnDetailedScanResult: true,
    })
    await scanner.start()
  } catch {
    if (alive) error.value = 'Kamera nicht verfügbar'
  }
})

onBeforeUnmount(() => {
  alive = false
  scanner?.destroy()
  scanner = null
})
</script>

<style scoped>
.qr-scan-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.qr-scan-modal {
  background: var(--sg-bg-card);
  border-radius: 12px;
  padding: 1.5rem;
  width: min(92vw, 420px);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: center;
}

.qr-scan-title {
  margin: 0;
  color: var(--sg-brand);
  font-size: 1.15rem;
}

.qr-scan-video {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  background: #000;
}

.qr-scan-hint {
  margin: 0;
  color: var(--sg-text-muted);
  font-size: 0.9rem;
  text-align: center;
}

.qr-scan-error {
  margin: 0;
  color: var(--sg-color-danger-text);
  font-weight: 600;
  text-align: center;
}

.qr-scan-cancel {
  min-height: 48px;
  padding: 0.6rem 2rem;
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  background: transparent;
  color: var(--sg-text-muted);
  font-size: 1rem;
  cursor: pointer;
}
</style>
