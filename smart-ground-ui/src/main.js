import './assets/main.css';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router/index.js';
import { useAuthStore } from './stores/authStore.js';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);

// Authentifizierungsstatus aus gespeichertem Token wiederherstellen
const authStore = useAuthStore();
authStore.init().finally(() => {
  app.mount('#app');
});
