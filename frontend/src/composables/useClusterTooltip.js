import { ref } from 'vue';

export function useClusterTooltip() {
  const tooltipActive = ref(false);
  const tooltipNodes = ref([]);
  const tooltipStyle = ref({
    position: 'absolute',
    left: '0px',
    top: '0px',
    bottom: 'auto',
    opacity: 0,
    pointerEvents: 'none',
    transform: 'translate(-50%, -100%) translateY(0px)'
  });

  function showTooltip(event, nodeStates) {
    if (!nodeStates || nodeStates.length === 0) return;
    tooltipNodes.value = nodeStates;
    
    const rect = event.currentTarget.getBoundingClientRect();
    const scrollX = window.scrollX || window.pageXOffset;
    const scrollY = window.scrollY || window.pageYOffset;
    
    tooltipStyle.value = {
      position: 'absolute',
      left: `${rect.left + rect.width / 2 + scrollX}px`,
      top: `${rect.top + scrollY}px`,
      bottom: 'auto',
      opacity: 1,
      pointerEvents: 'auto',
      transform: 'translate(-50%, -100%) translateY(-8px)',
      transition: 'opacity 0.25s cubic-bezier(0.4, 0, 0.2, 1), transform 0.25s cubic-bezier(0.4, 0, 0.2, 1)'
    };
    tooltipActive.value = true;
  }

  function hideTooltip() {
    tooltipActive.value = false;
    tooltipStyle.value.opacity = 0;
    tooltipStyle.value.pointerEvents = 'none';
    tooltipStyle.value.transform = 'translate(-50%, -100%) translateY(0px)';
  }

  return {
    tooltipActive,
    tooltipNodes,
    tooltipStyle,
    showTooltip,
    hideTooltip
  };
}
