# Firebase Cloud Messaging Integration — NammaKadaiTheru / KadaiTheru

This document describes the push-notification module added to the existing
`nktprocess` Spring Boot backend. It fits inside your current architecture —
the single `/data` process-engine endpoint, `process-flow.json` process
definitions, `NktOperationHandler` pattern, and `NktDynamicRepository` —
rather than introducing a parallel REST API or a second config mechanism.

## 1. What was actually inspected first

Before writing anything, the project was read directly (pom.xml, `application.yml`,
`process-flow.json`, `NktCoreService`, `NktDynamicRepository`, `NktOperationHandler`,
`JwtTokenProvider`, `NktAuthHandler`, `NktUserHandler`, `NktOrderHandler`, the
WebSocket handlers, and the store-staff auth flow). Two things from the original
task brief do **not** match this codebase and the implementation was adapted
to the real thing instead of replacing it:

| Brief assumed | This codebase actually has |
|---|---|
| `ApiConfigLoader` / `ApiConfigProperties` / `ApiDefinition`, YAML-defined APIs, one endpoint per operation | A single `POST /data?code=...&data={json}` endpoint (`ProcessEngineController` → `NktCoreService`), routed by **`process-flow.json`** (JSON, not YAML) into `NktProcessDefinition` objects keyed by `Code` |
| Per-operation REST controllers | One `NktOperationHandler` lambda per operation, registered by `HandlerKey` in `NktCoreService.registerHandlers()` |
| Roles `CUSTOMER` / `STORE_STAFF` / `STORE_OWNER` / `ADMIN` | Actual `userType` values in this codebase are `customer`, `business` (store owner), `employee` (store staff). **There is no `admin` userType or admin login flow yet.** |

Everything below was built against the real thing.

## 2. Architecture

```
Flutter                                   Spring Boot (nktprocess)
──────────────────────────────────────────────────────────────────
FCM SDK generates token
        │
        ▼
POST /data?code=nkt.notification.fcm.register_device
        │                                  ProcessEngineController
        │                                          │
        │                                  NktCoreService.process()
        │                                    (JWT → userId/userType,
        │                                     RequiredFields, allowedRoles)
        │                                          │
        │                                  NktNotificationHandler
        │                                    .registerDevice()
        │                                          │
        │                                  user_devices (MongoDB)
        ▼
   … order placed / accepted / dispatched / delivered …
        │                                  NktOrderHandler
        │                                    .placeOrder() / updateOrderStatus()
        │                                          │
        │                                  OrderNotificationService
        │                                    (order wording + targeting)
        │                                          │
        │                                  NotificationDispatchService
        │                                    (resolves user_devices,
        │                                     records history,
        │                                     deactivates dead tokens)
        │                                          │
        │                                  FirebaseNotificationService
        │                                    (Firebase Admin SDK only)
        ▼
Flutter receives push, reads data.type / orderId / storeId / screen,
navigates accordingly
```

