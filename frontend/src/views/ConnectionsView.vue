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
import Card from 'primevue/card';
import Select from 'primevue/select';
import { useConfirm } from 'primevue/useconfirm';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();
const confirm = useConfirm();

const connections = ref([]);
const query = ref('');
const testing = ref(false);

const defaultForm = {
  id: null,
  dbId: '',
  type: 'postgresql',
  host: 'localhost',
  port: 5432,
  databaseName: '',
  username: '',
  password: '',
  queryOptions: ''
};

const form = ref({ ...defaultForm });

const dbTypes = [
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'mariadb', label: 'MariaDB' },
  { value: 'mssql', label: 'SQL Server (MSSQL)' },
  { value: 'oracle', label: 'Oracle Database' },
  { value: 'mongodb', label: 'MongoDB' }
];

const filteredConnections = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return connections.value;
  return connections.value.filter(c =>
    (c.dbId || '').toLowerCase().includes(q) ||
    (c.type || '').toLowerCase().includes(q) ||
    (c.host || '').toLowerCase().includes(q) ||
    (c.databaseName || '').toLowerCase().includes(q) ||
    (c.username || '').toLowerCase().includes(q)
  );
});

// Auto-fill default port based on type selection
watch(() => form.value.type, (newType) => {
  if (form.value.id) return; // Don't overwrite existing connection ports on edit
  if (newType === 'postgresql') form.value.port = 5432;
  else if (newType === 'mariadb') form.value.port = 3306;
  else if (newType === 'mssql') form.value.port = 1433;
  else if (newType === 'oracle') form.value.port = 1521;
  else if (newType === 'mongodb') form.value.port = 27017;
});

