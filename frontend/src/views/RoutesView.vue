<script setup>
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue';
import StatusBadge from '../components/StatusBadge.vue';
import { fmtDate, routeDisplayId } from '../composables/useFormatters.js';
import { useI18n } from '../composables/useI18n.js';
import { useToasts } from '../composables/useToasts.js';
import { dashboardApi } from '../services/api.js';
import { useConfirm } from 'primevue/useconfirm';
import { useClusterTooltip } from '../composables/useClusterTooltip.js';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();
const confirm = useConfirm();
const { tooltipActive, tooltipNodes, tooltipStyle, showTooltip, hideTooltip } = useClusterTooltip();

const servicesWithDetails = ref([]);
const query = ref('');
const loading = ref(false);

// ─── MANAGEMENT STATE MODAL TABS ───
const isModalOpen = ref(false);
const currentTab = ref('source'); // 'source' or 'diagram'
const modalRoute = ref(null);
const sourceContent = ref('');
const mermaidCode = ref('');
const yamlCodeBlock = ref(null);

// State for handling Mermaid diagram zoom / pan
const diagramZoom = ref(1);
const diagramPanX = ref(0);
const diagramPanY = ref(0);
const isDragging = ref(false);

let dragStartX = 0, dragStartY = 0;
let dragPanX = 0, dragPanY = 0;

// State for managing separate description information view
const isDescModalOpen = ref(false);
const descModalTitle = ref('');
const descModalContent = ref('');

// Expand/collapse states for Services and REST groups
const expandedServices = ref(new Set());
const expandedRests = ref(new Set());

function toggleService(serviceId) {
  if (expandedServices.value.has(serviceId)) {
    expandedServices.value.delete(serviceId);
  } else {
    expandedServices.value.add(serviceId);
  }
}

function toggleRest(restKey) {
  if (expandedRests.value.has(restKey)) {
    expandedRests.value.delete(restKey);
  } else {
    expandedRests.value.add(restKey);
  }
}

watch(servicesWithDetails, (newVal) => {
  if (newVal && newVal.length > 0 && expandedServices.value.size === 0) {
    expandedServices.value.add(newVal[0].serviceId);
  }
}, { immediate: true });

// Dynamic stats computed property
const stats = computed(() => {
  let total = 0;
  let running = 0;
  let stopped = 0;
  let suspended = 0;
  let persistent = 0;

  const processedRouteIds = new Set();

  servicesWithDetails.value.forEach(svc => {
    // Process rests
    if (svc.rests) {
      svc.rests.forEach(rest => {
        if (rest.routeId && !processedRouteIds.has(rest.routeId)) {
          processedRouteIds.add(rest.routeId);
          total++;
          if (rest.status === 'Started') running++;
          else if (rest.status === 'Stopped') stopped++;
          else if (rest.status === 'Suspended') suspended++;
          persistent++; // REST endpoints are persistent versions
        }
      });
    }
    // Process regular routes
    if (svc.routes) {
      svc.routes.forEach(r => {
        if (r.id && !processedRouteIds.has(r.id)) {
          processedRouteIds.add(r.id);
          total++;
          if (r.status === 'Started') running++;
          else if (r.status === 'Stopped') stopped++;
          else if (r.status === 'Suspended') suspended++;
          if (r.persistent) persistent++;
        }
      });
    }
  });

  return { total, running, stopped, suspended, persistent };
});

// Advanced reactive filter logic for search
const filteredServices = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return servicesWithDetails.value;

  return servicesWithDetails.value.map(svc => {
    const nameMatches = (svc.name || '').toLowerCase().includes(q) 
      || (svc.description || '').toLowerCase().includes(q);
    
    const filteredRests = (svc.rests || []).filter(rest => {
      const pathMatches = (rest.path || '').toLowerCase().includes(q) 
        || (rest.description || '').toLowerCase().includes(q);
      const verbMatches = (rest.verbs || []).some(verb => 
        (verb.method || '').toLowerCase().includes(q) ||
        (verb.id || '').toLowerCase().includes(q) ||
        (verb.routeId || '').toLowerCase().includes(q) ||
        (verb.toUri || '').toLowerCase().includes(q) ||
        (verb.description || '').toLowerCase().includes(q)
      );
      return pathMatches || verbMatches;
    });

    const filteredRoutes = (svc.routes || []).filter(r => 
      (r.id || '').toLowerCase().includes(q) ||
      (r.originalId || '').toLowerCase().includes(q) ||
      (r.description || '').toLowerCase().includes(q) ||
      (r.sourceUri || '').toLowerCase().includes(q)
    );

    if (nameMatches || filteredRests.length > 0 || filteredRoutes.length > 0) {
      return {
        ...svc,
        rests: filteredRests,
        routes: filteredRoutes
      };
    }
    return null;
  }).filter(Boolean);
});

const routeMetrics = ref({});
const metricsInterval = ref(null);

async function fetchMetrics() {
  try {
    const data = await dashboardApi.routes.metrics();
    const metricsMap = {};
    if (Array.isArray(data)) {
      data.forEach(m => {
        metricsMap[m.routeId] = m;
      });
    }
    routeMetrics.value = metricsMap;
  } catch (error) {
    console.warn("Failed to fetch route metrics:", error);
  }
}

function formatCount(val) {
  if (val === undefined || val === null) return '0';
  if (val >= 1000000) {
    return (val / 1000000).toFixed(1) + 'M';
  }
  if (val >= 1000) {
    return (val / 1000).toFixed(1) + 'k';
  }
  return val.toString();
}

function formatFullNumber(val) {
  if (val === undefined || val === null) return '0';
  return Number(val).toLocaleString();
}

async function load() {
  loading.value = true;
  try {
    const data = await dashboardApi.routes.restServices();
    servicesWithDetails.value = data;
    await fetchMetrics();
  } catch (error) {
    toast(t('toast.load_routes_err') + error.message, 'error');
  } finally {
    loading.value = false;
  }
}

