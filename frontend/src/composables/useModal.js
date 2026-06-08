import { reactive } from 'vue';

const modal = reactive({
  open: false,
  title: '',
  content: '',
  kind: 'text'
});

export function useModal() {
  const openModal = (title, content, kind = 'text') => {
    modal.title = title;
    modal.content = content || '';
    modal.kind = kind;
    modal.open = true;
  };

  const closeModal = () => {
    modal.open = false;
  };

  return { modal, openModal, closeModal };
}
