<script setup>
import Badge from 'primevue/badge';

defineProps({
  status: {
    type: String,
    default: 'UNKNOWN'
  }
});

const severityMap = {
  'Started': 'success',
  'Stopped': 'warn',
  'Suspended': 'info',
  'Unknown': 'secondary'
}
</script>

<template>
  <Badge :severity="severityMap[status]">{{ status }}</Badge>
</template>
