# 🤖 Camel LangChain4j Agent with Gemini

This example demonstrates how to integrate **Apache Camel** with **LangChain4j** and **Google Gemini** within the **Camel Dashboard**. It exposes a REST API that forwards user prompts to a LangChain4j Agent.

---

## 🏗️ Architecture & Flow

```mermaid
sequenceDiagram
    actor User
    participant REST as REST Endpoint (/agent/chat)
    participant Camel as Camel Route (direct:chat)
    participant Agent as LangChain4j Agent
    participant Gemini as Google Gemini API

    User->>REST: POST "Explain Camel Dashboard in one sentence"
    REST->>Camel: Forward request body
    Camel->>Agent: Invoke langchain4j-agent (assistant)
    Agent->>Gemini: Call Google Gemini Chat Model
    Gemini-->>Agent: AI Response
    Agent-->>Camel: Process response
    Camel-->>REST: Return response
    REST-->>User: HTTP 200 (AI Response)
```

---

## ⚙️ Prerequisites & Setup

### 1. Get a Gemini API Key
To use the Google Gemini model, you need an API key. You can get one from [Google AI Studio](https://aistudio.google.com/).

### 2. Configure Environment Properties in Camel Dashboard
| Property Name | Example Value | Description |
|---|---|---|
| `GEMINI_API_KEY` | `AIzaSyD...` | Your Gemini API Key |
| `GEMINI_MODEL_FLASH_LITE` | `gemini-1.5-flash` | The Gemini model name to use |

> [!IMPORTANT]
> Ensure these properties are registered before deploying the route. Camel Dashboard will automatically inject these properties into the YAML route definitions at runtime.

---

## 📦 Dependency & Classpath Setup

Because this example dynamically registers a `GoogleAiGeminiChatModel` bean (which belongs to the third-party LangChain4j library `langchain4j-google-ai-gemini`), the class files must be available on the Camel Dashboard JVM classpath.

Choose **one** of the options below depending on how you are running the application:

### Option A: Local Development Mode (Running via `mvnw spring-boot:run`)
If you are running the backend in development mode, the simplest way to add the dependency is to declare it in the main [`pom.xml`](../../pom.xml):

1. Open [`pom.xml`](../../pom.xml) at the project root.
2. Add the `langchain4j-google-ai-gemini` dependency under `<dependencies>`:
   ```xml
   <dependency>
       <groupId>dev.langchain4j</groupId>
       <artifactId>langchain4j-google-ai-gemini</artifactId>
       <version>1.16.1</version>
   </dependency>
   ```
3. Restart your backend application.

---

### Option B: Production / Standalone Mode (Jar/Docker)
If you are running the packaged Camel Dashboard JAR, dependencies are loaded dynamically from the configured loader path (default is `./libs`):

1. Download the following Maven artifacts (and their dependencies) and copy them into the `./libs` directory at the project root:
   - `dev.langchain4j:langchain4j-google-ai-gemini:1.16.1`
   - `org.apache.camel:camel-langchain4j-agent:4.20.0`
   - `org.apache.camel:camel-langchain4j-agent-api:4.20.0`

2. Alternatively, you can use the Maven dependency plugin to download and place them into the `./libs` directory:
   ```bash
   mvn dependency:copy -Dartifact=dev.langchain4j:langchain4j-google-ai-gemini:1.16.1:jar -DoutputDirectory=./libs
   mvn dependency:copy -Dartifact=org.apache.camel:camel-langchain4j-agent:4.20.0:jar -DoutputDirectory=./libs
   mvn dependency:copy -Dartifact=org.apache.camel:camel-langchain4j-agent-api:4.20.0:jar -DoutputDirectory=./libs
   ```

3. Restart the Camel Dashboard backend.

> [!NOTE]
> Camel Dashboard dynamically scans the deployed route. It will automatically attempt to resolve the Camel component scheme `langchain4j-agent` (stage 2 above) if missing. However, third-party model libraries like `langchain4j-google-ai-gemini` must be staged manually.

---

## 🚀 Deploy the Route

1. Open the Camel Dashboard UI (`http://localhost:8080`).
2. Navigate to **Services** and create a new service called `Langchain4j Agent`.
3. Go to **Upload**, upload the [`langchain4j.camel.yaml`](./langchain4j.camel.yaml) file, and assign it to the `Langchain4j Agent` service.
4. Click **Deploy & Start** to run the route.

---

## 🧪 Testing the Endpoint

Once deployed, you can query your LangChain4j Agent using `curl`:

```bash
curl -X POST http://localhost:8080/cameldash/agent/chat \
  -H "Content-Type: text/plain" \
  -d "Explain Apache Camel in one short sentence."
```

### Expected Output:
```text
Apache Camel is an open-source integration framework that provides a rule-based routing and mediation engine to connect different systems using a vast library of pre-built components and enterprise integration patterns.
```