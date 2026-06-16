# Apache Camel Route Creation Instructions and Constraints

When creating, updating, or deploying Apache Camel routes in this application, the AI Agent must strictly follow these rules and guidelines.

## 1. Prerequisite Rules (At the start of route creation)
- **Root Element must be a YAML Sequence (List)**: The Camel YAML DSL root element must start with a YAML sequence (`-`), not a map/object.
  *Correct:*
  ```yaml
  - route:
      id: my-route
      from:
        uri: timer:tick
  ```
  *Incorrect:*
  ```yaml
  route:
    id: my-route
    from:
      uri: timer:tick
  ```
- **Camel Construct Presence**: The root sequence must contain at least one map with one of the following root keys:
  - `route`
  - `rest`
  - `beans`
  - `restConfiguration`
  - `routeConfiguration`
- **File Naming Extension**: The file name must end with `.camel.yaml`, `.yaml`, or `.yml`.

## 2. Route Identification Rules
- **Explicit Route IDs**: Every route must specify an explicit, unique `id` (e.g. `id: my-route`).
- **Explicit REST Verb IDs**: For REST routes (`- rest:`), every HTTP verb block (e.g., `get`, `post`, `put`, `delete`) must specify an explicit `id` (e.g. `id: get-all-users`). Do not omit IDs to avoid auto-generated route ID conflicts.
- **REST Configuration**: Explicitly define `apiContextRouteId` in the `restConfiguration` block if exposing documentation endpoints.

## 3. Collision and Duplicate Prevention
- **Route ID Collisions**: Route IDs must be unique globally across all active services. Collisions fail the pre-deploy check with a `CROSS_SERVICE_ROUTE_ID_COLLISION` error.
- **Endpoint and Path Collisions**: Public endpoints (e.g. REST base paths, netty port configurations, platform-http listener paths) must not overlap across different services. Reusing active paths triggers a `CROSS_SERVICE_ENDPOINT_COLLISION` error.

## 4. Dependencies, Resources, and Classpath Loading
- **Property Resolution**: Any property referenced using `{{prop_name}}` must exist in application properties, environment variables, or dynamic configuration. Undefined property references raise `MISSING_PROPERTY_REFERENCE` warnings.
- **Bean References**: References to beans (`ref:`, `bean:`, or `method:`) must exist in the Spring context or the dynamic database registry. Unresolved references raise `MISSING_STATIC_BEAN_REFERENCE` warnings (excluding standard system keywords like `body`, `header`, `exchange`, etc.).
- **Dynamic Database Connections**: Database routes (SQL/JDBC) must reference data sources registered via the Dynamic Connection Manager in the format `?dataSource=#datasourceName`.
- **Unresolved Camel Components**: If a route references a Camel component that is not yet on the classpath:
  - The deployment engine will automatically download the necessary dependencies.
  - **The application must be restarted** for the newly downloaded libraries to be loaded and recognized.
- **Third‑Party Libraries**: Any third‑party dependency/library not available in the standard Apache Camel catalog must be manually copied into the application's `lib` directory to be loaded on startup.

## 5. Lifecycle Restrictions
- **No Direct Route Deletion**: Individual running routes cannot be deleted directly from the API/UI. Instead, delete the parent Service or Route Version, which handles route teardown and cleans up endpoints cleanly.
