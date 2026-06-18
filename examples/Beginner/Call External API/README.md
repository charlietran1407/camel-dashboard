# Call External API Example

This is a beginner-friendly Apache Camel example that demonstrates how to schedule a task using the `timer` component and fetch data from an external HTTP rest endpoint using the `http` component in Camel YAML DSL.

> [!TIP]
> You can visually design and orchestrate this route (or other complex integrations) using **[Kaoto](https://kaoto.io/)**, the visual designer for Apache Camel, before deploying it here.

## Deployment Workflow using Camel Dashboard

You can deploy and monitor this route dynamically through the Camel Dashboard UI using the following steps:

### Step 1: Create a Service
1. Go to the **Services** view/page in the sidebar navigation.
2. In the **Add service** card, enter the **Service name** (e.g., `call-external-api-service`) and a description.
3. Click the **Save service** button. The new service will appear in the **Services** table.

### Step 2: Upload the Route to your Service
1. Go to the **Upload** view/page in the sidebar navigation.
2. Select your newly created service (`call-external-api-service`) from the **Services** dropdown.
3. Drag & drop the [call-external-api.camel.yaml](./call-external-api.camel.yaml) file into the dropzone (labeled *Drop .yaml/.yml files here*), or click to choose/browse it from your device.
4. Click the **Upload all** button. You should see a success toast, and the version will be saved under the history table.

### Step 3: Deploy the Route Version
1. Go to the **Deploy** view/page in the sidebar navigation.
2. Filter or select your service (`call-external-api-service`) to see its versions.
3. Expand your service name in the Accordion panel.
4. Click the **Deploy** button (paper airplane icon) next to the `call-external-api.camel.yaml` file (e.g., `v1`).
5. The application will run a static pre-deployment check. Once it passes, it will show a success notification and the version status will update to **Deployed**.

### Step 4: Verify the Execution
Since this route is triggered automatically by a `timer` component when it starts, you do not need to send any external requests to test it.

1. Go to the **Routes** page in the sidebar navigation to confirm that your route `route-3540` is listed as **Running**.
2. Check your backend console logs. You should see the JSON body fetched from the external API printed in the output console:
   ```json
   {
     "userId": 1,
     "id": 1,
     "title": "sunt aut facere repellat provident occaecati excepturi optio reprehenderit",
     "body": "quia et suscipit\nsuscipit recusandae... "
   }
   ```
   *(Note: The timer is set to run every 1000ms and repeat 10 times, so you will see this log output 10 times in total after deployment).*