async function routeAction(route, action) {
  if (action === 'delete') {
    confirm.require({
      message: t('common.confirm_delete_route', routeDisplayId(route)),
      header: t('common.confirm_delete') || 'Xác nhận xóa',
      icon: 'pi pi-exclamation-triangle',
      rejectProps: {
        label: t('common.cancel') || 'Hủy',
        severity: 'secondary',
        outlined: true
      },
      acceptProps: {
        label: t('common.delete') || 'Xóa',
        severity: 'danger'
      },
      accept: async () => {
        try {
          await dashboardApi.routes.delete(route.id);
          toast(t('toast.active_success', routeDisplayId(route), action), 'success');
          await load();
        } catch (error) {
          toast(t('common.error') + error.message, 'error');
        }
      }
    });
  } else {
    try {
      await dashboardApi.routes.action(route.id, action);
      toast(t('toast.active_success', routeDisplayId(route), action), 'success');
      await load();
    } catch (error) {
      toast(t('common.error') + error.message, 'error');
    }
  }
}

async function viewSource(route) {
  try {
    modalRoute.value = route;
    currentTab.value = 'source';
    sourceContent.value = '';
    mermaidCode.value = '';
    resetDiagramTransform();

    isModalOpen.value = true;

    const [sourceData, mermaidData] = await Promise.all([
      dashboardApi.routes.source(route.id).catch(err => {
        console.warn("Failed to fetch route source", err);
        return { content: null };
      }),
      fetch(`/api/routes/${route.id}/mermaid`).then(r => r.json()).catch(err => {
        console.warn("Failed to fetch route mermaid", err);
        return { mermaidCode: null };
      })
    ]);

    sourceContent.value = sourceData?.content || '';
    mermaidCode.value = mermaidData?.mermaidCode || '';
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

function showDescription(route) {
  descModalTitle.value = `${t('routes.table.id')}: ${routeDisplayId(route)}`;
  descModalContent.value = route.description || '-';
  isDescModalOpen.value = true;
}

// ─── UTILITY HELPERS ───
function getVerbSeverity(method) {
  const m = (method || '').toUpperCase();
  if (m === 'GET') return 'success';
  if (m === 'POST') return 'info';
  if (m === 'DELETE') return 'danger';
  if (m === 'PUT' || m === 'PATCH') return 'warning';
  return 'secondary';
}

function collectRestParams(rest) {
  const params = [];
  if (rest.verbs) {
    rest.verbs.forEach(v => {
      if (v.params) {
        params.push(...v.params);
      }
    });
  }
  return params;
}

function isSingleRouteIdRest(rest) {
  if (!rest.verbs || rest.verbs.length === 0) return false;
  const firstId = rest.verbs[0].routeId;
  if (!firstId) return false;
  return rest.verbs.every(v => v.routeId === firstId);
}

// ─── MERMAID DIAGRAM ZOOM / PAN CONTROL LOGIC ───
function resetDiagramTransform() {
  diagramZoom.value = 1;
  diagramPanX.value = 0;
  diagramPanY.value = 0;
}

function changeZoom(delta) {
  diagramZoom.value = Math.min(6, Math.max(0.1, diagramZoom.value + delta));
}

function zoomFit() {
  const canvas = document.getElementById('vue-mermaid-canvas');
  const inner = document.getElementById('vue-mermaid-canvas-inner');
  if (!canvas || !inner) return;
  const svg = inner.querySelector('svg');
  if (!svg) return;

  resetDiagramTransform();
  nextTick(() => {
    const canvasW = canvas.clientWidth;
    const canvasH = canvas.clientHeight;
    const svgW = svg.clientWidth || svg.getBoundingClientRect().width;
    const svgH = svg.clientHeight || svg.getBoundingClientRect().height;
    if (!svgW || !svgH) return;

    const pad = 48;
    const scaleX = (canvasW - pad) / svgW;
    const scaleY = (canvasH - pad) / svgH;
    diagramZoom.value = Math.min(scaleX, scaleY, 1);

    diagramPanX.value = (canvasW - svgW * diagramZoom.value) / 2;
    diagramPanY.value = (canvasH - svgH * diagramZoom.value) / 2;
  });
}

function handleWheel(e) {
  e.preventDefault();
  const canvas = document.getElementById('vue-mermaid-canvas');
  if (!canvas) return;
  const rect = canvas.getBoundingClientRect();
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;
  const delta = e.deltaY < 0 ? 0.12 : -0.12;

  const oldZoom = diagramZoom.value;
  diagramZoom.value = Math.min(6, Math.max(0.1, diagramZoom.value + delta));
  diagramPanX.value = mouseX - (diagramZoom.value / oldZoom) * (mouseX - diagramPanX.value);
  diagramPanY.value = mouseY - (diagramZoom.value / oldZoom) * (mouseY - diagramPanY.value);
}

function handleMouseDown(e) {
  if (e.button !== 0) return;
  isDragging.value = true;
  dragStartX = e.clientX;
  dragStartY = e.clientY;
  dragPanX = diagramPanX.value;
  dragPanY = diagramPanY.value;
  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
}

function handleMouseMove(e) {
  if (!isDragging.value) return;
  diagramPanX.value = dragPanX + (e.clientX - dragStartX);
  diagramPanY.value = dragPanY + (e.clientY - dragStartY);
}

function handleMouseUp() {
  isDragging.value = false;
  document.removeEventListener('mousemove', handleMouseMove);
  document.removeEventListener('mouseup', handleMouseUp);
}

watch([currentTab, mermaidCode], ([newTab, code]) => {
  if (newTab === 'diagram' && code && window.mermaid) {
    nextTick(() => {
      const renderArea = document.getElementById('vue-mermaid-render-area');
      if (renderArea) {
        renderArea.removeAttribute('data-processed');
        renderArea.innerHTML = code;
        try {
          window.mermaid.run({ nodes: [renderArea] });
          setTimeout(zoomFit, 100);
        } catch (err) {
          console.error("Mermaid render error", err);
        }
      }
    });
  }
});

onMounted(() => {
  load();
  metricsInterval.value = setInterval(fetchMetrics, 5000);
});

onUnmounted(() => {
  if (metricsInterval.value) {
    clearInterval(metricsInterval.value);
  }
});

watch(() => props.refreshKey, load);

watch([currentTab, sourceContent], ([newTab, content]) => {
  if (newTab === 'source' && content && window.Prism) {
    nextTick(() => {
      const el = yamlCodeBlock.value
      if (el && el.classList.contains('language-yaml')) {
        try {
          window.Prism.highlightElement(el)
        } catch (error) {
          console.warn('Prism not ready:', error)
        }
      }
    });
  }
}, { immediate: true });

// ─── DYNAMIC TOOLTIP LOGIC ───
</script>

<template>
  <section class="page active">
    <!-- Stat row -->
    <div class="stats-row" id="stats-row">
      <Card>
        <template #title>{{ t('routes.stats.total') }}</template>
        <template #content>
          <div class="stat-value blue">{{ stats.total }}</div>
        </template>
      </Card>

      <Card>
        <template #title>{{ t('routes.stats.running') }}</template>
        <template #content>
          <div class="stat-value green">{{ stats.running }}</div>
        </template>
      </Card>
      <Card>
        <template #title>{{ t('routes.stats.stopped') }}</template>
        <template #content>
          <div class="stat-value red">{{ stats.stopped }}</div>
        </template>
      </Card>
      <Card>
        <template #title>{{ t('routes.stats.suspended') }}</template>
        <template #content>
          <div class="stat-value yellow">{{ stats.suspended }}</div>
        </template>
      </Card>
      <Card>
        <template #title>{{ t('routes.stats.persistent') }}</template>
        <template #content>
          <div class="stat-value purple">{{ stats.persistent }}</div>
        </template>
      </Card>
    </div>

    <!-- Search bar -->
    <div class="search-bar" style="margin-bottom: 1.5rem">
      <IconField>
        <InputIcon class="pi pi-search" />
        <InputText v-model="query" :placeholder="t('routes.search_placeholder') || 'Search services, REST paths, routes...'" />
      </IconField>
    </div>

    <!-- Hierarchical Services/Routes Listing -->
    <div v-if="filteredServices.length === 0" class="empty-state text-center p-8 border rounded-xl bg-surface-card flex flex-col items-center justify-center min-h-[30vh]">
      <i class="pi pi-inbox text-5xl text-surface-400 mb-4"></i>
      <p class="text-xl text-surface-500 font-semibold">{{ t('routes.no_routes_found') || 'No services or routes found' }}</p>
    </div>

    <div v-else class="services-list flex flex-col gap-6">
      <div v-for="svc in filteredServices" :key="svc.serviceId" class="service-card shadow-sm border rounded-xl bg-surface-card">
        
        <!-- Service Header Accordion Bar -->
        <div class="service-header flex justify-between items-center p-4 bg-[var(--surface)] cursor-pointer select-none border-b rounded-t-xl mb-3" @click="toggleService(svc.serviceId)">
          <div class="flex items-center gap-3">
            <i :class="expandedServices.has(svc.serviceId) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" class="text-base text-surface-400 transition-transform duration-200"></i>
            <div>
              <h3 class="text-lg font-bold text-surface-900 dark:text-surface-50 flex items-center gap-2">
                📦 {{ svc.name || 'Ungrouped Service' }}
              </h3>
              <p v-if="svc.description" class="text-sm text-surface-500 dark:text-surface-400 mt-0.5">{{ svc.description }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Badge v-if="svc.rests && svc.rests.length > 0" severity="info" :value="svc.rests.length + ' REST'"></Badge>
            <Badge v-if="svc.routes && svc.routes.length > 0" severity="secondary" :value="svc.routes.length + ' Route'"></Badge>
          </div>
        </div>

        <!-- Service Accordion Body -->
        <Transition name="fade-collapse">
          <div v-show="expandedServices.has(svc.serviceId)" class="service-body p-4 flex flex-col gap-6">
            
            <!-- 🌐 REST ENDPOINTS SECTION -->
            <div v-if="svc.rests && svc.rests.length > 0" class="rest-section">
              <h4 class="text-xs font-bold uppercase tracking-wider text-surface-400 dark:text-surface-500 mb-3 flex items-center gap-2">
                <i class="pi pi-globe text-primary"></i> REST Endpoints
              </h4>
              
              <div class="flex flex-col gap-4">
                <div v-for="rest in svc.rests" :key="rest.path" class="rest-endpoint-card border rounded-lg bg-surface-hover">
                  
                  <!-- REST Path Header Bar -->
                  <div class="rest-path-header flex justify-between items-center p-3 cursor-pointer select-none" @click="toggleRest(svc.serviceId + '-' + rest.path)">
                    <div class="flex items-center gap-3">
                      <i :class="expandedRests.has(svc.serviceId + '-' + rest.path) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" class="text-sm text-surface-400 transition-transform duration-200"></i>
                      <span class="font-mono text-base font-semibold bg-primary/10 dark:bg-primary/20 text-primary px-3 py-0.5 rounded border border-primary/20">
                        {{ rest.path }}
                      </span>
                      <div v-if="isSingleRouteIdRest(rest) && rest.nodeStates && rest.nodeStates.length > 0" 
                           class="status-dots-wrapper mr-2" 
                           @mouseenter="showTooltip($event, rest.nodeStates)" 
                           @mouseleave="hideTooltip" 
                           @click.stop>
                        <div class="status-dots">
                          <span v-for="node in rest.nodeStates" 
                                :key="node.instanceId" 
                                class="status-dot" 
                                :class="node.status.toLowerCase()">
                          </span>
                        </div>
                      </div>
                      <StatusBadge v-else-if="isSingleRouteIdRest(rest)" :status="rest.status" />
                      <span v-else class="text-xs font-semibold bg-surface-300 dark:bg-surface-700 text-surface-600 dark:text-surface-400 px-2 py-0.5 rounded">Multi-route API</span>
                      <span v-if="rest.description" class="text-sm text-surface-500 dark:text-surface-400 italic max-w-xs truncate" :title="rest.description">{{ rest.description }}</span>
                    </div>

                    <!-- REST Controls (Only visible if all verbs share the same Route ID, i.e., inlined) -->
                    <div v-if="isSingleRouteIdRest(rest)" class="flex items-center gap-3" @click.stop>
                      <div v-if="routeMetrics[rest.routeId]" class="rest-metrics-summary flex items-center gap-3 text-xs font-semibold bg-surface-50 dark:bg-surface-800/50 border border-surface-200 dark:border-surface-700 rounded-lg px-3 py-1">
                        <span class="text-surface-500" title="Total Exchanges"><i class="pi pi-sync text-[10px]"></i> {{ formatCount(routeMetrics[rest.routeId].exchangesTotal) }}</span>
                        <span v-if="routeMetrics[rest.routeId].exchangesFailed > 0" class="text-red-500" title="Failed Exchanges"><i class="pi pi-times-circle text-[10px]"></i> {{ formatCount(routeMetrics[rest.routeId].exchangesFailed) }}</span>
                        <span class="text-surface-600 dark:text-surface-300" title="Average processing time"><i class="pi pi-clock text-[10px]"></i> {{ routeMetrics[rest.routeId].meanProcessingTimeMs }}ms</span>
                        <span class="text-primary" title="Throughput"><i class="pi pi-bolt text-[10px]"></i> {{ parseFloat(routeMetrics[rest.routeId].throughput || 0).toFixed(1) }}/s</span>
                        <span v-if="routeMetrics[rest.routeId].exchangesInflight > 0" class="text-amber-500 flex items-center gap-1 animate-pulse" title="Inflight Exchanges">
                          <i class="pi pi-spin pi-spinner text-[10px]"></i> {{ routeMetrics[rest.routeId].exchangesInflight }}
                        </span>
                      </div>
                      
                      <div class="btn-group flex gap-2">
                        <Button variant="outlined" v-if="rest.status !== 'Started' && rest.status !== 'Suspended'"
                          icon="pi pi-play" severity="success" size="small" class="btn-xs btn-success" :label="t('common.start')"
                          @click="routeAction({ id: rest.routeId, originalId: rest.path }, 'start')" />
                        <Button variant="outlined" v-if="rest.status === 'Started'" icon="pi pi-stop" severity="warn" size="small"
                          class="btn-xs btn-warning" :label="t('common.stop')" @click="routeAction({ id: rest.routeId, originalId: rest.path }, 'stop')" />
                        <Button variant="outlined" v-if="rest.status === 'Started'" icon="pi pi-pause" severity="info" size="small"
                          class="btn-xs btn-info" :label="t('common.suspend')" @click="routeAction({ id: rest.routeId, originalId: rest.path }, 'suspend')" />
                        <Button variant="outlined" v-if="rest.status === 'Suspended'" icon="pi pi-play" severity="info" size="small"
                          class="btn-xs btn-info" :label="t('common.resume')" @click="routeAction({ id: rest.routeId, originalId: rest.path }, 'resume')" />
                        <Button variant="outlined" icon="pi pi-eye" severity="secondary" size="small" class="btn-xs btn-ghost"
                          :title="t('routes.tooltip.preview')" @click="viewSource({ id: rest.routeId, restParams: collectRestParams(rest) })" />
                      </div>
                    </div>
                  </div>

                  <!-- REST Verbs (HTTP Methods under the Path) -->
                  <Transition name="fade-collapse">
                    <div v-show="expandedRests.has(svc.serviceId + '-' + rest.path)" class="rest-verbs-body p-3 border-t flex flex-col gap-3">
                      <div v-for="verb in rest.verbs" :key="verb.method" class="verb-row flex flex-col gap-2 p-3 border rounded-lg bg-surface-card shadow-sm">
                        <div class="flex justify-between items-center flex-wrap gap-2">
                          <div class="flex items-center gap-3">
                            <Tag :severity="getVerbSeverity(verb.method)" :value="verb.method" class="font-mono text-xs font-bold px-2 py-0.5 rounded" />
                            <span class="font-mono text-xs text-surface-600 dark:text-surface-300 font-semibold">
                              <i class="pi pi-arrow-right text-[10px] text-surface-400"></i> target: <strong>{{ verb.toUri || '-' }}</strong>
                            </span>
                            <div v-if="verb.nodeStates && verb.nodeStates.length > 0" 
                                 class="status-dots-wrapper" 
                                 @mouseenter="showTooltip($event, verb.nodeStates)" 
                                 @mouseleave="hideTooltip" 
                                 @click.stop>
                              <div class="status-dots">
                                <span v-for="node in verb.nodeStates" 
                                      :key="node.instanceId" 
                                      class="status-dot" 
                                      :class="node.status.toLowerCase()">
                                </span>
                              </div>
                            </div>
                            <StatusBadge v-else :status="verb.status" class="text-xs" />
                          </div>
                          
                          <div class="flex items-center gap-2" @click.stop>
                            <span v-if="verb.id" class="text-xs font-mono text-surface-400 dark:text-surface-500 mr-2">ID: {{ verb.id }}</span>
                            
                            <div v-if="routeMetrics[verb.routeId]" class="verb-metrics flex items-center gap-2 mr-2 text-[11px] bg-surface-50 dark:bg-surface-800 border border-surface-200 dark:border-surface-700 rounded px-2 py-0.5 font-semibold">
                              <span class="text-surface-500" title="Total"><i class="pi pi-sync text-[9px]"></i> {{ formatCount(routeMetrics[verb.routeId].exchangesTotal) }}</span>
                              <span v-if="routeMetrics[verb.routeId].exchangesFailed > 0" class="text-red-500" title="Failed"><i class="pi pi-times-circle text-[9px]"></i> {{ formatCount(routeMetrics[verb.routeId].exchangesFailed) }}</span>
                              <span class="text-surface-600 dark:text-surface-300" title="Avg Time"><i class="pi pi-clock text-[9px]"></i> {{ routeMetrics[verb.routeId].meanProcessingTimeMs }}ms</span>
                              <span class="text-primary" title="Throughput"><i class="pi pi-bolt text-[9px]"></i> {{ parseFloat(routeMetrics[verb.routeId].throughput || 0).toFixed(1) }}/s</span>
                            </div>

                            <!-- Verb-level controls -->
                            <div class="btn-group flex gap-2">
                              <Button variant="outlined" v-if="verb.status !== 'Started' && verb.status !== 'Suspended'"
                                icon="pi pi-play" severity="success" size="small" class="btn-xs btn-success"
                                @click="routeAction({ id: verb.routeId, originalId: verb.id }, 'start')" />
                              <Button variant="outlined" v-if="verb.status === 'Started'" icon="pi pi-stop" severity="warn" size="small"
                                class="btn-xs btn-warning" @click="routeAction({ id: verb.routeId, originalId: verb.id }, 'stop')" />
                              <Button variant="outlined" v-if="verb.status === 'Started'" icon="pi pi-pause" severity="info" size="small"
                                class="btn-xs btn-info" @click="routeAction({ id: verb.routeId, originalId: verb.id }, 'suspend')" />
                              <Button variant="outlined" v-if="verb.status === 'Suspended'" icon="pi pi-play" severity="info" size="small"
                                class="btn-xs btn-info" @click="routeAction({ id: verb.routeId, originalId: verb.id }, 'resume')" />
                              <Button variant="outlined" icon="pi pi-eye" severity="secondary" size="small" class="btn-xs btn-ghost"
                                :title="t('routes.tooltip.preview')" @click="viewSource({ id: verb.routeId, restParams: verb.params })" />
                              <!--
                              <Button variant="outlined" icon="pi pi-trash" severity="danger" size="small" class="btn-xs btn-danger"
                                @click="routeAction({ id: verb.routeId, originalId: verb.id }, 'delete')" />
                              -->
                            </div>
                          </div>
                        </div>

                        <p v-if="verb.description" class="text-sm text-surface-500 mt-1">{{ verb.description }}</p>
                        
                        <div v-if="verb.consumes || verb.produces" class="flex gap-4 mt-1 text-xs text-surface-400 dark:text-surface-500 font-mono">
                          <span v-if="verb.consumes"><i class="pi pi-sign-in text-[10px]"></i> Consumes: <code>{{ verb.consumes }}</code></span>
                          <span v-if="verb.produces"><i class="pi pi-sign-out text-[10px]"></i> Produces: <code>{{ verb.produces }}</code></span>
                        </div>

                        <!-- HTTP parameters definition -->
                        <div v-if="verb.params && verb.params.length > 0" class="mt-3">
                          <h5 class="text-[10px] font-bold text-surface-400 mb-1.5 uppercase tracking-wider">API Parameters</h5>
                          <div class="overflow-x-auto border rounded-lg">
                            <table class="w-full text-left border-collapse">
                              <thead>
                                <tr class="bg-surface-100 dark:bg-surface-800 text-[10px] text-surface-600 dark:text-surface-400 border-b uppercase tracking-wider font-semibold">
                                  <th class="p-2">Name</th>
                                  <th class="p-2">Type</th>
                                  <th class="p-2">Data Type</th>
                                  <th class="p-2">Required</th>
                                  <th class="p-2">Description</th>
                                </tr>
                              </thead>
                              <tbody>
                                <tr v-for="param in verb.params" :key="param.name" class="border-b last:border-0 text-xs text-surface-700 dark:text-surface-300">
                                  <td class="p-2 font-semibold font-mono">{{ param.name }}</td>
                                  <td class="p-2"><Tag :severity="param.type === 'body' ? 'info' : 'secondary'" :value="param.type" class="text-[10px]" /></td>
                                  <td class="p-2"><code class="text-xs bg-surface-200 dark:bg-surface-700 px-2 py-0.5 rounded font-mono">{{ param.dataType || 'string' }}</code></td>
                                  <td class="p-2"><Tag :severity="param.required ? 'danger' : 'success'" :value="param.required ? 'Required' : 'Optional'" class="text-[10px] px-1.5" /></td>
                                  <td class="p-2 text-surface-500 text-xs">{{ param.description || '-' }}</td>
                                </tr>
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </div>
                    </div>
                  </Transition>
                </div>
              </div>
            </div>

            <!-- ⚙️ REGULAR ROUTES SECTION -->
            <div v-if="svc.routes && svc.routes.length > 0" class="routes-section">
              <h4 class="text-xs font-bold uppercase tracking-wider text-surface-400 dark:text-surface-500 mb-3 flex items-center gap-2">
                <i class="pi pi-cog text-primary"></i> Regular Routes
              </h4>  
              <div class="border rounded-md shadow-sm p-[0.7px]">
              <DataTable :value="svc.routes" responsiveLayout="scroll" class="prime-datatable">
                <Column field="id" :header="t('routes.table.id')">
                  <template #body="slotProps">
                    <div class="route-id-cell flex items-center gap-1">
                      <span class="text-sm font-semibold text-surface-700 dark:text-surface-300">{{ routeDisplayId(slotProps.data) }}</span>
                      <Button variant="outlined" v-if="slotProps.data.description" icon="pi pi-info-circle" class="btn-ghost"
                        style="padding: 2px; width: 22px; height: 22px; min-height: auto; border: 0;"
                        :title="slotProps.data.description" @click.stop="showDescription(slotProps.data)" />
                    </div>
                  </template>
                </Column>

                <Column field="status" :header="t('routes.table.status')">
                  <template #body="slotProps">
                    <div v-if="slotProps.data.nodeStates && slotProps.data.nodeStates.length > 0" 
                         class="status-dots-wrapper" 
                         @mouseenter="showTooltip($event, slotProps.data.nodeStates)" 
                         @mouseleave="hideTooltip" 
                         @click.stop>
                      <div class="status-dots">
                        <span v-for="node in slotProps.data.nodeStates" 
                              :key="node.instanceId" 
                              class="status-dot" 
                              :class="node.status.toLowerCase()">
                        </span>
                      </div>
                    </div>
                    <StatusBadge v-else :status="slotProps.data.status" />
                  </template>
                </Column>

                <Column field="sourceUri" :header="t('routes.table.source')">
                  <template #body="slotProps">
                    <span class="source-uri-cell font-mono text-xs max-w-xs block truncate text-surface-600 dark:text-surface-400" :title="slotProps.data.sourceUri">
                      {{ slotProps.data.sourceUri || '-' }}
                    </span>
                  </template>
                </Column>

                <Column field="activeVersion" :header="t('routes.table.version')">
                  <template #body="slotProps">
                    <span class="version-tag">v{{ slotProps.data.activeVersion || '?' }}</span>
                  </template>
                </Column>

                <Column :header="t('routes.table.metrics') || 'Metrics'">
                  <template #body="slotProps">
                    <div v-if="routeMetrics[slotProps.data.id]" class="route-metrics-cell flex items-center gap-4 text-xs">
                      <div class="metric-group flex flex-col" :title="'Exchanges: Total / Failed'">
                        <span class="metric-label text-[10px] uppercase font-bold text-surface-400 dark:text-surface-500">Exchanges</span>
                        <div class="flex items-center gap-1 font-semibold">
                          <span class="text-surface-700 dark:text-surface-300 font-mono">{{ formatCount(routeMetrics[slotProps.data.id].exchangesTotal) }}</span>
                          <span v-if="routeMetrics[slotProps.data.id].exchangesFailed > 0" class="text-red-500 font-mono text-[11px] font-semibold bg-red-500/10 px-1 rounded">
                            / {{ formatCount(routeMetrics[slotProps.data.id].exchangesFailed) }}
                          </span>
                        </div>
                      </div>

                      <div class="metric-group flex flex-col" :title="'Average / Last processing time'">
                        <span class="metric-label text-[10px] uppercase font-bold text-surface-400 dark:text-surface-500">Avg / Last</span>
                        <div class="flex items-center gap-1 font-mono text-surface-600 dark:text-surface-400 font-semibold">
                          <span>{{ routeMetrics[slotProps.data.id].meanProcessingTimeMs }}ms</span>
                          <span class="text-[10px] text-surface-400">/ {{ routeMetrics[slotProps.data.id].lastProcessingTimeMs }}ms</span>
                        </div>
                      </div>

                      <div class="metric-group flex flex-col" :title="'Throughput (TPS)'">
                        <span class="metric-label text-[10px] uppercase font-bold text-surface-400 dark:text-surface-500">TPS</span>
                        <span class="font-mono text-primary font-semibold">
                          {{ parseFloat(routeMetrics[slotProps.data.id].throughput || 0).toFixed(2) }}
                        </span>
                      </div>

                      <div v-if="routeMetrics[slotProps.data.id].exchangesInflight > 0" class="metric-group flex flex-col items-center">
                        <span class="metric-label text-[10px] uppercase font-bold text-surface-400 dark:text-surface-500">Active</span>
                        <span class="font-mono font-bold bg-amber-500/20 text-amber-500 px-2 py-0.5 rounded-full flex items-center gap-1 animate-pulse">
                          <i class="pi pi-spin pi-spinner text-[10px]"></i>
                          {{ routeMetrics[slotProps.data.id].exchangesInflight }}
                        </span>
                      </div>
                    </div>
                    <span v-else class="text-xs text-surface-400 italic font-mono">-</span>
                  </template>
                </Column>

                <Column field="deployedAt" :header="t('routes.table.deployed_at')">
                  <template #body="slotProps">
                    <span class="muted deploy-time-cell text-xs">{{ fmtDate(slotProps.data.deployedAt) }}</span>
                  </template>
                </Column>

                <Column :header="t('routes.table.actions')">
                  <template #body="slotProps">
                    <div class="btn-group">
                      <Button variant="outlined" v-if="slotProps.data.status !== 'Started' && slotProps.data.status !== 'Suspended'"
                        icon="pi pi-play" severity="success" size="small" class="btn-xs btn-success" :label="t('common.start')"
                        @click="routeAction(slotProps.data, 'start')" />
                      <Button variant="outlined" v-if="slotProps.data.status === 'Started'" icon="pi pi-stop" severity="warn" size="small"
                        class="btn-xs btn-warning" :label="t('common.stop')" @click="routeAction(slotProps.data, 'stop')" />
                      <Button variant="outlined" v-if="slotProps.data.status === 'Started'" icon="pi pi-pause" severity="info" size="small"
                        class="btn-xs btn-info" :label="t('common.suspend')" @click="routeAction(slotProps.data, 'suspend')" />
                      <Button variant="outlined" v-if="slotProps.data.status === 'Suspended'" icon="pi pi-play" severity="info" size="small"
                        class="btn-xs btn-info" :label="t('common.resume')" @click="routeAction(slotProps.data, 'resume')" />
                      <Button variant="outlined" icon="pi pi-eye" severity="secondary" size="small" class="btn-xs btn-ghost"
                        :title="t('routes.tooltip.preview')" @click="viewSource(slotProps.data)" />
                      <!--
                      <Button variant="outlined" icon="pi pi-trash" severity="danger" size="small" class="btn-xs btn-danger"
                        @click="routeAction(slotProps.data, 'delete')" />
                      -->
                    </div>
                  </template>
                </Column>
              </DataTable>
              </div>            
            </div>

          </div>
        </Transition>
      </div>
    </div>

    <!-- Flowchart / Source preview modal -->
    <Dialog v-model:visible="isModalOpen" maximizable modal :header="t('modal.preview_title') || 'Source'"
      :style="{ width: '55rem' }" :breakpoints="{ '1199px': '75vw', '575px': '90vw' }">
      <Tabs v-model:value="currentTab">
        <TabList>
          <Tab value="source">{{ t('modal.preview_title') || 'source' }}</Tab>
          <Tab value="diagram">{{ t('modal.tab_diagram') || 'Diagram' }}</Tab>
          <Tab v-if="modalRoute?.restParams && modalRoute.restParams.length > 0" value="api">
            {{ t('modal.tab_api') || 'REST API' }}
          </Tab>
          <Tab value="metrics">{{ t('modal.tab_metrics') || 'Runtime Metrics' }}</Tab>
        </TabList>
        <TabPanels>
          <TabPanel value="source">
            <pre data-src="myfile.js" data-download-link data-download-link-label="Download this file"
              class="line-numbers" style="background: #1d1f21;"><code ref="yamlCodeBlock" class="language-yaml">{{
                sourceContent || t('modal.diagram_unavailable') }}</code>
        </pre>
          </TabPanel>
          <TabPanel value="diagram">
            <div class="diagram-toolbar"
              style="display: flex; gap: 0.5rem; margin-bottom: 0.5rem; align-items: center;">
              <Button icon="pi pi-minus-circle" severity="secondary" @click="changeZoom(-0.15)" variant="text" />
              <span class="muted" style="font-size: 0.85rem; min-width: 45px; text-align: center;">{{
                Math.round(diagramZoom * 100) }}%</span>
              <Button icon="pi pi-plus-circle" severity="secondary" @click="changeZoom(0.15)" variant="text" />
              <Button icon="pi pi-refresh" severity="secondary" @click="resetDiagramTransform" variant="text" />
              <Button icon="pi pi-expand" severity="secondary" @click="zoomFit" variant="text" />
            </div>

            <div class="mermaid-canvas" id="vue-mermaid-canvas" @wheel="handleWheel" @mousedown="handleMouseDown"
              :class="{ panning: isDragging }"
              style="border: 1px solid var(--border); border-radius: 8px; background: var(--bg); overflow: hidden; min-height:60vh;  position: relative; cursor: grab;">
              <div class="mermaid-canvas-inner" id="vue-mermaid-canvas-inner"
                :style="{ transform: `translate(${diagramPanX}px, ${diagramPanY}px) scale(${diagramZoom})`, transformOrigin: '0 0', position: 'absolute' }">
                <pre class="mermaid" id="vue-mermaid-render-area"
                  style="margin:0; background: transparent; border:none;">
            </pre>
              </div>
            </div>
          </TabPanel>
          <TabPanel v-if="modalRoute?.restParams && modalRoute.restParams.length > 0" value="api">
            <DataTable :value="modalRoute.restParams" responsiveLayout="scroll" class="prime-datatable" style="margin-top: 0.5rem">
              <Column field="name" :header="t('routes.table.param_name') || 'Name'">
                <template #body="slotProps">
                  <strong>{{ slotProps.data.name }}</strong>
                </template>
              </Column>
              <Column field="type" :header="t('routes.table.param_type') || 'Type'">
                <template #body="slotProps">
                  <Tag :severity="slotProps.data.type === 'body' ? 'info' : 'secondary'" :value="slotProps.data.type" />
                </template>
              </Column>
              <Column field="dataType" :header="t('routes.table.param_datatype') || 'Data Type'">
                <template #body="slotProps">
                  <code style="font-family: monospace; background: var(--surface2); padding: 2px 6px; border-radius: 4px;">{{ slotProps.data.dataType || 'string' }}</code>
                </template>
              </Column>
              <Column field="required" :header="t('routes.table.param_required') || 'Required'">
                <template #body="slotProps">
                  <Tag :severity="slotProps.data.required ? 'danger' : 'success'" :value="slotProps.data.required ? t('routes.table.param_required') : 'Optional'" />
                </template>
              </Column>
              <Column field="description" :header="t('routes.table.param_description') || 'Description'">
                <template #body="slotProps">
                  <span>{{ slotProps.data.description || '-' }}</span>
                </template>
              </Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="metrics">
            <div v-if="routeMetrics[modalRoute?.id]" class="metrics-details-panel p-4 flex flex-col gap-6">
              <!-- Grid of main KPI cards -->
              <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div class="metric-card p-3 border rounded-xl bg-surface-50 dark:bg-surface-800/30 flex flex-col overflow-hidden">
                  <span class="text-xs text-surface-400 dark:text-surface-500 font-bold uppercase tracking-wider">Total Exchanges</span>
                  <span class="text-2xl font-bold font-mono text-surface-800 dark:text-surface-100 mt-1 break-all">
                    {{ formatFullNumber(routeMetrics[modalRoute.id].exchangesTotal) }}
                  </span>
                </div>
                <div class="metric-card p-3 border rounded-xl bg-surface-50 dark:bg-surface-800/30 flex flex-col overflow-hidden">
                  <span class="text-xs text-surface-400 dark:text-surface-500 font-bold uppercase tracking-wider">Completed</span>
                  <span class="text-2xl font-bold font-mono text-green-500 mt-1 break-all">
                    {{ formatFullNumber(routeMetrics[modalRoute.id].exchangesSucceeded) }}
                  </span>
                </div>
                <div class="metric-card p-3 border rounded-xl bg-surface-50 dark:bg-surface-800/30 flex flex-col overflow-hidden">
                  <span class="text-xs text-surface-400 dark:text-surface-500 font-bold uppercase tracking-wider">Failed</span>
                  <span class="text-2xl font-bold font-mono text-red-500 mt-1 break-all">
                    {{ formatFullNumber(routeMetrics[modalRoute.id].exchangesFailed) }}
                  </span>
                </div>
                <div class="metric-card p-3 border rounded-xl bg-surface-50 dark:bg-surface-800/30 flex flex-col overflow-hidden">
                  <span class="text-xs text-surface-400 dark:text-surface-500 font-bold uppercase tracking-wider">Inflight</span>
                  <span class="text-2xl font-bold font-mono text-amber-500 mt-1 break-all" :class="{ 'animate-pulse': routeMetrics[modalRoute.id].exchangesInflight > 0 }">
                    {{ formatFullNumber(routeMetrics[modalRoute.id].exchangesInflight) }}
                  </span>
                </div>
              </div>

              <!-- Processing Time stats & Throughput/Load -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <!-- Processing Time Stats -->
                <div class="border rounded-xl p-4 bg-surface-card flex flex-col gap-3">
                  <h4 class="text-sm font-bold text-surface-800 dark:text-surface-200 border-b pb-2 flex items-center gap-2">
                    <i class="pi pi-clock text-primary"></i> Processing Times (ms)
                  </h4>
                  <div class="flex flex-col gap-2 font-mono text-sm">
                    <div class="flex justify-between">
                      <span class="text-surface-500">Mean Processing Time:</span>
                      <span class="font-bold">{{ routeMetrics[modalRoute.id].meanProcessingTimeMs }} ms</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-surface-500">Min Processing Time:</span>
                      <span class="font-bold">{{ routeMetrics[modalRoute.id].minProcessingTimeMs }} ms</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-surface-500">Max Processing Time:</span>
                      <span class="font-bold">{{ routeMetrics[modalRoute.id].maxProcessingTimeMs }} ms</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-surface-500">Last Processing Time:</span>
                      <span class="font-bold">{{ routeMetrics[modalRoute.id].lastProcessingTimeMs }} ms</span>
                    </div>
                  </div>
                </div>

                <!-- Load & Throughput -->
                <div class="border rounded-xl p-4 bg-surface-card flex flex-col gap-3">
                  <h4 class="text-sm font-bold text-surface-800 dark:text-surface-200 border-b pb-2 flex items-center gap-2">
                    <i class="pi pi-bolt text-primary"></i> Throughput & Load
                  </h4>
                  <div class="flex flex-col gap-2 font-mono text-sm">
                    <div class="flex justify-between">
                      <span class="text-surface-500">Current Throughput:</span>
                      <span class="font-bold text-primary">{{ parseFloat(routeMetrics[modalRoute.id].throughput || 0).toFixed(2) }} TPS</span>
                    </div>
                    <div class="flex justify-between" title="Exchanges per minute over 1-minute window">
                      <span class="text-surface-500">Load (1 min):</span>
                      <span class="font-bold">{{ routeMetrics[modalRoute.id].load01 }}</span>
                    </div>
                    <div class="flex justify-between" title="Exchanges per minute over 5-minute window">
                      <span class="text-surface-500">Load (5 min):</span>
                      <span class="font-bold">{{ routeMetrics[modalRoute.id].load05 }}</span>
                    </div>
                    <div class="flex justify-between" title="Exchanges per minute over 15-minute window">
                      <span class="text-surface-500">Load (15 min):</span>
                      <span class="font-bold">{{ routeMetrics[modalRoute.id].load15 }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="text-center p-8 text-surface-500 italic">
              {{ t('modal.metrics_unavailable') }}
            </div>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </Dialog>
    <Dialog v-model:visible="isDescModalOpen" modal header="Header">
      <template #header>
        <h3>{{ descModalTitle }}</h3>
      </template>
      <p class="muted">ID: {{ modalRoute?.id }}</p>
      <pre class="code-preview">{{ descModalContent }}</pre>
    </Dialog>
    <!-- Shared Dynamic Glass Tooltip -->
    <div class="glass-tooltip" :style="tooltipStyle">
      <div class="tooltip-header">Cluster Status Matrix</div>
      <div v-for="node in tooltipNodes" :key="node.instanceId" class="tooltip-row">
        <div class="tooltip-row-header">
          <span class="node-icon">🖥️</span>
          <span class="node-name">{{ node.instanceId }}</span>
          <span class="status-tag-mini" :class="node.status.toLowerCase()">{{ node.status }}</span>
        </div>
        <div v-if="node.errorMessage" class="error-detail">{{ node.errorMessage }}</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.mermaid-canvas.panning {
  cursor: grabbing !important;
}

/* Optimize Prism YAML UI */
.code-preview code[class*="language-"] {
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 0.9rem;
  background: transparent;
  padding: 1rem;
  display: block;
  overflow: auto;
}

.prime-datatable :deep(th) {
  font-family: var(--font-ui);
  text-transform: uppercase;
  font-size: 0.78rem;
  color: var(--text-muted);
}

/* .prime-datatable :deep(tbody tr:hover) {
  position: relative;
  z-index: 8;
} */
.p-datatable .p-datatable-table-container{
  overflow: visible !important;
}

/* Round the corner cells of the inner table to avoid bleeding over the rounded-lg border */
/* .prime-datatable :deep(thead tr:first-child th:first-child) {
  border-top-left-radius: 0.5rem;
}
.prime-datatable :deep(thead tr:first-child th:last-child) {
  border-top-right-radius: 0.5rem;
}
.prime-datatable :deep(tbody tr:last-child td:first-child) {
  border-bottom-left-radius: 0.5rem;
}
.prime-datatable :deep(tbody tr:last-child td:last-child) {
  border-bottom-right-radius: 0.5rem;
} */



/* Collapsible card animations */
.fade-collapse-enter-active,
.fade-collapse-leave-active {
  transition: max-height 0.3s ease-out, opacity 0.25s ease;
  max-height: 2000px;
  overflow: hidden;
}

.fade-collapse-enter-from,
.fade-collapse-leave-to {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
}
</style>