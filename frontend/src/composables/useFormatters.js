export function fmtDate(value) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString();
}

export function routeDisplayId(route) {
  if (!route) return '';
  return route.originalId || route.id;
}