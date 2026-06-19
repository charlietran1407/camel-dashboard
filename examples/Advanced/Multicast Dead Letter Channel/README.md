# Multicast + Dead Letter Channel (Advanced)

An advanced Apache Camel example demonstrating the **Multicast EIP** pattern combined with a **Dead Letter Channel (DLC)** error handler. This simulates a real-world notification dispatcher that fans out an incoming event to multiple delivery channels (Email, SMS, Webhook) in parallel, and gracefully recovers from channel failures through automatic retry and dead-letter queuing.

> [!TIP]
> You can visually design and orchestrate this route (or other complex integrations) using **[Kaoto](https://kaoto.io/)**, the visual designer for Apache Camel, before deploying it here.

---

## 🏗️ Architecture

```
POST /notify
     │
     ▼
[notify-dispatcher] ── errorHandler: DeadLetterChannel (3 retries, 2s delay)
     │
     ├─ [Multicast - parallel, stopOnException: false]
     │       ├─── direct:channel-email   (30% random failure)
     │       ├─── direct:channel-sms     (20% random failure)
     │       └─── direct:channel-webhook (always succeeds)
     │
     └─ On exhausted retries ──► direct:notify-dlc
                                      │
                                      └─ Log + Archive (manual review / Kafka DLQ)
```

---

## ✨ Key Concepts Covered

| Concept | Description |
|---|---|
| **Multicast EIP** | Fan-out a single message to multiple endpoints simultaneously |
| **`parallelProcessing: true`** | All channels are called concurrently (non-blocking) |
| **`stopOnException: false`** | One channel failure does NOT abort the other channels |
| **Dead Letter Channel** | Catches messages that fail all retries and routes to a fallback handler |
| **`maximumRedeliveries: 3`** | Automatic retry up to 3 times before giving up |
| **`useOriginalMessage: true`** | DLC receives the original payload (not a modified/partial body) |

---

## 🚀 Deployment via Camel Dashboard

### Step 1: Create a Service

1. Go to **Services** in the sidebar.
2. Click **Add service**, enter name `notification-dispatcher` and a description.
3. Click **Save service**.

### Step 2: Upload the Route

1. Go to **Upload** in the sidebar.
2. Select `notification-dispatcher` from the **Services** dropdown.
3. Drag & drop [`multicast-dlc.camel.yaml`](./multicast-dlc.camel.yaml) into the dropzone.
4. Click **Upload all**.

### Step 3: Deploy the Route Version

1. Go to **Deploy** in the sidebar.
2. Find your `notification-dispatcher` service in the accordion panel.
3. Click the **Deploy** button (✈️) next to `multicast-dlc.camel.yaml`.
4. Wait for the pre-deploy validation to pass and the success notification to appear.

### Step 4: Test with a REST call

Send a POST request to trigger the dispatcher:

```bash
curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u123","event":"ORDER_PLACED","message":"Your order #456 has been placed."}'
```

Expected response:
```json
{ "status": "dispatched", "channels": ["email", "sms", "webhook"] }
```

### Step 5: Observe the Logs

Check the backend console logs. You'll see parallel channel execution:
```
[DISPATCHER] Received event '...' — multicasting to all channels
[EMAIL] ✅ Notification sent to user via email.
[SMS] ✅ Notification sent to user via SMS.
[WEBHOOK] ✅ Notification pushed to webhook endpoint.
```

When a channel fails (random simulation), you'll see retry attempts:
```
[DLC] ❌ Channel failed after retries. Storing failed message for manual review.
       Cause: Email server timeout — channel unavailable
```

---

## 🔧 Customization

- **Replace log statements** with actual integrations: `smtp:` (email), HTTP call to an SMS gateway, or `http:` for webhooks.
- **Replace the DLC log step** with a `jpa:` call to persist failed messages, or `kafka:` to push to a Kafka Dead Letter Queue.
- **Adjust retry settings** (`maximumRedeliveries`, `redeliveryDelay`) in the `errorHandler` block to match your SLA requirements.

---

## 📚 Related Apache Camel Docs

- [Multicast EIP](https://camel.apache.org/components/latest/eips/multicast-eip.html)
- [Dead Letter Channel](https://camel.apache.org/components/latest/eips/dead-letter-channel.html)
- [Error Handler](https://camel.apache.org/manual/error-handler.html)
