<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue';
import { useI18n } from '../composables/useI18n.js';
import { dashboardApi } from '../services/api.js';

const props = defineProps({
  currentPage: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['navigate', 'refresh']);
const { currentLang, setLang, t } = useI18n();
const collapsed = ref(false);

// Health check state
const healthStatus = ref('checking'); // 'up' | 'down' | 'checking'
const healthDetail = ref(null); // { camelStatus, camelVersion, uptimeSeconds, routeCount }
let healthPollTimer = null;

async function checkHealth() {
  try {
    const data = await dashboardApi.health.check();
    healthStatus.value = data.status === 'UP' ? 'up' : 'down';
    healthDetail.value = data;
  } catch {
    healthStatus.value = 'down';
    healthDetail.value = null;
  }
}

function formatUptime(seconds) {
  if (seconds == null) return '-';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

const healthDotClass = computed(() => ({
  green: healthStatus.value === 'up',
  red: healthStatus.value === 'down',
  yellow: healthStatus.value === 'checking'
}));

const healthLabel = computed(() => {
  if (healthStatus.value === 'up') return t('topbar.running');
  if (healthStatus.value === 'down') return t('topbar.offline');
  return t('topbar.checking');
});

const isMobile = ref(false);

function updateIsMobile() {
  isMobile.value = window.innerWidth <= 900;
}

onMounted(() => {
  checkHealth();
  healthPollTimer = setInterval(checkHealth, 30000);
  
  updateIsMobile();
  window.addEventListener('resize', updateIsMobile);
});

onUnmounted(() => {
  clearInterval(healthPollTimer);
  window.removeEventListener('resize', updateIsMobile);
});

function handleNavigate(pageId) {
  emit('navigate', pageId);
  if (isMobile.value) {
    collapsed.value = false;
  }
}

const pages = [
  { id: 'services', icon: 'box' },
  { id: 'routes', icon: 'listArrow' },
  { id: 'upload', icon: 'upload' },
  //{ id: 'versions', icon: 'clock' },
  { id: 'deploy', icon: 'send' },
  { id: 'beans', icon: 'grid' },
  { id: 'properties', icon: 'settings' },
  { id: 'connections', icon: 'database' },
  { id: 'cluster', icon: 'server' }
];

const pageTitle = computed(() => t(`nav.${props.currentPage}`));
const menuIconOpen = `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>`; //(☰)
const menuIconClose = `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`; //(✕)
const iconSvgs = {
  database: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/><path d="M3 12c0 1.66 4 3 9 3s9-1.34 9-3"/></svg>`,
  box: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>`,
  list: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>`,
  upload: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 16 12 12 8 16"/><line x1="12" y1="12" x2="12" y2="21"/><path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"/></svg>`,
  clock: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`,
  send: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>`,
  grid: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>`,
  settings: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>`,
  server: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>`,
  listArrow: '<svg width="18" height="18" viewBox="0 0 48 48" version="1.1" xmlns="http://www.w3.org/2000/svg" xmlnsa:xlink="http://www.w3.org/1999/xlink"> <g id="connection-arrow" stroke="none" stroke-width="1" fill="none" fill-rule="evenodd" stroke-linecap="round" stroke-linejoin="round"> <rect width="48" height="48" fill="white" fill-opacity="0.01"/> <g id="编组-2" transform="translate(4.000000, 3.953742)" stroke="currentColor" stroke-width="4"> <g id="编组" transform="translate(0.000000, 4.046258)"> <path d="M36.9898007,0.0264752559 L8.18181818,0.0264752559 C5.45454545,0.0264752559 0,1.550592 0,7.96725867 C0,14.3839253 5.45454545,16 8.18181818,16 L31.9938556,16 C34.7211284,16 40,17.5679494 40,23.9846161 C40,30.4012828 34.7211284,32.0029211 31.9938556,32.0029211 L2.06499237,32.0029211" id="Path-307"> </path> <polyline id="Path-309" points="4.04568993 27.99261 0.0673983189 32.0591365 4.04568993 36"> </polyline> </g> <polyline id="Path-309" transform="translate(36.032050, 4.003695) scale(-1, 1) translate(-36.032050, -4.003695) " points="38.0211961 5.32907052e-15 34.0429045 4.06652653 38.0211961 8.00739003"> </polyline> </g> </g> </svg>'
};

const toggleMenuIcon = computed(() => {  
  return collapsed.value ? menuIconOpen : menuIconClose;
});

</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="sidebar-logo">
      <span class="logo-mark">C</span>
      <span>CamelDash</span>
      <button 
        v-if="isMobile" 
        class="sidebar-close-btn" 
        type="button" 
        @click="collapsed = false" 
        v-html="menuIconClose"
      ></button>
    </div>
    <nav class="sidebar-nav">
      <button
        v-for="page in pages"
        :key="page.id"
        type="button"
        class="nav-item"
        :class="{ active: page.id === currentPage }"
        @click="handleNavigate(page.id)"
      >
        <span class="nav-icon" v-html="iconSvgs[page.icon]"></span>
        <span>{{ t(`nav.${page.id}`) }}</span>
      </button>
    </nav>
  </aside>

  <div 
    v-if="isMobile && collapsed" 
    class="sidebar-overlay" 
    @click="collapsed = false"
  ></div>

  <main class="main">
    <header class="topbar">
      <div class="topbar-title-wrap">
        <button class="btn btn-ghost btn-sm" type="button" @click="collapsed = !collapsed" v-html="toggleMenuIcon"></button>
        <div class="topbar-title">{{ pageTitle }}</div>
      </div>
      <div class="topbar-actions">
        <div class="lang-switcher">
          <button class="btn-lang" :class="{ active: currentLang === 'vi' }" type="button" @click="setLang('vi')">VI</button>
          <button class="btn-lang" :class="{ active: currentLang === 'en' }" type="button" @click="setLang('en')">EN</button>
        </div>
        <div class="status-indicator" :title="healthDetail ? `Camel ${healthDetail.camelStatus} v${healthDetail.camelVersion} | Routes: ${healthDetail.routeCount} | Uptime: ${formatUptime(healthDetail.uptimeSeconds)}` : 'Connecting...'">
          <span class="dot" :class="healthDotClass"></span>
          <span>{{ healthLabel }}</span>
        </div>
        <button class="btn btn-primary" type="button" @click="emit('refresh')">
           <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M23 4v6h-6M1 20v-6h6" />
            <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15" />
          </svg>
          <span>{{ t('topbar.refresh') }}</span></button>
      </div>
    </header>
    <slot />
  </main>
</template>
