# HTTP Hello Example

This is a beginner-friendly Apache Camel example that demonstrates how to expose a simple HTTP endpoint using the `platform-http` component in Camel YAML DSL. When accessed, it responds with an HTML page.

> [!TIP]
> You can visually design and orchestrate this route (or other complex integrations) using **[Kaoto](https://kaoto.io/)**, the visual designer for Apache Camel, before deploying it here.

## Deployment Workflow using Camel Dashboard

You can manage, deploy, and test this route dynamically through the Camel Dashboard UI using the following steps:

### Step 1: Create a Service
1. Go to the **Services** view/page in the sidebar navigation.
2. In the **Add service** card, enter the **Service name** (e.g., `http-hello-service`) and a description.
3. Click the **Save service** button. The new service will appear in the **Services** table.

### Step 2: Upload the Route to your Service
1. Go to the **Upload** view/page in the sidebar navigation.
2. Select your newly created service (`http-hello-service`) from the **Services** dropdown.
3. Drag & drop the [http_hello.camel.yaml](./http_hello.camel.yaml) file into the dropzone (labeled *Drop .yaml/.yml files here*), or click to choose/browse it from your device.
4. Click the **Upload all** button. You should see a success toast, and the version will be saved under the history table.

### Step 3: Deploy the Route Version
1. Go to the **Deploy** view/page in the sidebar navigation.
2. Filter or select your service (`http-hello-service`) to see its versions.
3. Expand your service name in the Accordion panel.
4. Click the **Deploy** button (paper airplane icon) next to the `http_hello.camel.yaml` file (e.g., `v1`).
5. The application will run a static pre-deployment check. Once it passes, it will show a success notification and the version status will update to **Deployed**.

### Step 4: Testing the Endpoint
Once successfully deployed, the Camel route exposes `/hello` directly on the platform HTTP engine.

Open your browser and navigate to:
- **Default Endpoint**: [http://localhost:8080/hello](http://localhost:8080/hello)
  *(Note: Since this route consumes directly from `platform-http`, it is exposed at the root level and does not require the `/cameldash` REST context path prefix).*

You should see the following response in your browser:
> **This is html from CamelDash**