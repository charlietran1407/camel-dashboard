<script setup>
import { onMounted, ref, watch } from 'vue';
import { fmtDate } from '../composables/useFormatters.js';
import { useI18n } from '../composables/useI18n.js';
import { useToasts } from '../composables/useToasts.js';
import { dashboardApi } from '../services/api.js';
import { zodResolver } from '@primevue/forms/resolvers/zod';
import { z } from 'zod';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();

const services = ref([]);
const versions = ref([]);
const uploadServiceId = ref('');
const textServiceId = ref('');
const queue = ref([]);
const isDragOver = ref(false);

const resolver = ref(zodResolver(
  z.object({
    description: z.string().optional().nullable().default(''),
    content: z.string().min(1, { message: t('toast.upload_text_content_req') })
  })
));

const warningsDialogVisible = ref(false);
const selectedVersionWithWarnings = ref(null);

function showWarningsPopup(version) {
  selectedVersionWithWarnings.value = version;
  warningsDialogVisible.value = true;
}

function formatMessage(item) {
  if (!item) return '';
  const key = `validation.${item.code}`;
  const translated = t(key, ...(item.args || []));
  if (translated === key) {
    return item.message;
  }
  return translated;
}

async function load() {
  try {
    [services.value, versions.value] = await Promise.all([
      dashboardApi.services.list(),
      dashboardApi.versions.list()
    ]);
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

function addFiles(files) {
  files.forEach(file => {
    const index = queue.value.findIndex(item => item.file.name === file.name);
    const item = { file, description: '' };
    if (index >= 0) {
      queue.value[index] = item;
    } else {
      queue.value.push(item);
    }
  });
}

function onDrop(event) {
  isDragOver.value = false;
  if (event.dataTransfer && event.dataTransfer.files) {
    const files = [...event.dataTransfer.files].filter(file => {
      const name = file.name.toLowerCase();
      return name.endsWith('.yaml') || name.endsWith('.yml');
    });
    if (files.length > 0) {
      addFiles(files);
    } else {
      toast(t('upload.invalid_file_type') || 'Only .yaml/.yml files are allowed', 'warning');
    }
  }
}

async function uploadAll() {
  if (!uploadServiceId.value) {
    toast(t('services.select_placeholder'), 'warning');
    return;
  }
  let ok = 0;
  for (const item of queue.value) {
    const formData = new FormData();
    formData.append('file', item.file);
    formData.append('serviceId', uploadServiceId.value);
    formData.append('description', item.description);
    try {
      const res = await dashboardApi.versions.uploadFile(formData);
      if (res && res.warnings && res.warnings.length > 0) {
        const warningMsgs = res.warnings.map(w => formatMessage(w)).join('\n');
        toast(`${item.file.name} uploaded with warnings:\n${warningMsgs}`, 'warning');
      }
      ok++;
    } catch (error) {
      let errMsg = error.message;
      if (error.errors && Array.isArray(error.errors) && error.errors.length > 0) {
        errMsg = error.errors.map(err => formatMessage(err)).join('\n');
      }
      toast(`${item.file.name}: ${errMsg}`, 'error');
    }
  }
  if (ok) {
    toast(t('toast.upload_success', ok), 'success');
    queue.value = [];
    await load();
  }
}

const onFormSubmit = async ({ valid, values }) => {
  if (!valid) return;
  if (!textServiceId.value) {
    toast(t('services.select_placeholder'), 'warning');
    return;
  }
  try {
    const params = new URLSearchParams({
      serviceId: textServiceId.value,
      description: values.description || ''
    });
    const res = await dashboardApi.versions.uploadText(params, values.content);
    if (res && res.warnings && res.warnings.length > 0) {
      const warningMsgs = res.warnings.map(w => formatMessage(w)).join('\n');
      const routeFileName = res.fileName || 'Route';
      toast(`${routeFileName} uploaded with warnings:\n${warningMsgs}`, 'warning');
    } else {
      toast(t('toast.upload_text_success'), 'success');
    }
    await load();
  } catch (error) {
    let errMsg = error.message;
    if (error.errors && Array.isArray(error.errors) && error.errors.length > 0) {
      errMsg = error.errors.map(err => formatMessage(err)).join('\n');
    }
    toast(t('common.error') + errMsg, 'error');
  }
};

async function deleteVersion(id) {
  if (!confirm(t('common.confirm_delete_version'))) return;
  try {
    await dashboardApi.versions.delete(id);
    toast(t('toast.delete_version_success'), 'success');
    await load();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

onMounted(load);
watch(() => props.refreshKey, load);
</script>

<template>
  <section class="page active">
    <div class="upload-grid">
      <Card>
        <template #title>
          <h2>{{ t('upload.title') }}</h2>
        </template>
        <template #content>
          <div>
            <label class="block mb-[0.4rem] text-[0.82rem] font-semibold">{{ t('nav.services') }}</label>
            <Select v-model="uploadServiceId" :options="services" optionValue="id" optionLabel="name" :placeholder="t('services.select_placeholder')" class="w-full" />
          </div>
          <label
            class="drop-zone"
            :class="{ dragover: isDragOver }"
            @dragover.prevent="isDragOver = true"
            @dragenter.prevent="isDragOver = true"
            @dragleave.prevent="isDragOver = false"
            @drop.prevent="onDrop"
          >
            <input type="file" accept=".yaml,.yml" multiple hidden @change="addFiles([...$event.target.files]); $event.target.value = ''" />
            <strong>{{ t('upload.dropzone') }}</strong>
            <span>{{ t('upload.or') }} {{ t('upload.select_file') }}</span>
          </label>
          <div id="upload-queue" class="flex flex-col">
            <div v-for="(item, index) in queue" :key="item.file.name" class="queue-item">
              <div class="queue-item-info">
                <span class="queue-item-name">{{ item.file.name }}</span>
                <span class="queue-item-size">{{ (item.file.size / 1024).toFixed(1) }} KB</span>
              </div>
              <InputText class="w-1/2" type="text" v-model="item.description" :placeholder="t('upload.desc_placeholder')" />
              <Button variant="outlined" severity="danger" icon="pi pi-times" @click="queue.splice(index, 1)" />
            </div>
          </div>
          <Button variant="outlined" class="full-width" severity="secondary" type="button" icon="pi pi-check" v-if="queue.length" @click="uploadAll" :label="t('upload.btn_all') "/>
        </template>
      </Card>
      <Card>
        <template #title>
          <h2>{{ t('upload.text_title') }}</h2>
        </template>
        <template #content>
          <Form :resolver @submit="onFormSubmit" class="flex flex-col w-full gap-4 px-5 pt-4 pb-4">
          <div>
            <label class="block mb-[0.4rem] text-[0.82rem] font-semibold">{{ t('nav.services') }}</label>
            <Select v-model="textServiceId" :options="services" optionValue="id" optionLabel="name" :placeholder="t('services.select_placeholder')" class="w-full" />
          </div> 

          <FormField v-slot="$field" name="description" initialValue="" class="flex flex-col gap-1">      
            <FloatLabel variant="on">
              <InputText id="uploadDescription" type="text" class="w-full"/>
              <label for="uploadDescription">{{ t('upload.desc_placeholder') }}</label>
            </FloatLabel>          
          </FormField>      
          <FormField v-slot="$field" name="content" initialValue="" class="flex flex-col gap-1">          
            <Textarea rows="12" cols="80" class="w-full" />
            <Message v-if="$field?.invalid" severity="error" size="small" variant="simple">
              {{ $field.error?.message }}
            </Message>
          </FormField>
          <Button variant="outlined" icon="pi pi-upload" class="w-full" severity="secondary" type="submit" :label="t('upload.text_title')"/>
          </Form>
        </template>
      </Card>
    </div>

    <Card class="mt-5">
      <template #title>
        <h2>History</h2>
      </template>
      <template #content>
        <DataTable :value="versions" dataKey="id" :paginator="true" :rows="10" :globalFilterFields="['fileName', 'description']" :emptyMessage="t('upload.history.empty')" responsiveLayout="scroll" class="prime-datatable">
          <Column field="fileName" :header="'File'" sortable>
            <template #body="{ data }">
              <div class="flex items-center gap-2">
                <span>{{ data.fileName }}</span>
                <Button  v-if="data.warnings && data.warnings.length > 0"
                        icon="pi pi-exclamation-triangle"
                        severity="warn"
                        variant="outlined"
                        class="p-0 h-6 w-6 text-amber-500"
                        @click="showWarningsPopup(data)" />
              </div>
            </template>
          </Column>
          <Column field="version" :header="'Version'" sortable>
            <template #body="{ data }">
              <Tag :value="'v' + data.version" />
            </template>
          </Column>
          <Column field="description" :header="'Description'" sortable>
            <template #body="{ data }">
              <span class="muted">{{ data.description || '-' }}</span>
            </template>
          </Column>
          <Column field="uploadedAt" :header="'Uploaded'" sortable>
            <template #body="{ data }">
              <span class="muted">{{ fmtDate(data.uploadedAt) }}</span>
            </template>
          </Column>
          <Column :header="t('common.actions')">
            <template #body="{ data }">
              <div class="flex gap-2">
                <Button variant="outlined" severity="danger" icon="pi pi-trash" @click="deleteVersion(data.id)" size="small"/>
              </div>
            </template>
          </Column>
        </DataTable>
      </template>
    </Card>

    <!-- Warnings Popup Dialog -->
    <Dialog v-slot:default v-model:visible="warningsDialogVisible" modal :header="t('upload.warnings_dialog_title') || 'Validation Warnings'" :style="{ width: '45rem' }">
      <div v-if="selectedVersionWithWarnings" class="flex flex-col gap-3 pt-2">
        <p class="text-sm text-gray-400 mb-2">
          File: <strong>{{ selectedVersionWithWarnings.fileName }}</strong> (Version: v{{ selectedVersionWithWarnings.version }})
        </p>
        <div class="flex flex-col gap-2 max-h-[30rem] overflow-y-auto">
          <Message v-for="(warn, index) in selectedVersionWithWarnings.warnings" :key="index" severity="warn" class="w-full">
            <div class="flex flex-col gap-1">
              <span class="font-bold text-[0.85rem]">{{ warn.code }}</span>
              <span class="text-sm text-gray-300">{{ formatMessage(warn) }}</span>
            </div>
          </Message>
        </div>
      </div>
    </Dialog>
  </section>
</template>

<style scoped>
.prime-datatable :deep(th) {
    font-family: var(--font-ui);
    text-transform: uppercase;
    font-size: 0.78rem;
    color: var(--text-muted);
}

.drop-zone.dragover {
    border-color: var(--accent);
    background: rgba(99, 102, 241, 0.08) !important;
    color: var(--text);
}
</style>
