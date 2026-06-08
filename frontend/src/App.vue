<script setup>
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppModal from './components/AppModal.vue';
import AppShell from './components/AppShell.vue';
import ToastHost from './components/ToastHost.vue';
import ConfirmDialog from 'primevue/confirmdialog';

const route = useRoute();
const router = useRouter();
const refreshTick = ref(0);

function handleNavigate(pageName) {
  router.push(`/${pageName}`);
}
</script>

<template>
  <AppShell
    :current-page="route.name || 'routes'"
    @navigate="handleNavigate"
    @refresh="refreshTick++"
  >
    <RouterView :refresh-key="refreshTick" />
  </AppShell>
  
  <ToastHost />
  <ConfirmDialog />
  <AppModal />
</template>