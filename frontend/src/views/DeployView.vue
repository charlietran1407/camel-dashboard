<script setup>
import { computed, onMounted, ref, watch, nextTick } from 'vue';
import { fmtDate } from '../composables/useFormatters.js';
import { useI18n } from '../composables/useI18n.js';
import { useToasts } from '../composables/useToasts.js';
import { useConfirm } from 'primevue/useconfirm';
import { dashboardApi } from '../services/api.js';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();
const confirm = useConfirm();
const services = ref([]);
const versions = ref([]);
const selectedServiceId = ref('');
const deployLogs = ref([]);
const deployingVersionId = ref(null);

const isPreviewOpen = ref(false);
const previewTitle = ref('');
const previewContent = ref('');
const yamlCodeBlock = ref(null);

const isMessageOpen = ref(false);
const messageTitle = ref('');
const messageContent = ref('');
const selectedLog = ref(null);

const isWarningsOpen = ref(false);
const warningsTitle = ref('');
const activeWarnings = ref([]);

const serviceNames = computed(() => Object.fromEntries(services.value.map(service => [service.id, service.name])));
const groupedVersions = computed(() => {
  const grouped = {};
  versions.value.forEach(version => {
    (grouped[version.serviceId || 'Unknown Service'] ||= []).push(version);
  });
  return grouped;
});


