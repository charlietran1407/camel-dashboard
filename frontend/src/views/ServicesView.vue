<script setup>
import { onMounted, ref, watch } from 'vue';
import StatusBadge from '../components/StatusBadge.vue';
import { routeDisplayId } from '../composables/useFormatters.js';
import { useI18n } from '../composables/useI18n.js';
import { useToasts } from '../composables/useToasts.js';
import { dashboardApi } from '../services/api.js';
import { useClusterTooltip } from '../composables/useClusterTooltip.js';

import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import { useConfirm } from 'primevue/useconfirm';
import { zodResolver } from '@primevue/forms/resolvers/zod';
import { z } from 'zod';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();
const confirm = useConfirm();
const { tooltipActive, tooltipNodes, tooltipStyle, showTooltip, hideTooltip } = useClusterTooltip();

const services = ref([]);
const routes = ref([]);
const form = ref({ name: '', description: '' });
const editingId = ref(null);

async function load() {
  try {
    [services.value, routes.value] = await Promise.all([
      dashboardApi.services.list(),
      dashboardApi.routes.list()
    ]);
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

function editService(service) {
  editingId.value = service.id;
  form.value = {
    name: service.name,
    description: service.description || ''
  };
}

function cancelEdit() {
  editingId.value = null;
  form.value = { name: '', description: '' };
}

const onFormSubmit = async ({ valid, values }) => {
  if (valid) {
    try {
      if (editingId.value) {
        await dashboardApi.services.update(editingId.value, values);
        toast(t('toast.service_save_success', form.value.name), 'success');
        editingId.value = null;
      } else {
        await dashboardApi.services.create(values);
        toast(t('toast.service_save_success', form.value.name), 'success');
      }
      form.value = { name: '', description: '' };
      await load();
    } catch (error) {
      toast(t('common.error') + error.message, 'error');
    }
  }
};

async function deleteService(id) {
  confirm.require({
    message: t('common.confirm_delete_service'),
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
        await dashboardApi.services.delete(id);
        toast(t('toast.service_delete_success'), 'success');
        await load();
      } catch (error) {
        toast(t('common.error') + error.message, 'error');
      }
    }
  });
}

function findRoute(id) {
  return routes.value.find(route => route.id === id);
}

// ─── DYNAMIC TOOLTIP LOGIC ───

onMounted(load);
watch(() => props.refreshKey, load);

const resolver = ref(zodResolver(
  z.object({
    name: z.string().min(1, { message: t('toast.service_name_req') }),
    description: z.string().optional().nullable().default('')
  })
));

</script>

<template>
  <section class="page active">
    <div class="upload-grid">

      <Card>
        <template #title>{{ editingId ? 'Edit Service' : t('services.add_title') }}</template>
        <template #content>
          <Form :resolver @submit="onFormSubmit" class="flex flex-col w-full"
            style="display: flex; flex-direction: column;">
            <FormField v-slot="$field" name="name" initialValue="" class="flex flex-col gap-1">
              <div class="pt-2">
                <FloatLabel variant="on">
                  <InputText id="name" type="text" v-model="form.name" class="w-full" />
                  <label for="name">{{ t('services.name_label') }}</label>
                </FloatLabel>
              </div>
              <Message v-if="$field?.invalid" severity="error" size="small" variant="simple">
                {{ $field.error?.message }}
              </Message>
            </FormField>
            <FormField v-slot="$field" name="description" initialValue="" class="flex flex-col gap-1">
              <div class="pt-2">
                <FloatLabel variant="on">
                  <InputText id="description" type="text" v-model="form.description" class="w-full" />
                  <label for="description">{{ t('common.desc_label') }}</label>
                </FloatLabel>
              </div>
              <Message v-if="$field?.invalid" severity="error" size="small" variant="simple">
                {{ $field.error?.message }}
              </Message>
            </FormField>

            <div class="flex gap-2 mt-6 w-full">
              <Button variant="outlined" type="submit" :icon="editingId ? 'pi pi-check' : 'pi pi-save'" :severity="editingId ? 'success' : 'secondary'" :label="editingId ? 'Update Service' : t('services.save_btn')" class="flex-1"/>
              <Button variant="outlined" v-if="editingId" type="button" icon="pi pi-times" severity="danger" outlined label="Cancel" @click="cancelEdit" class="flex-1"/>
            </div>
          </Form>
        </template>
      </Card>
      <Card>
        <template #title>{{ t('services.table_title') }}</template>
        <template #content>
          <DataTable :value="services" responsiveLayout="scroll" class="prime-datatable" sortField="createAt">
            <template #empty>
              <div class="empty-state">{{ t('services.empty') }}</div>
            </template>
            <Column field="name" :header="t('services.name_label')">
              <template #body="slotProps">
                <strong>{{ slotProps.data.name }}</strong>
              </template>
            </Column>
            <Column field="description" :header="t('common.desc_label')">
              <template #body="slotProps">
                <span class="muted">{{ slotProps.data.description || '-' }}</span>
              </template>
            </Column>

            <Column header="Routes">
              <template #body="slotProps">
                <div v-for="routeId in slotProps.data.routeIds || []" :key="routeId" class="service-route-row">
                  <template v-if="findRoute(routeId)">
                    <code>{{ routeDisplayId(findRoute(routeId)) }}</code>
                    <div v-if="findRoute(routeId).nodeStates && findRoute(routeId).nodeStates.length > 0" 
                         class="status-dots-wrapper align-middle" 
                         style="margin-left: 0.5rem" 
                         @mouseenter="showTooltip($event, findRoute(routeId).nodeStates)" 
                         @mouseleave="hideTooltip" 
                         @click.stop>
                      <div class="status-dots">
                        <span v-for="node in findRoute(routeId).nodeStates" 
                              :key="node.instanceId" 
                              class="status-dot" 
                              :class="node.status.toLowerCase()">
                        </span>
                      </div>
                    </div>
                    <StatusBadge v-else :status="findRoute(routeId).status" />
                  </template>
                  <template v-else>
                    <code>{{ routeId }}</code>
                    <StatusBadge status="UNKNOWN" />
                  </template>
                </div>
                <div v-if="!(slotProps.data.routeIds && slotProps.data.routeIds.length)" class="muted text-xs">
                  -
                </div>
              </template>
            </Column>
            <Column :header="t('common.actions')">
              <template #body="slotProps">
                <div class="flex gap-2">
                  <Button variant="outlined" icon="pi pi-pencil" severity="warn" size="small" class="btn-xs mr-2"
                    @click="editService(slotProps.data)" />
                  <Button variant="outlined" icon="pi pi-trash" severity="danger" size="small" class="btn-xs btn-danger"
                    @click="deleteService(slotProps.data.id)" />
                </div>
              </template>
            </Column>
          </DataTable>
        </template>
      </Card>
    </div>
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
.prime-datatable :deep(th) {
  font-family: var(--font-ui);
  text-transform: uppercase;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.prime-datatable :deep(tbody tr:hover) {
  position: relative;
  z-index: 8;
}
</style>
