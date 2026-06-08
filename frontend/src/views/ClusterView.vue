<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { dashboardApi } from '../services/api.js';
import { useToasts } from '../composables/useToasts.js';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Tag from 'primevue/tag';

const { toast } = useToasts();
const nodes = ref([]);
const loading = ref(false);
let pollingTimer = null;

// ── Computed stats ──────────────────────────────────────────────────────────
const totalNodes   = computed(() => nodes.value.length);
const onlineNodes  = computed(() => nodes.value.filter(n => n.status === 'ONLINE').length);
const healthRate   = computed(() =>
  totalNodes.value ? Math.round((onlineNodes.value / totalNodes.value) * 100) : 0
);

// ── Data fetching ───────────────────────────────────────────────────────────
async function fetchNodes() {
  try {
    nodes.value = await dashboardApi.cluster.nodes();
  } catch (err) {
    toast('Không thể tải dữ liệu cluster: ' + err.message, 'error');
  }
}

async function initialLoad() {
  loading.value = true;
  await fetchNodes();
  loading.value = false;
}

onMounted(() => {
  initialLoad();
  pollingTimer = setInterval(fetchNodes, 5000);
});

onUnmounted(() => {
  if (pollingTimer) clearInterval(pollingTimer);
});

// ── Formatters ──────────────────────────────────────────────────────────────
function formatLastSeen(ts) {
  if (!ts) return '-';
  const diff = Math.floor((Date.now() - new Date(ts).getTime()) / 1000);
  if (diff < 5)  return 'Just now';
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return new Date(ts).toLocaleTimeString();
}

function formatUptime(seconds) {
  if (!seconds && seconds !== 0) return '-';
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function nodeTagSeverity(status) {
  return status === 'ONLINE' ? 'success' : 'secondary';
}
</script>

<template>
  <div class="cluster-view">

    <!-- ── KPI Stat Cards ─────────────────────────────────────────────────── -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon">🖥️</div>
        <div class="stat-body">
          <div class="stat-label">Total Nodes</div>
          <div class="stat-value">{{ totalNodes }}</div>
        </div>
      </div>

      <div class="stat-card stat-card--online">
        <div class="stat-icon">
          <span class="pulse-dot"></span>
        </div>
        <div class="stat-body">
          <div class="stat-label">Active Nodes</div>
          <div class="stat-value stat-value--green">{{ onlineNodes }}</div>
        </div>
      </div>

      <div class="stat-card" :class="healthRate === 100 ? 'stat-card--perfect' : healthRate >= 50 ? 'stat-card--warn' : 'stat-card--danger'">
        <div class="stat-icon">🏥</div>
        <div class="stat-body">
          <div class="stat-label">Cluster Health</div>
          <div class="stat-value" :class="healthRate === 100 ? 'stat-value--green' : healthRate >= 50 ? 'stat-value--yellow' : 'stat-value--red'">
            {{ healthRate }}%
          </div>
        </div>
        <!-- Health progress bar -->
        <div class="health-bar-track">
          <div class="health-bar-fill"
               :style="{ width: healthRate + '%' }"
               :class="healthRate === 100 ? 'fill--green' : healthRate >= 50 ? 'fill--yellow' : 'fill--red'">
          </div>
        </div>
      </div>
    </div>

    <!-- ── Nodes DataTable ────────────────────────────────────────────────── -->
    <div class="table-card">
      <div class="table-header">
        <div class="table-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/>
            <rect x="2" y="14" width="20" height="8" rx="2" ry="2"/>
            <line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/>
          </svg>
          Active Cluster Instances
        </div>
        <div class="table-subtitle">Auto-refresh every 5s</div>
      </div>

      <DataTable
        :value="nodes"
        :loading="loading"
        responsiveLayout="scroll"
        class="cluster-table"
        :rowClass="row => row.isCurrent ? 'row-current' : ''"
      >
        <!-- Instance ID -->
        <Column field="instanceId" header="Instance ID" style="min-width:180px">
          <template #body="{ data }">
            <div class="instance-cell">
              <span class="instance-id">{{ data.instanceId }}</span>
              <span v-if="data.isCurrent" class="current-badge">This node</span>
            </div>
          </template>
        </Column>

        <!-- Group -->
        <Column field="groupName" header="Group" style="min-width:100px">
          <template #body="{ data }">
            <span class="group-label">{{ data.groupName || 'default' }}</span>
          </template>
        </Column>

        <!-- IP -->
        <Column field="ipAddress" header="IP Address" style="min-width:130px">
          <template #body="{ data }">
            <span class="mono-text">{{ data.ipAddress }}</span>
          </template>
        </Column>

        <!-- Port -->
        <Column field="port" header="Port" style="min-width:70px">
          <template #body="{ data }">
            <span class="mono-text port-text">:{{ data.port }}</span>
          </template>
        </Column>

        <!-- Status -->
        <Column field="status" header="Status" style="min-width:110px">
          <template #body="{ data }">
            <div class="status-cell">
              <span v-if="data.status === 'ONLINE'" class="pulse-dot pulse-dot--sm"></span>
              <Tag
                :severity="nodeTagSeverity(data.status)"
                :value="data.status"
                class="status-tag"
              />
            </div>
          </template>
        </Column>

        <!-- Last Seen -->
        <Column field="lastSeen" header="Last Heartbeat" style="min-width:140px">
          <template #body="{ data }">
            <span class="mono-text muted-text">{{ formatLastSeen(data.lastSeen) }}</span>
          </template>
        </Column>

        <!-- Uptime -->
        <Column field="uptimeSeconds" header="Uptime" style="min-width:100px">
          <template #body="{ data }">
            <span class="mono-text">{{ formatUptime(data.uptimeSeconds) }}</span>
          </template>
        </Column>
      </DataTable>

      <!-- Empty state -->
      <div v-if="!loading && nodes.length === 0" class="empty-state">
        <div class="empty-icon">🖥️</div>
        <div class="empty-text">No cluster nodes detected</div>
        <div class="empty-sub">Nodes will appear here once they register their heartbeat</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cluster-view {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* ── Stat Cards ─────────────────────────────────────────────────────────── */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.stat-card {
  background: rgba(17, 24, 39, 0.55);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 1.4rem 1.6rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  position: relative;
  overflow: hidden;
  transition: border-color 0.3s, box-shadow 0.3s;
}
.stat-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.03) 0%, transparent 60%);
  pointer-events: none;
}
.stat-card:hover {
  border-color: rgba(255,255,255,0.15);
  box-shadow: 0 4px 24px rgba(0,0,0,0.25);
}
.stat-card--online  { border-color: rgba(34,197,94,0.25); }
.stat-card--perfect { border-color: rgba(34,197,94,0.25); }
.stat-card--warn    { border-color: rgba(234,179,8,0.25); }
.stat-card--danger  { border-color: rgba(239,68,68,0.25); }

