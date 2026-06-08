<script setup>
import { onMounted, ref, watch } from 'vue';
import { fmtDate } from '../composables/useFormatters.js';
import { useI18n } from '../composables/useI18n.js';
import { useModal } from '../composables/useModal.js';
import { useToasts } from '../composables/useToasts.js';
import { dashboardApi } from '../services/api.js';

import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Tag from 'primevue/tag';
import { useConfirm } from 'primevue/useconfirm';

const props = defineProps({ refreshKey: Number });
const { t } = useI18n();
const { toast } = useToasts();
const { openModal } = useModal();
const confirm = useConfirm();
const beans = ref([]);
const form = ref({ file: null, beanName: '', className: '', description: '' });
const fileupload = ref();
async function load() {
  try {
    beans.value = await dashboardApi.beans.list();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}


function onFileSelect(event) {
  form.value.file = event.files[0];
}
async function uploadBean() {

  if (!form.value.beanName || !form.value.beanName.trim()) {
    toast(t('toast.bean_name_req'), 'warning');
    return;
  }

  if (!form.value.className || !form.value.className.trim()) {
    toast(t('toast.class_name_req'), 'warning');
    return;
  }

  if (!form.value.file) {
    toast(t('toast.file_req'), 'warning');
    return;
  }

  const data = new FormData();
  data.append('file', form.value.file);
  data.append('beanName', form.value.beanName);
  data.append('className', form.value.className);
  data.append('description', form.value.description);
  try {
    await dashboardApi.beans.upload(data);
    form.value = { file: null, beanName: '', className: '', description: '' };
    fileupload.value.clear();
    await load();
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

async function scanClasses(bean) {
  try {
    const classes = await dashboardApi.beans.classes(bean.id);
    openModal(t('modal.bean_classes_title'), classes.join('\n') || '-');
  } catch (error) {
    toast(t('common.error') + error.message, 'error');
  }
}

async function beanAction(bean, action) {
  if (action === 'delete') {
    confirm.require({
      message: t('common.confirm_delete_bean'),
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
          await dashboardApi.beans.delete(bean.id);
          await load();
        } catch (error) {
          toast(t('common.error') + error.message, 'error');
        }
      }
    });
  } else {
    try {
      if (action === 'register') await dashboardApi.beans.register(bean.id);
      if (action === 'unregister') await dashboardApi.beans.unregister(bean.id);
      await load();
    } catch (error) {
      toast(t('common.error') + error.message, 'error');
    }
  }
}

onMounted(load);
watch(() => props.refreshKey, load);
</script>

<template>
  <section class="page active">
    <div class="upload-grid">
      <Card>
        <template #title>{{ t('nav.beans') }}</template>
        <template #content>
          <div class="flex gap-4 flex-col">
            <div class="form-group">
              <label>JAR</label>
              <FileUpload :chooseLabel="t('upload.select_file')" ref="fileupload" @select="onFileSelect"
                severity="secondary" class="p-button-sm p-button-outlined p-button-secondary" mode="basic" name="file"
                accept=".jar" />
            </div>
            <div class="form-group">
              <label>Bean name</label>
              <InputText v-model="form.beanName" class="form-input" />
            </div>
            <div class="form-group">
              <label>Class name</label>
              <InputText v-model="form.className" class="form-input" />
            </div>
            <div class="form-group">
              <label>Description</label>
              <InputText v-model="form.description" class="form-input" />
            </div>
            <Button variant="outlined" class="btn-primary" severity="secondary" icon="pi pi-upload" label="Upload"
              @click="uploadBean" />
          </div>
        </template>
      </Card>

      <Card>
        <template #title>{{ t('nav.beans') }}</template>
        <template #content>
          <div class="table-wrap">
            <DataTable :value="beans" responsiveLayout="scroll" class="prime-datatable" bodyClass="text-center">
              <template #empty>
                <div class="empty-state">{{ t('upload.history.empty') }}</div>
              </template>
              <Column field="beanName" header="Name">
                <template #body="slotProps">
                  <span class="text-sm">{{ slotProps.data.beanName }}</span>
                </template>
              </Column>
              <Column field="className" header="Class">
                <template #body="slotProps">
                  <code class="text-sm">{{ slotProps.data.className }}</code>
                </template>
              </Column>
              <Column field="registered" header="Status">
                <template #body="slotProps">
                  <Tag :icon="slotProps.data.registered ? 'pi pi-info-circle' : 'pi pi-ban'" :severity="slotProps.data.registered ? 'success' : 'danger'" ></Tag>
                </template>
              </Column>
              <Column field="uploadedAt" header="Uploaded">
                <template #body="slotProps">
                  <span class="muted text-sm">{{ fmtDate(slotProps.data.uploadedAt) }}</span>
                </template>
              </Column>
              <Column :header="t('common.actions')">
                <template #body="slotProps">
                  <div class="btn-group">
                    <Button variant="outlined" label="Classes" severity="secondary" class="btn-xs btn-ghost"
                      @click="scanClasses(slotProps.data)" size="small" />
                    <Button variant="outlined" icon="pi pi-check" v-if="!slotProps.data.registered" :label="t('common.activate')" 
                      class="btn-xs btn-success" @click="beanAction(slotProps.data, 'register')" size="small" />
                    <Button variant="outlined" icon="pi pi-ban" v-else :label="t('common.deactivate')" severity="warn" class="btn-xs   btn-warning"
                      @click="beanAction(slotProps.data, 'unregister')" size="small" />
                    <Button variant="outlined" icon="pi pi-trash" severity="danger" class="btn-xs btn-danger" size="small"
                      @click="beanAction(slotProps.data, 'delete')" />
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
