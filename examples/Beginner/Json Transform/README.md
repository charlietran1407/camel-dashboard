# JSON Transform Example

This is a beginner-friendly Apache Camel example that demonstrates how to expose a REST POST endpoint using REST DSL, perform validation on fields inside a JSON body, and transform the payload format using the `transform` EIP and `jsonpath` expressions.

## Deployment Workflow using Camel Dashboard

You can deploy and test this route dynamically through the Camel Dashboard UI using the following steps:

### Step 1: Create a Service
1. Go to the **Services** view/page in the sidebar navigation.
2. In the **Add service** card, enter the **Service name** (e.g., `json-transform-service`) and a description.
3. Click the **Save service** button. The new service will appear in the **Services** table.

### Step 2: Upload the Route to your Service
1. Go to the **Upload** view/page in the sidebar navigation.
2. Select your newly created service (`json-transform-service`) from the **Services** dropdown.
3. Drag & drop the [json_transform.camel.yaml](./json_transform.camel.yaml) file into the dropzone (labeled *Drop .yaml/.yml files here*), or click to choose/browse it from your device.
4. Click the **Upload all** button. You should see a success toast, and the version will be saved under the history table.

### Step 3: Deploy the Route Version
1. Go to the **Deploy** view/page in the sidebar navigation.
2. Filter or select your service (`json-transform-service`) to see its versions.
3. Expand your service name in the Accordion panel.
4. Click the **Deploy** (or **Triển khai**) button (paper airplane icon) next to the `json_transform.camel.yaml` file (e.g., `v1`).
5. The application will run a static pre-deployment check. Once it passes, it will show a success notification and the version status will update to **Deployed**.

#### Testing the Endpoint
Once successfully deployed, the REST service exposes a POST endpoint at `(cameldash)/users/transform`.

Send an HTTP POST request to:
`http://localhost:8080/(cameldash)/users/transform`

**Headers:**
`Content-Type: application/json`

**Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response (200 OK):**
```json
{
  "fullName": "John Doe",
  "status": "processed"
}
```