.stat-icon {
  font-size: 1.5rem;
  display: flex;
  align-items: center;
}
.stat-body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.stat-label {
  font-size: 0.78rem;
  color: var(--surface-400, #94a3b8);
  text-transform: uppercase;
  letter-spacing: 0.07em;
  font-weight: 600;
}
.stat-value {
  font-size: 2.2rem;
  font-weight: 800;
  line-height: 1;
  color: var(--surface-0, #f8fafc);
  font-variant-numeric: tabular-nums;
}
.stat-value--green  { color: hsl(142, 70%, 45%); }
.stat-value--yellow { color: hsl(45, 90%, 52%); }
.stat-value--red    { color: hsl(350, 75%, 55%); }

/* Health bar */
.health-bar-track {
  height: 4px;
  background: rgba(255,255,255,0.08);
  border-radius: 99px;
  overflow: hidden;
  margin-top: 0.5rem;
}
.health-bar-fill {
  height: 100%;
  border-radius: 99px;
  transition: width 0.6s cubic-bezier(.4,0,.2,1);
}
.fill--green  { background: hsl(142, 70%, 45%); }
.fill--yellow { background: hsl(45, 90%, 52%); }
.fill--red    { background: hsl(350, 75%, 55%); }

/* ── Pulse dot animation ────────────────────────────────────────────────── */
@keyframes pulse-breathing {
  0%   { transform: scale(0.92); box-shadow: 0 0 0 0 rgba(34,197,94,0.7); }
  70%  { transform: scale(1);    box-shadow: 0 0 0 7px rgba(34,197,94,0); }
  100% { transform: scale(0.92); box-shadow: 0 0 0 0 rgba(34,197,94,0); }
}
.pulse-dot {
  width: 14px;
  height: 14px;
  background: hsl(142, 70%, 45%);
  border-radius: 50%;
  display: inline-block;
  animation: pulse-breathing 2s infinite;
}
.pulse-dot--sm {
  width: 8px;
  height: 8px;
  margin-right: 6px;
  flex-shrink: 0;
}

/* ── Table card ─────────────────────────────────────────────────────────── */
.table-card {
  background: rgba(17, 24, 39, 0.55);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 1.2rem 1.5rem 0.75rem;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.table-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1rem;
  font-weight: 700;
  color: var(--surface-0, #f8fafc);
}
.table-subtitle {
  font-size: 0.75rem;
  color: var(--surface-400, #94a3b8);
}

/* PrimeVue table overrides */
.cluster-table :deep(.p-datatable-thead th) {
  background: transparent !important;
  border-color: rgba(255,255,255,0.06) !important;
  color: var(--surface-300, #cbd5e1) !important;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding: 0.65rem 1rem;
}
.cluster-table :deep(.p-datatable-tbody td) {
  border-color: rgba(255,255,255,0.04) !important;
  padding: 0.75rem 1rem;
  font-size: 0.875rem;
}
.cluster-table :deep(.p-datatable-tbody tr:hover td) {
  background: rgba(255,255,255,0.03) !important;
}
.cluster-table :deep(.row-current td) {
  background: rgba(34,197,94,0.04) !important;
}

/* Cell styles */
.instance-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.instance-id {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--surface-0, #f8fafc);
}
.current-badge {
  font-size: 0.65rem;
  background: rgba(34,197,94,0.18);
  color: hsl(142, 70%, 50%);
  border: 1px solid rgba(34,197,94,0.3);
  border-radius: 99px;
  padding: 1px 8px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.group-label {
  font-size: 0.8rem;
  color: var(--surface-300, #cbd5e1);
  background: rgba(255,255,255,0.07);
  padding: 2px 8px;
  border-radius: 6px;
}
.mono-text {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.82rem;
}
.port-text { color: hsl(200, 80%, 60%); }
.muted-text { color: var(--surface-400, #94a3b8); }

.status-cell {
  display: flex;
  align-items: center;
}
.status-tag {
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-weight: 700;
}

/* Empty state */
.empty-state {
  padding: 3rem 1rem;
  text-align: center;
}
.empty-icon { font-size: 2.5rem; margin-bottom: 0.75rem; }
.empty-text { font-size: 1rem; font-weight: 600; color: var(--surface-200, #e2e8f0); margin-bottom: 0.4rem; }
.empty-sub  { font-size: 0.82rem; color: var(--surface-400, #94a3b8); }
</style>
