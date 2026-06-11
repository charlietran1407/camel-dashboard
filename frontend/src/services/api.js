const jsonHeaders = { 'Content-Type': 'application/json' };
const pendingRequests = new Map();

export async function apiFetch(path, options = {}) {
  const method = options.method || 'GET';
  const bodyKey = options.body ? (options.body instanceof FormData ? 'form-data' : String(options.body)) : '';
  const requestKey = `${method}:${path}:${bodyKey}`;

  if (pendingRequests.has(requestKey)) {
    return pendingRequests.get(requestKey);
  }

  const promise = (async () => {
    try {
      const response = await fetch(path, {
        ...options,
        headers: {
          ...(options.body instanceof FormData ? {} : jsonHeaders),
          ...options.headers
        }
      });

      if (!response.ok) {
        const errorData = await response.json().catch(async () => ({ error: await response.text() }));
        const err = new Error(typeof errorData === 'object' ? (errorData.error || response.statusText) : errorData);
        if (typeof errorData === 'object') {
          Object.assign(err, errorData);
        }
        throw err;
      }

      if (response.status === 204) {
        return null;
      }

      return response.json();
    } finally {
      pendingRequests.delete(requestKey);
    }
  })();

  pendingRequests.set(requestKey, promise);
  return promise;
}

export const dashboardApi = {
  services: {
    list: () => apiFetch('/api/services'),
    create: payload => apiFetch('/api/services', { method: 'POST', body: JSON.stringify(payload) }),
    update: (id, payload) => apiFetch(`/api/services/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(payload) }),
    delete: id => apiFetch(`/api/services/${encodeURIComponent(id)}`, { method: 'DELETE' })
  },
  routes: {
    list: () => apiFetch('/api/routes'),
    restServices: () => apiFetch('/api/routes/rest-services'),
    action: (routeId, action) => apiFetch(`/api/routes/${encodeURIComponent(routeId)}/${action}`, { method: 'POST' }),
    delete: routeId => apiFetch(`/api/routes/${encodeURIComponent(routeId)}`, { method: 'DELETE' }),
    source: routeId => apiFetch(`/api/routes/${encodeURIComponent(routeId)}/source`),
    mermaid: routeId => apiFetch(`/api/routes/${encodeURIComponent(routeId)}/mermaid`),
    metrics: () => apiFetch('/api/routes/metrics'),
    metricsById: routeId => apiFetch(`/api/routes/${encodeURIComponent(routeId)}/metrics`),
    deploy: versionId => apiFetch(`/api/routes/deploy/${encodeURIComponent(versionId)}`, { method: 'POST' })
  },
  versions: {
    list: () => apiFetch('/api/versions'),
    byService: serviceId => apiFetch(`/api/versions/service/${encodeURIComponent(serviceId)}`),
    get: id => apiFetch(`/api/versions/${encodeURIComponent(id)}`),
    getContent: id => apiFetch(`/api/versions/${encodeURIComponent(id)}/content`),
    delete: id => apiFetch(`/api/versions/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    setAutoRestore: (id, autoRestore) => apiFetch(`/api/versions/${encodeURIComponent(id)}/auto-restore?autoRestore=${autoRestore}`, { method: 'POST' }),
    uploadFile: formData => apiFetch('/api/versions/upload', { method: 'POST', body: formData }),
    uploadText: (params, content) => apiFetch(`/api/versions/upload/text?${params}`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: content
    })
  },
  beans: {
    list: () => apiFetch('/api/beans'),
    upload: formData => apiFetch('/api/beans/upload', { method: 'POST', body: formData }),
    classes: id => apiFetch(`/api/beans/${encodeURIComponent(id)}/classes`),
    register: id => apiFetch(`/api/beans/${encodeURIComponent(id)}/register`, { method: 'POST' }),
    unregister: id => apiFetch(`/api/beans/${encodeURIComponent(id)}/unregister`, { method: 'POST' }),
    delete: id => apiFetch(`/api/beans/${encodeURIComponent(id)}`, { method: 'DELETE' })
  },
  properties: {
    list: () => apiFetch('/api/env-properties'),
    save: payload => apiFetch('/api/env-properties', { method: 'POST', body: JSON.stringify(payload) }),
    delete: key => apiFetch(`/api/env-properties/${encodeURIComponent(key)}`, { method: 'DELETE' })
  },
  dbConnections: {
    list: () => apiFetch('/api/db-connections'),
    get: id => apiFetch(`/api/db-connections/${encodeURIComponent(id)}`),
    save: payload => apiFetch('/api/db-connections', { method: 'POST', body: JSON.stringify(payload) }),
    delete: id => apiFetch(`/api/db-connections/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    test: payload => apiFetch('/api/db-connections/test', { method: 'POST', body: JSON.stringify(payload) })
  },
  logs: {
    list: (type) => apiFetch(`/api/logs${type ? `?type=${encodeURIComponent(type)}` : ''}`),
    clear: (type) => apiFetch(`/api/logs${type ? `?type=${encodeURIComponent(type)}` : ''}`, { method: 'DELETE' })
  },
  cluster: {
    nodes: () => apiFetch('/api/cluster/nodes'),
    current: () => apiFetch('/api/cluster/current')
  },
  health: {
    check: () => apiFetch('/api/health')
  }
};
