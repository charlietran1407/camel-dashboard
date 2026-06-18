# Exception Handling Example

This is a beginner-friendly Apache Camel example that demonstrates how to implement route-level exception handling using `onException`, validate query parameters dynamically, and return custom HTTP error responses (like `400 Bad Request`) using REST DSL.

> [!TIP]
> You can visually design and orchestrate this route (or other complex integrations) using **[Kaoto](https://kaoto.io/)**, the visual designer for Apache Camel, before deploying it here.

## Deployment Workflow using Camel Dashboard

You can deploy and test this route dynamically through the Camel Dashboard UI using the following steps:

### Step 1: Create a Service
1. Go to the **Services** view/page in the sidebar navigation.
2. In the **Add service** card, enter the **Service name** (e.g., `exception-handling-service`) and a description.
3. Click the **Save service** button. The new service will appear in the **Services** table.

### Step 2: Upload the Route to your Service
1. Go to the **Upload** view/page in the sidebar navigation.
2. Select your newly created service (`exception-handling-service`) from the **Services** dropdown.
3. Drag & drop the [exception-handling.camel.yaml](./exception-handling.camel.yaml) file into the dropzone (labeled *Drop .yaml/.yml files here*), or click to choose/browse it from your device.
4. Click the **Upload all** button. You should see a success toast, and the version will be saved under the history table.

### Step 3: Deploy the Route Version
1. Go to the **Deploy** view/page in the sidebar navigation.
2. Filter or select your service (`exception-handling-service`) to see its versions.
3. Expand your service name in the Accordion panel.
4. Click the **Deploy** button (paper airplane icon) next to the `exception-handling.camel.yaml` file (e.g., `v1`).
5. The application will run a static pre-deployment check. Once it passes, it will show a success notification and the version status will update to **Deployed**.

### Step 4: Testing the Endpoint
Once successfully deployed, the Camel route exposes the `/payment` endpoint mapped under the REST servlet context path `/cameldash`.

#### 1. Valid Request (Amount > 0)
Open your browser and navigate to:
[http://localhost:8080/cameldash/payment?amount=150](http://localhost:8080/cameldash/payment?amount=150)

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "Payment of 150 processed successfully."
}
```

#### 2. Invalid Request (Amount <= 0)
Open your browser and navigate to:
[http://localhost:8080/cameldash/payment?amount=-5](http://localhost:8080/cameldash/payment?amount=-5)

**Response (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Invalid amount: -5. Amount must be greater than 0."
}
```

#### 3. Missing Parameter Request
Open your browser and navigate to:
[http://localhost:8080/cameldash/payment](http://localhost:8080/cameldash/payment)

**Response (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Missing required query parameter: amount"
}
```
