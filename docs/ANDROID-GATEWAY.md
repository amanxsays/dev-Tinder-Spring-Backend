# Authenticated web and Android AI gateway

Web and Android clients authenticate with the existing Node service. Node derives
the caller's identity, signs its request, and streams Spring's SSE response back
to the client. The AI routing/retrieval/Redis pipeline is unchanged.

## Configuration (server secrets only)

- `MONGODB_URI`: existing MongoDB connection string supplied securely through the
  environment (or Spring's existing `SPRING_MONGODB_URI` override). A credential
  previously embedded in tracked application configuration was removed from the
  current source. It remains in older Git history and must be rotated by the
  database owner before release. No history rewriting or live database changes
  were performed. The existing local value was preserved in ignored `.env`.

- `AI_GATEWAY_SECRET`: the same random secret, at least 32 UTF-8 bytes, in Node
  and Spring. This is separate from `JWT_KEY`; never include either in an APK or
  any `VITE_*` value.
- `AI_GATEWAY_REQUIRED`: defaults to `true`. Missing/short secrets fail closed
  with HTTP 503 for chat. Other services and the public `/api/ping` still work.
- `AI_GATEWAY_REQUIRED=false`: an explicit, temporary deployment compatibility
  window. Completely unsigned legacy requests are allowed, but partially signed
  or invalid signed requests are never accepted. Remove this window after the
  frontend has moved to Node's `/ai/chat/stream` endpoint.

Do not enable these settings in production until the coordinated Node/frontend
release is approved. Existing environment values and deployments were not changed
as part of local implementation.

## Node-to-Spring assertion

Node sends `POST /api/chat/stream` with the existing JSON body. Spring takes the
user identity from `X-DevTinder-User`, not the JSON `userId` value. Headers:

| Header | Value |
| --- | --- |
| `X-DevTinder-User` | Authenticated canonical 24-character lowercase ObjectId |
| `X-DevTinder-Timestamp` | UTC epoch seconds as decimal text |
| `X-DevTinder-Signature` | Lowercase hex HMAC-SHA256 |

The canonical string (LF separators, no trailing LF) is:

```text
POST
/api/chat/stream
<userId>
<timestamp>
<lowercase hex SHA256 of the original query's UTF-8 bytes>
```

Requests older than 60 seconds or more than 5 seconds ahead are rejected. Both
hosts need working clock synchronization. Query whitespace is signed exactly;
the gateway must not trim after signing. Query text must be nonblank and at most
16,000 Java UTF-16 code units (matching JavaScript string length).

The assertion is transport authentication over HTTPS, not an exactly-once
mechanism: a captured assertion can be replayed within its short validity window.
Never expose assertions to clients or logs, and never automatically retry AI POSTs.

## Rollout and rollback

1. Add the shared secret to the existing services with explicit operator approval.
2. Deploy the Node gateway and Spring verification with a short, explicit legacy
   compatibility window if required for website continuity.
3. Deploy the website using Node's authenticated streaming endpoint.
4. Enforce `AI_GATEWAY_REQUIRED=true`; test rejection of direct unsigned requests.
5. Release the Android APK only after that gate passes.

If gateway authentication fails, disable the AI entry point temporarily or repair
the coordinated release. Do not leave production chat in legacy mode as a fix.
Older open website tabs may need a reload after enforcement.

Health only proves HTTP availability, not Redis, Gemini, or quota availability.
Gemini 429/503 and disconnected streams remain explicit client-visible failures.
Stats/ingestion endpoint authorization is outside this narrowly scoped change.

## Isolated verification

Run with JDK 25 (without starting a production-connected application context):

```powershell
.\mvnw.cmd '-Dtest=AiServiceAssertionVerifierTests,ChatAuthenticationTests,FastChatControllerTests,HealthControllerTests,FastIntentServiceTests,RedisWorkerServiceTests' test
```

Tests cover unsigned/partial/tampered/expired/future requests, missing configuration,
an independent Node UTF-8 signature fixture, authoritative queued identity, and
preservation of the fast-intent bypass.
