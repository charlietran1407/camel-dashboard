<script setup>
import { computed, onMounted, ref, watch, nextTick } from 'vue';
import { fmtDate } from '../composables/useFormatters.js';
import { useI18n } from '../composables/useI18n.js';
import { useToasts } from '../composables/useToasts.js';
import { dashboardApi } from '../services/api.js';

import Card from 'primevue/card';
import Select from 'primevue/select';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import Accordion from 'primevue/accordion';
import AccordionPanel from 'primevue/accordionpanel';
import AccordionHeader from 'primevue/accordionheader';
import AccordionContent from 'primevue/accordioncontent';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();

const services = ref([]);
const versions = ref([]);
const selectedServiceId = ref('');
const deployingVersionId = ref(null);

const isPreviewOpen = ref(false);
const previewTitle = ref('');
const previewContent = ref('');
const yamlCodeBlock = ref(null);

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

async function quickDeploy(version) {
  if (!confirm(t('common.confirm_deploy'))) return;
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
  if (!confirm(t('common.confirm_delete_version'))) return;
  try {
    await dashboardApi.versions.delete(version.id);
    toast(t('toast.delete_version_success'), 'success');
    await load();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

onMounted(load);
watch(() => props.refreshKey, load);
watch(selectedServiceId, load);
</script>

<template>
  <section class="page active">
    <Card>
      <template #title>
        <h2>{{ t('nav.versions') }}</h2>
      </template>
       <template #content>
         <Select v-model="selectedServiceId" :options="services" optionValue="id" optionLabel="name" :placeholder="t('services.select_placeholder')" class="compact-select" />
         <div v-if="!Object.keys(groupedVersions).length" class="empty-state">{{ t('versions.empty') }}</div>
          <Accordion :multiple="true">
            <AccordionPanel
              v-for="(items, serviceId) in groupedVersions"
              :key="serviceId"
              :value="serviceId"
            >
              <AccordionHeader>
                <span>{{ serviceNames[serviceId] || serviceId }}</span>
                <span class="version-tag">{{ items.length }} {{ t('versions.count') }}</span>
              </AccordionHeader>
              <AccordionContent>
                <div v-for="version in [...items].reverse()" :key="version.id" class="version-row">
                  <span class="version-tag">v{{ version.version }}</span>
                  <span v-if="version.active" class="version-active-tag">{{ t('versions.active') }}</span>
                  <span v-if="version.autoRestore" class="version-auto-restore-tag">{{ t('versions.auto_restore') }}</span>
                  <div class="version-info">
                    <div class="version-filename">{{ version.fileName }}</div>
                    <div class="version-meta">{{ version.description || t('versions.no_desc') }} | {{ fmtDate(version.uploadedAt) }}</div>
                  </div>
                  <div class="btn-group">
                    <Button severity="secondary" icon="pi pi-eye" size="small" @click="previewVersion(version)" />
                    <Button severity="success" icon="pi pi-rocket" size="small" :loading="deployingVersionId === version.id" :disabled="deployingVersionId !== null" @click="quickDeploy(version)" :label="t('common.deploy_action')" />
                    <Button 
                      :severity="version.autoRestore ? 'warning' : 'info'" 
                      size="small" 
                      :title="!version.deployedAt ? t('versions.auto_restore_disabled_tooltip') : t('versions.auto_restore_tooltip')" 
                      :disabled="!version.deployedAt"
                      @click="toggleAutoRestore(version)"
                    >
                      {{ version.autoRestore ? t('versions.disable_auto_restore') : t('versions.enable_auto_restore') }}
                    </Button>
                    <Button severity="danger" icon="pi pi-trash" size="small" @click="deleteVersion(version)" />
                  </div>
                </div>
              </AccordionContent>
            </AccordionPanel>
          </Accordion>
       </template>
    </Card>
  </section>
  
  <Dialog v-model:visible="isPreviewOpen" maximizable modal :header="previewTitle"
    :style="{ width: '50rem' }" :breakpoints="{ '1199px': '75vw', '575px': '90vw' }">
    <pre class="line-numbers" style="margin: 0; background: #1d1f21;"><code ref="yamlCodeBlock" class="language-yaml">{{ previewContent }}</code></pre>
  </Dialog>
</template>