Three service layers, each with one job (task requirement: *"the [Firebase]
service should not contain MongoDB business rules"*):

* **`FirebaseNotificationService`** (`com.service`) — Firebase Admin SDK only.
  Builds `Message`/`MulticastMessage`, calls `sendEachForMulticast` / `sendEach`
  (the current recommended APIs — **not** the deprecated `sendMulticast` /
  `sendAll`), batches at 500, classifies `FirebaseMessagingException` into
  "dead token" vs "transient error". Knows nothing about Mongo.
* **`NotificationDispatchService`** (`com.service`) — the reusable Mongo ⇄
  Firebase bridge. Resolves `user_devices`, calls `FirebaseNotificationService`,
  deactivates dead tokens, writes `notifications` history. Shared by both
  callers below.
* **`OrderNotificationService`** (`com.service`) — order wording/targeting
  only ("Order placed successfully", "New order ORD10001 received", …). Calls
  `NotificationDispatchService`. This is what keeps order business logic out
  of the Firebase layer.
* **`NktNotificationHandler`** (`com.service.handlers`) — the four
  `NktOperationHandler` entry points (`FCM_REGISTER_DEVICE`,
  `FCM_SEND_NOTIFICATION`, `FCM_SEND_GROUP_NOTIFICATION`,
  `FCM_SEND_BATCH_NOTIFICATION`). Owns request validation, authorisation and
  group-target resolution; delegates sending to `NotificationDispatchService`.

`NktOrderHandler` gained one constructor dependency (`OrderNotificationService`)
and two call sites: the end of `placeOrder()` and the shared
`updateOrderStatus()` helper (which `storeOrderAccept/Reject/Dispatch/Deliver`
all already funnel through — one hook covers all four). Every notification
call is wrapped so a Firebase failure **never** breaks the order API response.

## 3. Files changed / added

**New files:**
```
src/main/java/com/configs/FirebaseConfig.java
src/main/java/com/service/FirebaseNotificationService.java
src/main/java/com/service/NotificationDispatchService.java
src/main/java/com/service/OrderNotificationService.java
src/main/java/com/service/handlers/NktNotificationHandler.java
src/main/java/com/models/fcm/FcmSendResult.java
src/main/java/com/models/fcm/FcmTokenOutcome.java
src/main/java/com/models/fcm/FcmBatchEntry.java
src/test/java/com/service/FirebaseNotificationServiceTest.java
src/test/java/com/service/NotificationDispatchServiceTest.java
src/test/java/com/service/handlers/NktNotificationHandlerTest.java
docs/FCM_INTEGRATION.md            (this file)
docs/mongo_indexes_fcm.js
docs/postman_fcm_examples.json
```

**Modified files (minimal, additive changes only):**
```
pom.xml                                             — firebase-admin dependency (already present, see §4)
src/main/resources/application.yml                  — firebase.* config block
src/main/resources/process-flow.json                — 4 new process definitions
src/main/java/com/constant/NktProcessCodes.java      — 4 new code constants
src/main/java/com/service/NktCoreService.java        — inject + register 4 new handlers
src/main/java/com/service/handlers/NktOrderHandler.java — inject OrderNotificationService, 2 call sites
```

No existing class was deleted, renamed, or had its public behaviour changed.
The legacy `nkt.notification.register_device` → `deviceTokens` handler is
left exactly as it was; the new `user_devices` collection is deliberately
separate (see §7).

## 4. Maven dependency

Your `pom.xml` **already contains**:

```xml
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.10.0</version>
</dependency>
```

This is a current, stable release of the Firebase Admin Java SDK and is
compatible with your Spring Boot 4.0.2 / Java 17 (compiler target 21) setup —
no version change needed. `FirebaseConfig` also relies transitively on
`com.google.auth:google-auth-library-oauth2-http`, which `firebase-admin`
already pulls in, so nothing else needs adding.

## 5. Firebase configuration — local development

1. In the [Firebase Console](https://console.firebase.google.com/) → your
   project → ⚙️ Project settings → **Service accounts** → **Generate new
   private key**. This downloads a JSON file — treat it like a password.
2. Save it **outside** the repo, e.g. `~/secrets/nkt-firebase-adminsdk.json`.
   Confirm `.gitignore` already excludes it (add `*firebase-adminsdk*.json`
   if you keep it inside the project folder by mistake).
3. Set these environment variables before running the app:

   ```bash
   export FIREBASE_ENABLED=true
   export FIREBASE_CREDENTIALS_PATH=/absolute/path/to/nkt-firebase-adminsdk.json
   export FIREBASE_PROJECT_ID=your-firebase-project-id
   ```

   Or, in Eclipse/IntelliJ run configuration → Environment variables, add the
   same three. With `FIREBASE_ENABLED` unset/false, the app still boots
   normally and every push send becomes a logged no-op — useful when you're
   working on something unrelated and don't want to configure credentials.

## 6. Firebase configuration — production (Render)

Render doesn't give you an easy place to mount an arbitrary file, so use the
inline-JSON path instead of a file path:

1. Locally, base64-encode the service-account JSON:
   ```bash
   base64 -i nkt-firebase-adminsdk.json | tr -d '\n' > firebase-b64.txt
   ```
2. In the Render dashboard → your service → **Environment**, add:
   | Key | Value |
   |---|---|
   | `FIREBASE_ENABLED` | `true` |
   | `FIREBASE_CREDENTIALS_JSON` | *(paste the contents of `firebase-b64.txt`)* |
   | `FIREBASE_PROJECT_ID` | your Firebase project id |
3. Deploy. `FirebaseConfig` decodes the base64 value **in memory** and builds
   `GoogleCredentials` directly from the byte stream — the JSON is never
   written to disk on the Render instance.
4. Never commit `firebase-b64.txt` or the original JSON. Delete the local
   copies once they're in Render's environment settings.

If you'd rather use a real GCP service-account binding (e.g. deploying on
GCP later), leave `FIREBASE_CREDENTIALS_JSON`/`FIREBASE_CREDENTIALS_PATH`
unset — `FirebaseConfig` then falls back to
`GoogleCredentials.getApplicationDefault()`, i.e. standard
`GOOGLE_APPLICATION_CREDENTIALS` / Application Default Credentials discovery.

## 7. MongoDB: `user_devices` (new) vs `deviceTokens` (existing, untouched)

The codebase already has a `NOTIFICATION_REGISTER_DEVICE` handler writing to
a `deviceTokens` collection (and an unused `DeviceToken` `@Document` model
pointing at `device_tokens` — the two don't even agree on a name, a
pre-existing inconsistency, not something this change introduces). That
handler is **left exactly as-is**.

This integration adds a new, separate `user_devices` collection because the
brief specifies a richer schema (multi-device support, `storeId`,
`appVersion`, `inactiveReason`, `lastUsedAt`) that the legacy handler doesn't
have, and because every other domain in this codebase (`wishlist`,
`ratings`, `otp_records`, `store_staff_tokens`, …) is a plain
`Map<String,Object>` collection accessed through `NktDynamicRepository` with
no dedicated Java entity — `user_devices` follows that exact convention
rather than adding a new Spring Data `@Document` model.

```json
{
  "userId":         "EMP_3f1c...",        // employeeId for employee, Mongo _id for customer/business
  "userType":       "employee",           // customer | business | employee
  "storeId":        "ST011",              // resolved server-side; null for customer
  "deviceId":       "DEVICE_001",
  "fcmToken":       "xxxxx",
  "platform":       "ANDROID",
  "appVersion":     "1.0.0",
  "isActive":       true,
  "inactiveReason": null,                 // "FCM_TOKEN_INVALID" once Firebase reports it dead
  "createdAt":      "2026-09-17T10:00:00",
  "updatedAt":      "2026-09-17T10:00:00",
  "lastUsedAt":     "2026-09-17T10:00:00"
}
```

Keyed by `(userId, deviceId)` — see `docs/mongo_indexes_fcm.js` for the
unique index. A returning device (same `deviceId`) updates its existing row
(new token, refreshed timestamps); a genuinely new device inserts a new row.
One user can hold many device rows (multi-device is fully supported, not
assumed away).

`storeId` is **resolved server-side**, never trusted from the request body:
`employee` → looked up via `store_staff_employees.employeeId`; `business` →
via `stores.userId`; `customer` → left `null`.

## 8. Process definitions added to `process-flow.json`

| Code | HandlerKey | Protected | allowedRoles |
|---|---|---|---|
| `nkt.notification.fcm.register_device` | `FCM_REGISTER_DEVICE` | ✔ | customer, business, employee |
| `nkt.notification.fcm.send` | `FCM_SEND_NOTIFICATION` | ✔ | business, employee |
| `nkt.notification.fcm.send_group` | `FCM_SEND_GROUP_NOTIFICATION` | ✔ | business |
| `nkt.notification.fcm.send_batch` | `FCM_SEND_BATCH_NOTIFICATION` | ✔ | business |

Note **`customer` is never in `allowedRoles` for any of the three send
operations** — `NktCoreService.validateRoles()` rejects the request before
`NktNotificationHandler` even runs, so a customer cannot call
`FCM_SEND_GROUP_NOTIFICATION` and blast every other customer (task
requirement #20). `FCM_REGISTER_DEVICE` deliberately has no `UserIdField`,
so `NktCoreService` does **not** remap an employee's `userId` to their store
owner's Mongo id (that remap only happens when `UserIdField` is set) — the
device row is always keyed by the real authenticated identity (the
`employeeId` for store staff), which is what lets per-employee targeting in
`FCM_SEND_BATCH_NOTIFICATION` work correctly.

## 9. Authorisation beyond `allowedRoles`

`allowedRoles` only checks the *role*, not *whose* store is being targeted.
`NktNotificationHandler` adds a second layer:

* **`FCM_SEND_NOTIFICATION` / batch entries** — if the request includes a
  `storeId`, it must equal the caller's own resolved store (via
  `store_staff_employees` for `employee`, `stores` for `business`).
* **`STORE_STAFF` / `STORE_OWNER` / `STORE` group sends** — the caller must
  own the `storeId` being targeted, checked the same way.
* **`CUSTOMERS` group send (platform-wide broadcast)** — see §10, this one
  needs special treatment because there's no admin role yet.
* **Revoked/released employees are never notified** — `STORE_STAFF` targeting
  and `OrderNotificationService` both filter `store_staff_employees` down to
  `status == "Active"` before resolving devices, so an employee your existing
  revoke/release flow has already marked non-`Active` is silently excluded.
  No new business logic was invented for this — it just respects whatever
  status your existing (or future) revoke/release handler sets.

## 10. The `CUSTOMERS` broadcast and the missing ADMIN role

Task requirement #20 asks for an `ADMIN` role that can send global/group
notifications. **This codebase has no `admin` userType or admin
login/JWT flow today** — only `customer`, `business`, `employee` — so adding
one outright would be exactly the kind of new, unrelated auth architecture
the brief says not to introduce.

Instead, broadcasting to **all customers** (the one truly platform-wide,
highest-blast-radius operation) is gated behind an explicit
`isPlatformAdmin: true` flag on the caller's own `stores` document. It's
`false`/absent by default and **not settable through any API** — only via
direct database access — so nobody can self-grant it. See
`NktNotificationHandler.callerIsPlatformAdmin()` for the single method this
lives in.

**Recommended follow-up** once you're ready to invest in it: add a real
`admin` userType with its own login (mirroring `store_staff_login`'s
register/login split), set `allowedRoles: ["admin"]` on
`nkt.notification.fcm.send_group`, and replace `callerIsPlatformAdmin()` with
`"admin".equalsIgnoreCase(callerUserType)`. Everything else in this
integration is unaffected by that change.

## 11. Batching, invalid tokens, and the send summary shape

`FirebaseNotificationService` never sends more than 500 devices in one
Firebase Admin SDK call (`MAX_BATCH_SIZE = 500`) — 1200 devices become three
calls (500 + 500 + 200), each accumulated into one summary:

```json
{ "totalDevices": 1250, "success": 1220, "failed": 30, "invalidTokens": 15 }
```

A token is only ever deactivated (`isActive:false`,
`inactiveReason:"FCM_TOKEN_INVALID"`) when Firebase reports
`UNREGISTERED` or `INVALID_ARGUMENT` — i.e. the token itself is dead. Any
other failure (`UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`,
`SENDER_ID_MISMATCH`) is transient/infra and leaves the device row alone, so
a temporary Firebase outage can never wipe out your device table. Dead
device rows are deactivated, **never deleted** — history stays queryable.

## 12. Notification history — recommendation

A `notifications` collection is implemented (see `NotificationDispatchService
.recordHistory()`), matching the shape in the brief:

```json
{
  "userId": "USER_001", "storeId": "ST011", "notificationType": "ORDER",
  "title": "Order Confirmed", "body": "Order ORD10001 confirmed",
  "data": { "orderId": "ORD10001", "screen": "order_details" },
  "status": "SENT", "sentAt": "...", "readAt": null, "createdAt": "..."
}
```

It is **not mandatory to keep** — it's a best-effort write (wrapped in its
own try/catch, never blocks a send) and is deliberately **skipped** for the
`CUSTOMERS` broadcast specifically, to avoid one write per customer on every
platform-wide announcement. If you don't plan to build an in-app
notification-inbox screen soon, you can delete the `recordHistory` calls
without touching anything else — every send path still works with the
`notifications` collection absent.

