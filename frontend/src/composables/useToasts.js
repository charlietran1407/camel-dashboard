import { useToast } from 'primevue/usetoast';

export function useToasts() {
  const primevueToast = useToast();

  const toast = (message, type = 'info', life = 4000) => {
    let severity = type;
    if (type === 'warning') severity = 'warn';
    
    primevueToast.add({
      severity: severity,
      summary: severity.charAt(0).toUpperCase() + severity.slice(1),
      detail: message,
      life: life
    });
  };

  return { toasts: [], toast };
}

