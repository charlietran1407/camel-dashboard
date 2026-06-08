<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { fmtDate } from '../composables/useFormatters.js';
import { useI18n } from '../composables/useI18n.js';
import { useToasts } from '../composables/useToasts.js';
import { dashboardApi } from '../services/api.js';

import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Checkbox from 'primevue/checkbox';
import { useConfirm } from 'primevue/useconfirm';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();
const confirm = useConfirm();

const properties = ref([]);
const query = ref('');
const form = ref({ key: '', value: '', description: '', secret: false });

const filteredProperties = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return properties.value;
  return properties.value.filter(p =>
    p.key.toLowerCase().includes(q) ||
    (p.value || '').toLowerCase().includes(q) ||
    (p.description || '').toLowerCase().includes(q)
  );
});

async function load() {
  try {
    properties.value = await dashboardApi.properties.list();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

async function saveProperty() {
  if (!form.value.key || !form.value.key.trim()) {
    toast(t('toast.prop_key_req'), 'warning');
    return;
  }
  if (!form.value.value || !form.value.value.trim()) {
    toast(t('toast.prop_val_req'), 'warning');
    return;
  }
  try {
    await dashboardApi.properties.save(form.value);
    toast(t('toast.prop_save_success', form.value.key), 'success');
    form.value = { key: '', value: '', description: '', secret: false };
    await load();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

function editProperty(property) {
  form.value = { ...property };
}

async function deleteProperty(property) {
  confirm.require({
    message: t('common.confirm_delete_property'),
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
        await dashboardApi.properties.delete(property.key);
        toast(t('toast.prop_delete_success'), 'success');
        await load();
      } catch (error) {
        toast(t('common.error') + error.message, 'error');
      }
    }
  });
}

onMounted(load);
watch(() => props.refreshKey, load);
</script>

<template>
  <section class="page active">
    <div class="upload-grid">
      <Card>
        <template #title>{{ t('nav.property') }}</template>
        <template #content>
          <div class="flex gap-4 flex-col">
            <div class="form-group"><label>Key</label>
              <InputText v-model="form.key" class="form-input" />
            </div>
            <div class="form-group"><label>Value</label>
              <InputText v-model="form.value" class="form-input" />
            </div>
            <div class="form-group"><label>{{ t('common.desc_label') }}</label>
              <InputText v-model="form.description" class="form-input" />
            </div>
            <div class="flex items-center mr-auto form-group">
              <label class="flex items-center cursor-pointer select-none space-x-2">
                <Checkbox v-model="form.secret" :binary="true" />
                <span>{{ t('properties.secret') }}</span>
              </label>
            </div>
            <Button variant="outlined" class="w-full" severity="secondary" icon="pi pi-save" :label="t('common.save')"
              @click="saveProperty" />

          </div>
        </template>
      </Card>

      <Card>
        <template #title>
          <div class="flex justify-between items-center">
            <span>{{ t('nav.properties') }}</span>
            <div style="position: relative; display: flex; align-items: center; width: 100%; max-width: 220px;">
              <i class="pi pi-search" style="position: absolute; left: 10px; color: var(--text-muted);" />
              <InputText style="padding-left: 2.25rem; width: 100%; height: 32px; min-height: auto;"
                class="search-input" type="text" v-model="query" placeholder="Search..." />
            </div>
          </div>
        </template>
        <template #content>
          <div class="table-wrap">
            <DataTable  tableStyle="width: 100%;":value="filteredProperties" responsiveLayout="scroll" class="prime-datatable">
              <template #empty>
                <div class="empty-state">{{ t('upload.history.empty') }}</div>
              </template>

              <Column style="width: 30%;" class="text-wrap break-words" field="key" header="Key">
                <template #body="slotProps">
                  <code>{{ slotProps.data.key }}</code>
                </template>
              </Column>

              <Column style="width: 20%;" class="text-wrap break-words" field="value" header="Value">
                <template #body="slotProps">
                  <span class="text-sm">{{ slotProps.data.secret ? '********' : slotProps.data.value }}</span>
                </template>
              </Column>

              <Column  style="width: 25%; " class="text-wrap break-words" field="description" :header="t('common.desc_label')">
                <template #body="slotProps">
                  <span class="muted text-sm">{{ slotProps.data.description || '-' }}</span>
                </template>
              </Column>

              <Column style="width: 15%;" class="text-wrap break-words" field="updatedAt" header="Updated">
                <template #body="slotProps">
                  <span class="muted text-sm">{{ fmtDate(slotProps.data.updatedAt) }}</span>
                </template>
              </Column>

              <Column style="width: 10%;" :header="t('common.actions')">
                <template #body="slotProps">
                  <div class="btn-group">
                    <Button  variant="outlined"  icon="pi pi-pencil" severity="secondary" size="small" class="btn-xs btn-ghost"
                      @click="editProperty(slotProps.data)" />
                    <Button variant="outlined"  icon="pi pi-trash" severity="danger" size="small" class="btn-xs btn-danger"
                      @click="deleteProperty(slotProps.data)" />
                  </div>
                </template>
              </Column>
            </DataTable>
          </div>
        </template>
      </Card>
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
</style>