async function load() {
  try {
    services.value = await dashboardApi.services.list();
    versions.value = selectedServiceId.value
      ? await dashboardApi.versions.byService(selectedServiceId.value)
      : await dashboardApi.versions.list();
    await loadLogs();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}
async function previewVersion(version) {
  try {
    previewTitle.value = `${t('modal.preview_title')}: ${version.fileName} (v${version.version})`;
    previewContent.value = '';
    isPreviewOpen.value = true;
    const detail = await dashboardApi.versions.getContent(version.id);
    previewContent.value = detail.content || '';
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

function showMessage(log) {
  selectedLog.value = log;
  const sName = serviceNames.value[log.target] || log.target || '';
  messageTitle.value = `${t('deploy.message')} - ${sName} (v${log.version || ''})`;
  messageContent.value = log.message || '';
  isMessageOpen.value = true;
}

async function loadLogs() {
  try {
    deployLogs.value = await dashboardApi.logs.list('DEPLOY');
  } catch (error) {
    console.error('Failed to load deployment logs:', error);
  }
}

async function clearDeployHistory() {
  confirm.require({
    message: t('deploy.clear_history_confirm'),
    header: t('common.confirm_delete') || 'Xác nhận xóa',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: t('common.cancel') || 'Hủy',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: t('common.delete') || 'Xóa',
      severity: 'danger',
      outlined: true
    },
    accept: async () => {
      try {
        await dashboardApi.logs.clear('DEPLOY');
        toast(t('toast.clear_deploy_logs_success'), 'success');
        await loadLogs();
      } catch (error) {
        toast(t('common.error') + error.message, 'error');
      }
    }
  });
}

function quickDeploy(version) {
  confirm.require({
    message: t('common.confirm_deploy'),
    header: t('common.confirm_deploy') || 'Xác nhận triển khai',
    icon: 'pi pi-send',
    rejectProps: {
      label: t('common.cancel') || 'Hủy',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: t('common.deploy_action') || 'Triển khai',
      severity: 'success',
      outlined: true
    },
    accept: async () => {
      deployingVersionId.value = version.id;
      try {
        const result = await dashboardApi.routes.deploy(version.id);
        if (result && result.status === 'RESTART_REQUIRED') {
          toast(result.message, 'warning', 15000);
        } else {
          toast(t('toast.deploy_success', result.routeId), 'success');
        }
        await load();
      } catch (error) {
        await loadLogs();
        if (error.validationResult && error.validationResult.errors) {
          const details = error.validationResult.errors.map(e => {
            const key = `validation.${e.code}`;
            const translated = t(key, ...(e.args || []));
            const formatted = translated === key ? e.message : translated;
            return `${formatted}${e.location ? ` (${e.location})` : ''}`;
          }).join('\n');
          toast(`${t('validation.pre_deploy_failed')}:\n${details}`, 'error');
        } else {
          toast(t('common.error') + error.message, 'error');
        }
      } finally {
        deployingVersionId.value = null;
      }
    }
  });
}

async function toggleAutoRestore(version) {
  try {
    await dashboardApi.versions.setAutoRestore(version.id, !version.autoRestore);
    toast(t('toast.active_success'), 'success');
    await load();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

async function deleteVersion(version) {
  confirm.require({
    message: t('common.confirm_delete_version'),
    header: t('common.confirm_delete') || 'Xác nhận xóa',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: t('common.cancel') || 'Hủy',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: t('common.delete') || 'Xóa',
      severity: 'danger',
      outlined: true
    },
    accept: async () => {
      try {
        await dashboardApi.versions.delete(version.id);
        toast(t('toast.delete_version_success'), 'success');
        await load();
      } catch (error) {
        toast(t('common.error') + error.message, 'error');
      }
    }
  });
}

async function downloadVersion(version) {
  try {
    const detail = await dashboardApi.versions.getContent(version.id);
    const content = detail.content || '';
    const blob = new Blob([content], { type: 'text/yaml' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = version.fileName || `route_v${version.version}.yaml`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
    toast(t('toast.download_success'), 'success');
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

function showWarnings(version) {
  warningsTitle.value = `${t('versions.warnings_title')}: ${version.fileName} (v${version.version})`;
  activeWarnings.value = version.warnings || [];
  isWarningsOpen.value = true;
}

onMounted(load);
watch(() => props.refreshKey, load);
watch(selectedServiceId, load);
watch(previewContent, (content) => {
  if (content && window.Prism) {
    nextTick(() => {
      const el = yamlCodeBlock.value;
      if (el && el.classList.contains('language-yaml')) {
        el.removeAttribute('data-processed');
        try {
          window.Prism.highlightElement(el);
        } catch (e) {
          console.warn('Prism highlight failed:', e);
        }
      }
    });
  }
});
</script>

<template>
  <section class="page active">
    <div class="deploy-grid">
      <Card>
        <template #title>
          <h2>{{ t('nav.versions') }}</h2>
        </template>
        <template #content>
          <Select v-model="selectedServiceId" :options="services" optionValue="id" optionLabel="name"
            :placeholder="t('services.select_placeholder')" class="compact-select" />
         
            <div v-if="!Object.keys(groupedVersions).length" class="empty-state">{{ t('versions.empty') }}</div>
            <Accordion v-else :multiple="true">
              <AccordionPanel v-for="(items, serviceId) in groupedVersions" :key="serviceId" :value="serviceId">
                <AccordionHeader>
                  <span>{{ serviceNames[serviceId] || serviceId }}</span>
                  <span class="version-tag">{{ items.length }} {{ t('versions.count') }}</span>
                </AccordionHeader>
                <AccordionContent>
                  <div v-for="version in [...items].reverse()" :key="version.id" class="version-row">
                    <span class="version-tag">v{{ version.version }}</span>
                    <div class="flex flex-col gap-2">
                      <span v-if="version.active" class="version-active-tag">{{ t('common.deployed') }}</span>
                      <span v-if="version.autoRestore" class="version-auto-restore-tag">{{ t('versions.auto_restore')}}</span>
                    </div>
                    <div class="version-info">
                      <div class="version-filename" style="display: flex; align-items: center; gap: 0.5rem;">
                        {{ version.fileName }}
                        <Button 
                          v-if="version.warnings && version.warnings.length > 0"
                          severity="warn" 
                          icon="pi pi-exclamation-triangle" 
                          size="small" 
                          outlined
                          class="p-button-rounded warning-btn-pulse"
                          style="padding: 0; width: 1.3rem; height: 1.3rem; min-width: 1.3rem; font-size: 0.75rem;"
                          @click="showWarnings(version)"
                          :title="t('versions.warnings_count', [version.warnings.length])"
                        />
                      </div>
                      <div class="version-meta">{{ version.description || t('versions.no_desc') }} | {{
                        fmtDate(version.uploadedAt) }}</div>
                    </div>
                    <div class="btn-group">
                      <Button variant="outlined" severity="secondary" icon="pi pi-eye" size="small"
                        @click="previewVersion(version)" />
                      <Button variant="outlined" severity="secondary" icon="pi pi-download" size="small"
                        @click="downloadVersion(version)" :title="t('versions.download_tooltip')" />
                      <Button  variant="outlined" severity="success" icon="pi pi-send" size="small"
                        :loading="deployingVersionId === version.id" :disabled="deployingVersionId !== null"
                        @click="quickDeploy(version)" :label="t('common.deploy_action')" />
                      <Button variant="outlined" :severity="version.autoRestore ? 'success' : 'secondary'" size="small"
                        icon="pi pi-history"
                        :disabled="!version.deployedAt"
                        :title="!version.deployedAt ? t('versions.auto_restore_disabled_tooltip') : (version.autoRestore ? t('versions.disable_auto_restore') : t('versions.enable_auto_restore'))"
                        @click="toggleAutoRestore(version)" />
                      <Button variant="outlined" severity="danger" icon="pi pi-trash" size="small"
                        @click="deleteVersion(version)" />
                    </div>
                  </div>
                </AccordionContent>
              </AccordionPanel>
            </Accordion>
          </template>
      </Card>
      <Card>
        <template #title>
          <div class="flex items-center justify-between w-full"
            style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
            <h2>{{ t('deploy.history') }}</h2>
            <Button variant="outlined" v-if="deployLogs.length" icon="pi pi-trash" severity="danger" size="small"
              :label="t('deploy.clear_history')" @click="clearDeployHistory" />
          </div>
        </template>
        <template #content>
          <DataTable :value="deployLogs" responsiveLayout="scroll" class="prime-datatable mt-2" :paginator="true"
            :rows="10">
            <template #empty>
              <div class="empty-state">{{ t('deploy.history_empty') }}</div>
            </template>

            <Column field="status" :header="t('deploy.status')" style="width: 100px">
              <template #body="slotProps">
                <Tag :severity="slotProps.data.status === 'SUCCESS' ? 'success' : 'danger'"
                  :value="slotProps.data.status" />
              </template>
            </Column>

            <Column field="timestamp" :header="t('deploy.time')" style="width: 160px">
              <template #body="slotProps">
                <span class="deploy-time-cell text-xs">{{ fmtDate(slotProps.data.timestamp) }}</span>
              </template>
            </Column>

            <Column field="target" :header="t('deploy.service')" style="width: 110px">
              <template #body="slotProps">
                <span v-if="serviceNames[slotProps.data.target]" class="font-semibold">{{ serviceNames[slotProps.data.target] }}</span>
                <code v-else>{{ slotProps.data.target }}</code>
              </template>
            </Column>

            <Column field="version" :header="t('deploy.version')" style="width: 80px">
              <template #body="slotProps">
                <span class="version-tag">v{{ slotProps.data.version }}</span>
              </template>
            </Column>

            <Column field="fileName" :header="t('deploy.file_name')" style="width: 140px">
              <template #body="slotProps">
                <span class="text-xs">{{ slotProps.data.fileName }}</span>
              </template>
            </Column>

            <Column field="message" :header="t('deploy.message')" style="width: 80px; text-align: center;">
              <template #body="slotProps">
                <Button
                  v-if="slotProps.data.message"
                  icon="pi pi-exclamation-circle"
                  variant="text"
                  rounded
                  :severity="slotProps.data.status === 'SUCCESS' ? 'success' : 'danger'"
                  @click="showMessage(slotProps.data)"
                  :title="slotProps.data.message"
                />
                <span v-else>-</span>
              </template>
            </Column>
          </DataTable>
        </template>
      </Card>
    </div>
  </section>

  <Dialog v-model:visible="isPreviewOpen" maximizable modal :header="previewTitle" :style="{ width: '50rem' }"
    :breakpoints="{ '1199px': '75vw', '575px': '90vw' }">
    <pre class="line-numbers" style="margin: 0; background: #1d1f21;"><code ref="yamlCodeBlock" class="language-yaml">{{
      previewContent }}</code></pre>
  </Dialog>

  <Dialog v-model:visible="isMessageOpen" modal :header="messageTitle" :style="{ width: '40rem' }"
    :breakpoints="{ '1199px': '75vw', '575px': '90vw' }">
    <div class="p-4 rounded-lg font-mono text-sm whitespace-pre-wrap break-words border"
      :class="selectedLog?.status === 'FAILED' ? 'bg-red-950/20 border-red-500/30 text-red-200' : 'bg-emerald-950/20 border-emerald-500/30 text-emerald-200'">
      {{ messageContent }}
    </div>
  </Dialog>

  <Dialog v-model:visible="isWarningsOpen" modal :header="warningsTitle" :style="{ width: '40rem' }"
    :breakpoints="{ '1199px': '75vw', '575px': '90vw' }">
    <div class="warning-list-dialog">
      <div v-if="!activeWarnings.length" class="empty-state">
        {{ t('versions.no_warnings') }}
      </div>
      <div v-for="(warning, index) in activeWarnings" :key="index" class="warning-item">
        <div class="warning-code">[{{ warning.code }}]</div>
        <div class="warning-msg">{{ warning.message }}</div>
        <div v-if="warning.args && warning.args.length > 0" class="warning-details">
          <strong>Details:</strong> {{ warning.args.join(', ') }}
        </div>
      </div>
    </div>
  </Dialog>
</template>

<style scoped>
.prime-datatable :deep(th) {
  font-family: var(--font-ui);
  text-transform: uppercase;
  font-size: 0.78rem;
  color: var(--text-muted);
}
.warning-btn-pulse {
  animation: warningPulse 2.0s infinite;
  display: inline-flex;
  justify-content: center;
  align-items: center;
}
@keyframes warningPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(245, 158, 11, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 158, 11, 0);
  }
}
.warning-list-dialog {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.5rem 0;
}
.warning-item {
  border-left: 4px solid var(--p-orange-500, #f59e0b);
  padding: 0.75rem 1rem;
  background-color: var(--p-orange-50, rgba(245, 158, 11, 0.05));
  border-radius: 0 6px 6px 0;
}
.warning-code {
  font-weight: bold;
  color: var(--p-orange-700, #b45309);
  font-size: 0.85rem;
  margin-bottom: 0.25rem;
}
.warning-msg {
  color: var(--text-color, #333);
  font-size: 0.95rem;
  line-height: 1.4;
}
.warning-details {
  font-family: monospace;
  font-size: 0.8rem;
  color: #666;
  margin-top: 0.5rem;
  background: rgba(0,0,0,0.03);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}
</style>
