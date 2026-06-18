# CSV Reader Example

This is a beginner-friendly Apache Camel example that demonstrates how to accept a CSV file upload, unmarshal it into Java objects, split the rows, and print each row to the log console using Camel's native `csv` dataformat and the `split` EIP.

> [!TIP]
> You can visually design and orchestrate this route (or other complex integrations) using **[Kaoto](https://kaoto.io/)**, the visual designer for Apache Camel, before deploying it here.

## Deployment Workflow using Camel Dashboard

You can deploy and test this route dynamically through the Camel Dashboard UI using the following steps:

### Step 1: Create a Service
1. Go to the **Services** view/page in the sidebar navigation.
2. In the **Add service** card, enter the **Service name** (e.g., `csv-reader-service`) and a description.
3. Click the **Save service** button. The new service will appear in the **Services** table.

### Step 2: Upload the Route to your Service
1. Go to the **Upload** view/page in the sidebar navigation.
2. Select your newly created service (`csv-reader-service`) from the **Services** dropdown.
3. Drag & drop the [csv-reader.camel.yaml](./csv-reader.camel.yaml) file into the dropzone (labeled *Drop .yaml/.yml files here*), or click to choose/browse it from your device.
4. Click the **Upload all** button. You should see a success toast, and the version will be saved under the history table.

### Step 3: Deploy the Route Version
1. Go to the **Deploy** view/page in the sidebar navigation.
2. Filter or select your service (`csv-reader-service`) to see its versions.
3. Expand your service name in the Accordion panel.
4. Click the **Deploy** button (paper airplane icon) next to the `csv-reader.camel.yaml` file (e.g., `v1`).
5. The application will run a static pre-deployment check.

> [!WARNING]
> **Classpath & Dependency Loading Note:**
> Because the `camel-csv` dependency is not in the dashboard's initial classpath, your first deployment attempt will show a validation warning:
> 
> *“Missing Camel components were downloaded and staged in './libs': [org.apache.camel:camel-csv:4.20.0]. Please restart the application (the JARs will be picked up automatically via -Dloader.path), then re-deploy this route version.”*
> 
> To resolve this:
> 1. Restart your Camel Dashboard application so it picks up the downloaded JARs from `./libs`.
> 2. Go back to the **Deploy** page and click the **Deploy** button again.


### Step 4: Testing the Endpoint
Once successfully deployed, the Camel route exposes `/csv` directly on the platform HTTP engine.

#### 1. Use the provided test CSV File
You can use the sample [users.csv](./users.csv) file located in this directory for testing.


#### 2. Send the Upload Request
Use an HTTP client (like Postman or curl) to send a `POST` request to `http://localhost:8080/csv` with the CSV file uploaded in a multipart form-data body.

**Example `curl` command:**
```bash
curl -X POST -F "file=@users.csv" http://localhost:8080/csv
```

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "CSV file processed and logged successfully"
}
```

#### 3. Verify the Output Logs
1. Go to the **Routes** page to check that your route status is **Running**.
2. Check your backend console logs. You should see log messages outputted for each row:
   ```text
   CSV Row: [id, name, role]
   CSV Row: [1, John Doe, Admin]
   CSV Row: [2, Jane Smith, User]
   ```
