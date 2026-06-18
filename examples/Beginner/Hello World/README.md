# Cron Log Example

This is a beginner-friendly Apache Camel example that demonstrates how to schedule a recurring log message using a cron trigger (`cron:tab`) and resolve dynamic properties in Camel YAML DSL.

> [!TIP]
> You can visually design and orchestrate this route (or other complex integrations) using **[Kaoto](https://kaoto.io/)**, the visual designer for Apache Camel, before deploying it here.

## Deployment Workflow using Camel Dashboard

You can configure, deploy, and test this route dynamically through the Camel Dashboard UI using the following steps:

### Step 1: Add the Required Property
This route references a property placeholder `{{cron.message}}`. You need to define it before deploying:
1. Go to the **Properties** view/page in the sidebar navigation.
2. In the **Add property** card, enter:
   - **Key**: `cron.message`
   - **Value**: `Hello World! current time is`
3. Click the **Save** button.

### Step 2: Create a Service
1. Go to the **Services** view/page in the sidebar navigation.
2. In the **Add service** card, enter the **Service name** (e.g., `cron-log-service`) and a description.
3. Click the **Save service** button. The new service will appear in the **Services** table.

### Step 3: Upload the Route to your Service
1. Go to the **Upload** view/page in the sidebar navigation.
2. Select your newly created service (`cron-log-service`) from the **Services** dropdown.
3. Drag & drop the [cron-log.camel.yaml](./cron-log.camel.yaml) file into the dropzone (labeled *Drop .yaml/.yml files here*), or click to choose/browse it from your device.
4. Click the **Upload all** button. You should see a success toast, and the version will be saved under the history table.

### Step 4: Deploy the Route Version
1. Go to the **Deploy** view/page in the sidebar navigation.
2. Filter or select your service (`cron-log-service`) to see its versions.
3. Expand your service name in the Accordion panel.
4. Click the **Deploy** button (paper airplane icon) next to the `cron-log.camel.yaml` file (e.g., `v1`).
5. The application will run a static pre-deployment check. Once it passes, it will show a success notification and the version status will update to **Deployed**.

### Step 5: Verify the Execution
1. Go to the **Routes** view/page in the sidebar navigation to confirm that the route `cron-log` is listed as **Running**.
2. Check your backend console logs. You should see the message being printed every 5 seconds:
   ```text
   Hello World! current time is 15:20:00
   Hello World! current time is 15:20:05
   ```
