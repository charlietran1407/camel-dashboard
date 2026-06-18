# File Watcher Example

This is a beginner-friendly Apache Camel example that demonstrates how to monitor file changes in a directory using the `file-watch` component in Camel YAML DSL.

> [!TIP]
> You can visually design and orchestrate this route (or other complex integrations) using **[Kaoto](https://kaoto.io/)**, the visual designer for Apache Camel, before deploying it here.

## Deployment Workflow using Camel Dashboard

You can deploy and test this route dynamically through the Camel Dashboard UI using the following steps:

### Step 1: Create a Service
1. Go to the **Services** view/page in the sidebar navigation.
2. In the **Add service** card, enter the **Service name** (e.g., `file-watcher-service`) and a description.
3. Click the **Save service** button. The new service will appear in the **Services** table.

### Step 2: Upload the Route to your Service
1. Go to the **Upload** view/page in the sidebar navigation.
2. Select your newly created service (`file-watcher-service`) from the **Services** dropdown.
3. Drag & drop the [file-watch.camel.yaml](./file-watch.camel.yaml) file into the dropzone (labeled *Drop .yaml/.yml files here*), or click to choose/browse it from your device.
4. Click the **Upload all** button. You should see a success toast, and the version will be saved under the history table.

### Step 3: Deploy the Route Version
1. Go to the **Deploy** view/page in the sidebar navigation.
2. Filter or select your service (`file-watcher-service`) to see its versions.
3. Expand your service name in the Accordion panel.
4. Click the **Deploy** button (paper airplane icon) next to the `file-watch.camel.yaml` file (e.g., `v1`).
5. The application will run a static pre-deployment check. Once it passes, it will show a success notification and the version status will update to **Deployed**.

### Step 4: Testing the Route
1. Ensure the monitored folder exists on the machine where the Camel integration runs. By default, it watches `/app/logs`.
   *(Note: You can create the `/app/logs` folder on your system drive root, or update the path parameter in [file-watch.camel.yaml](./file-watch.camel.yaml) to your desired folder before uploading).*
2. Go to the **Routes** page to confirm that the route is listed as **Running**.
3. Check your backend console logs. You should see log messages outputted like:
   ```text
   Detect event MODIFY on file /app/logs/app.log
   Detect event MODIFY on file /app/logs/app.log
   ...
   ```
