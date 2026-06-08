import { createRouter, createWebHashHistory } from 'vue-router';

import RoutesView from './views/RoutesView.vue';
import ServicesView from './views/ServicesView.vue';
import UploadView from './views/UploadView.vue';
import VersionsView from './views/VersionsView.vue';
import DeployView from './views/DeployView.vue';
import BeansView from './views/BeansView.vue';
import PropertiesView from './views/PropertiesView.vue';
import ClusterView from './views/ClusterView.vue';
import ConnectionsView from './views/ConnectionsView.vue';

const routes = [
    { path: '/', redirect: '/routes' },
    { path: '/routes', name: 'routes', component: RoutesView },
    { path: '/services', name: 'services', component: ServicesView },
    { path: '/upload', name: 'upload', component: UploadView },
    { path: '/versions', name: 'versions', component: VersionsView },
    { path: '/deploy', name: 'deploy', component: DeployView },
    { path: '/beans', name: 'beans', component: BeansView },
    { path: '/properties', name: 'properties', component: PropertiesView },
    { path: '/connections', name: 'connections', component: ConnectionsView },
    { path: '/cluster', name: 'cluster', component: ClusterView }
];

const router = createRouter({
    history: createWebHashHistory(),
    routes
});

export default router;