## 13. Logging

`FirebaseNotificationService.mask()` is used everywhere a token would
otherwise be logged: `xxxxx...ABCD` (first 6 + last 4 characters). No JWT
access/refresh token, no full FCM token, and no service-account credential
is ever written to a log line by this module.

## 14. Error codes (statusCode / errorCode / statusDesc)

Following the exact response convention already used by `NktAuthHandler` /
`NktOrderHandler` (`statusCode` + optional `errorCode` + `statusDesc`):

| errorCode | statusCode | When |
|---|---|---|
| `DEVICE_ID_REQUIRED` / `FCM_TOKEN_REQUIRED` / `PLATFORM_REQUIRED` | N400 | Missing registration fields |
| `TARGET_USER_REQUIRED` / `TITLE_BODY_REQUIRED` | N400 | Missing send fields |
| `GROUP_TYPE_REQUIRED` / `INVALID_GROUP_TYPE` / `STORE_ID_REQUIRED` | N400 | Group-send validation |
| `MESSAGES_REQUIRED` / `NO_VALID_TARGETS` | N400 | Batch-send validation |
| `NO_ACTIVE_DEVICE` | N404 | Target user has no active device |
| `FORBIDDEN` | N403 | Caller doesn't own the targeted store / isn't platform admin |
| `UNAUTHENTICATED` | N401 | No JWT-resolved userId |

