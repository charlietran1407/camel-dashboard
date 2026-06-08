<script setup>
import { ref, watch, nextTick } from 'vue';
import { useModal } from '../composables/useModal.js';

const { modal, closeModal } = useModal();
const codeBlock = ref(null);

watch(() => modal.content, (content) => {
  if (modal.kind === 'code' && content && window.Prism) {
    nextTick(() => {
      const el = codeBlock.value;
      if (el && el.classList.contains('language-yaml')) {
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
  <div class="modal-overlay" :class="{ open: modal.open }" @click.self="closeModal">
    <div class="modal">
      <div class="modal-header">
        <h3>{{ modal.title }}</h3>
        <button class="modal-close" type="button" @click="closeModal">x</button>
      </div>
      <pre v-if="modal.kind === 'code'" class="line-numbers code-preview"><code ref="codeBlock" class="language-yaml">{{ modal.content }}</code></pre>
      <div v-else id="modal-body">{{ modal.content }}</div>
    </div>
  </div>
</template>
