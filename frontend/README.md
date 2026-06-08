# CamelDash Frontend

Vue 3 + Vite frontend for the Camel dashboard.

## Development

```bash
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080`.

## Build

```bash
npm run build
```

The production build writes to `../src/main/resources/static`, so Spring Boot can continue serving the UI from `classpath:/static/`.