Internal Firebase/config exceptions are never surfaced raw — every catch
block returns a clean `statusCode`/`statusDesc`, matching the existing
`GlobalExceptionHandler` convention.

## 15. Flutter-side request payloads

All requests go through your existing single endpoint:

```
POST /data
Content-Type: application/x-www-form-urlencoded (or multipart — same as every other nkt.* call)

code=nkt.notification.fcm.register_device
data={"deviceId":"DEVICE_001","fcmToken":"<token-from-FirebaseMessaging.instance.getToken()>","platform":"ANDROID","appVersion":"1.0.0","token":"<JWT access token>"}
```

`token` inside `data` is the existing convention this codebase already uses
for every protected `nkt.*` call — see `NktCoreService.extractUserId()`.

**Register/update device:**
```json
{ "deviceId": "DEVICE_001", "fcmToken": "xxxxx", "platform": "ANDROID", "appVersion": "1.0.0" }
```

**Single notification** (business/employee only):
```json
{
  "targetUserId": "USER_001", "title": "Order Confirmed",
  "body": "Your order ORD10001 has been confirmed",
  "notificationType": "ORDER", "orderId": "ORD10001", "storeId": "ST011", "screen": "order_details"
}
```

**Group notification** (business only):
```json
{
  "groupType": "STORE_STAFF", "storeId": "ST011", "title": "New Order",
  "body": "New order ORD10001 received", "notificationType": "ORDER",
  "orderId": "ORD10001", "screen": "order_details"
}
```

