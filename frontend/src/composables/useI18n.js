import { computed, ref } from 'vue';
import { translations } from '../i18n.js';

const currentLang = ref(localStorage.getItem('lang') || 'vi');

export function useI18n() {
  const t = (key, ...args) => {
    let text = translations[currentLang.value]?.[key] || key;
    args.forEach((value, index) => {
      text = text.replace(`{${index}}`, value);
    });
    return text;
  };

  const setLang = lang => {
    currentLang.value = lang;
    localStorage.setItem('lang', lang);
    document.documentElement.lang = lang;
  };

  return {
    currentLang: computed(() => currentLang.value),
    setLang,
    t
  };
}