async function load() {
  try {
    connections.value = await dashboardApi.dbConnections.list();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

function clearForm() {
  form.value = { ...defaultForm };
}

function editConnection(conn) {
  form.value = { ...conn };
}

async function testConnection() {
  if (!validateForm()) return;

  testing.value = true;
  try {
    const result = await dashboardApi.dbConnections.test(form.value);
    if (result && result.success) {
      toast(t('toast.connection_test_success'), 'success');
    } else {
      toast(t('toast.connection_test_failed', result ? result.message : 'Unknown'), 'error');
    }
  } catch (error) {
    toast(t('toast.connection_test_failed', error.message), 'error');
  } finally {
    testing.value = false;
  }
}

function validateForm() {
  if (!form.value.dbId || !form.value.dbId.trim()) {
    toast(t('toast.connection_db_id_req'), 'warning');
    return false;
  }
  if (!form.value.type) {
    toast(t('toast.connection_type_req'), 'warning');
    return false;
  }
  if (!form.value.host || !form.value.host.trim()) {
    toast(t('toast.connection_host_req'), 'warning');
    return false;
  }
  if (!form.value.port) {
    toast(t('toast.connection_port_req'), 'warning');
    return false;
  }
  if (!form.value.databaseName || !form.value.databaseName.trim()) {
    toast(t('toast.connection_dbname_req'), 'warning');
    return false;
  }
  if (!form.value.username || !form.value.username.trim()) {
    toast(t('toast.connection_user_req'), 'warning');
    return false;
  }
  return true;
}

async function saveConnection() {
  if (!validateForm()) return;

  try {
    const saved = await dashboardApi.dbConnections.save(form.value);
    toast(t('toast.connection_save_success', saved.dbId), 'success');
    clearForm();
    await load();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

async function deleteConnection(conn) {
  confirm.require({
    message: t('common.confirm_delete_connection'),
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
        await dashboardApi.dbConnections.delete(conn.id);
        toast(t('toast.connection_delete_success'), 'success');
        if (form.value.id === conn.id) {
          clearForm();
        }
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
      <!-- Connection Form Card -->
      <Card>
        <template #title>
          <div class="flex justify-between items-center">
            <span>{{ form.id ? t('common.edit') : t('common.create.new') }} {{ t('nav.connection') }}</span>
            <Button variant="outlined" v-if="form.id" icon="pi pi-plus" class="p-button-sm btn-ghost" size="small" @click="clearForm" :label="t('common.create.new')" />
          </div>
        </template>
        <template #content>
          <div class="flex gap-4 flex-col">
            <!-- DB ID -->
            <div class="form-group">
              <label>{{ t('connections.db_id') }} (e.g. <code>demoDb</code>)</label>
              <InputText v-model="form.dbId" class="form-input" :disabled="!!form.id" placeholder="demoDb" />
            </div>

            <!-- Database Type -->
            <div class="form-group">
              <label>{{ t('connections.type') }}</label>
              <Select v-model="form.type" :options="dbTypes" optionValue="value" optionLabel="label" class="w-full" />
            </div>

            <!-- Host -->
            <div class="form-group">
              <label>{{ t('connections.host') }}</label>
              <InputText v-model="form.host" class="form-input" placeholder="localhost" />
            </div>

            <!-- Port -->
            <div class="form-group">
              <label>{{ t('connections.port') }}</label>
              <InputText v-model.number="form.port" type="number" class="form-input" placeholder="5432" />
            </div>

            <!-- Database Name -->
            <div class="form-group">
              <label>{{ t('connections.database_name') }}</label>
              <InputText v-model="form.databaseName" class="form-input" placeholder="my_database" />
            </div>

            <!-- Username -->
            <div class="form-group">
              <label>{{ t('connections.username') }}</label>
              <InputText v-model="form.username" class="form-input" placeholder="postgres" />
            </div>

            <!-- Password -->
            <div class="form-group">
              <label>{{ t('connections.password') }}</label>
              <InputText type="password" v-model="form.password" class="form-input" placeholder="••••••••" />
            </div>

            <!-- Query Options -->
            <div class="form-group">
              <label>{{ t('connections.query_options') }}</label>
              <InputText v-model="form.queryOptions" class="form-input" placeholder="authSource=admin&ssl=false" />
            </div>

            <!-- Actions buttons -->
            <div class="flex gap-2 w-full mt-2">
              <Button 
                variant="outlined"
                class="flex-1 btn-ghost" 
                severity="secondary" 
                icon="pi pi-question-circle" 
                :loading="testing" 
                label="Test"
                @click="testConnection" 
              />
              <Button 
              variant="outlined"
               severity="secondary" 
                class="flex-1 btn-primary" 
                icon="pi pi-save" 
                :label="t('common.save')" 
                @click="saveConnection" 
              />
            </div>
          </div>
        </template>
      </Card>

      <!-- Connections List Card -->
      <Card>
        <template #title>
          <div class="flex justify-between items-center">
            <span>{{ t('nav.connections') }}</span>
            <div style="position: relative; display: flex; align-items: center; width: 100%; max-width: 220px;">
              <i class="pi pi-search" style="position: absolute; left: 10px; color: var(--text-muted);" />
              <InputText style="padding-left: 2.25rem; width: 100%; height: 32px; min-height: auto;"
                class="search-input" type="text" v-model="query" placeholder="Search..." />
            </div>
          </div>
        </template>
        <template #content>
          <div class="table-wrap">
            <DataTable tableStyle="width: 100%;" :value="filteredConnections" responsiveLayout="scroll" class="prime-datatable">
              <template #empty>
                <div class="empty-state">{{ t('upload.history.empty') }}</div>
              </template>

              <!-- DB ID -->
              <Column style="width: 20%;" class="text-wrap break-words" field="dbId" :header="t('connections.db_id')">
                <template #body="slotProps">
                  <code>{{ slotProps.data.dbId }}</code>
                </template>
              </Column>

              <!-- Type -->
              <Column style="width: 15%;" class="text-wrap break-words" field="type" :header="t('connections.type')">
                <template #body="slotProps">
                  <span class="text-sm font-semibold capitalize">{{ slotProps.data.type }}</span>
                </template>
              </Column>

              <!-- Connection string (Host:Port) -->
              <Column style="width: 30%;" class="text-wrap break-words" header="Address">
                <template #body="slotProps">
                  <span class="text-sm">{{ slotProps.data.host }}:{{ slotProps.data.port }}</span>
                </template>
              </Column>

              <!-- Database Name -->
              <Column style="width: 20%;" class="text-wrap break-words" field="databaseName" :header="t('connections.database_name')">
                <template #body="slotProps">
                  <span class="text-sm">{{ slotProps.data.databaseName }}</span>
                </template>
              </Column>

              <!-- Actions -->
              <Column style="width: 15%;" :header="t('common.actions')">
                <template #body="slotProps">
                  <div class="btn-group">
                    <Button variant="outlined" icon="pi pi-pencil" severity="secondary" size="small" class="btn-xs btn-ghost"
                      @click="editConnection(slotProps.data)" />
                    <Button variant="outlined" icon="pi pi-trash" severity="danger" size="small" class="btn-xs btn-danger"
                      @click="deleteConnection(slotProps.data)" />
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
.select-input {
  background: var(--bg-surface);
  color: var(--text-main);
  border: 1px solid var(--border-color);
  padding: 0.5rem;
  border-radius: 6px;
  outline: none;
  cursor: pointer;
}
.select-input option {
  background: var(--bg-surface);
  color: var(--text-main);
}
</style>