**Batch notification** (business only):
```json
{
  "messages": [
    { "targetUserId": "EMP001", "title": "Order Assigned", "body": "Order ORD1001 assigned to you", "notificationType": "ORDER", "orderId": "ORD1001", "storeId": "ST011", "screen": "order_details" },
    { "targetUserId": "EMP002", "title": "Order Assigned", "body": "Order ORD1002 assigned to you", "notificationType": "ORDER", "orderId": "ORD1002", "storeId": "ST011", "screen": "order_details" }
  ]
}
```

**On the Flutter side**, wire `FirebaseMessaging.onMessage` /
`onMessageOpenedApp` to read `message.data['type']`, `data['orderId']`,
`data['storeId']`, `data['screen']` and route to the matching screen — the
`data` payload is always exactly that shape (see §11 of the original brief),
built centrally by `NotificationDispatchService.dataPayload()`.

## 16. Postman

See `docs/postman_fcm_examples.json` — a ready-to-import collection with all
four operations pre-filled (register, single, group, batch), using your
existing `{{baseUrl}}` and `{{accessToken}}` variables.

## 17. Tests

`src/test/java/com/service/FirebaseNotificationServiceTest.java`,
`NotificationDispatchServiceTest.java` and
`src/test/java/com/service/handlers/NktNotificationHandlerTest.java` cover:
register new device, update existing device token, empty device list,
multiple devices for one user, invalid-token deactivation vs. transient
errors left alone, exactly-500 and >500 batching, unauthorised group-send
(wrong store), `CUSTOMERS` blocked without `isPlatformAdmin`, and
revoked/released employees excluded from `STORE_STAFF` sends. Run with:

```bash
./mvnw test -Dtest=*Fcm*,*Notification*
```

## 18. Deployment checklist (Render)

1. Merge these files into your existing `nktprocess` repo (no new project,
   no new module).
2. Set `FIREBASE_ENABLED`, `FIREBASE_CREDENTIALS_JSON`, `FIREBASE_PROJECT_ID`
   in Render → Environment (see §6).
3. Run `docs/mongo_indexes_fcm.js` against your Atlas cluster once.
4. Deploy as usual (`spring-boot-maven-plugin`, already configured — no
   build changes needed).
5. Smoke-test with Postman: register a device with a real Flutter-issued FCM
   token, then send yourself a single notification and confirm it arrives.
