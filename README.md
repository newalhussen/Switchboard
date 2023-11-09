# Switchboard

A small feature-flag service: toggle features per environment and roll them out gradually, without redeploying.

## Features
- Create, edit, delete flags; enable/disable each flag independently in **development**, **staging** and **production**.
- **Percentage rollouts** with deterministic assignment: `SHA-256(flagKey:userId)` is mapped to one of 10,000 buckets, so a user always gets the same answer, raising the percentage only adds users, and different flags bucket users independently.
- Authenticated REST evaluation API. Each environment has its own SDK key; the key pins the evaluation to that environment.
- Audit history of every change (who, what, before/after), per flag and global.
- Admin UI (Next.js): flag matrix with toggles and rollout %, flag detail with history, SDK key rotation.
- OpenAPI docs: Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`.

## Architecture
```
frontend/ Next.js + Tailwind ──server-side proxy (admin token in httpOnly cookie)──▶ backend/ Spring Boot ──▶ PostgreSQL (Flyway)
your apps ──────────────────────── Bearer <SDK key> ──────────────────────────────▶ POST /api/v1/evaluate
```
Credentials: the **admin token** (`SWITCHBOARD_ADMIN_TOKEN`) manages flags; an **SDK key** can only evaluate. SDK keys are stored as SHA-256 hashes and shown once.

## Run locally
Prerequisites: JDK 21+, Node 20+, PostgreSQL 14+.
```bash
createdb switchboard
cd backend && cp .env.example .env     # set SWITCHBOARD_ADMIN_TOKEN (16+ chars) and DB settings
./mvnw spring-boot:run                 # API on :8080; first start logs one SDK key per environment
cd ../frontend && cp .env.example .env.local
npm install && npm run dev             # UI on http://localhost:3000, sign in with the admin token
```

## Evaluate a flag
```bash
curl -X POST localhost:8080/api/v1/evaluate -H "Authorization: Bearer $SDK_KEY" \
  -H "Content-Type: application/json" -d '{"flagKey":"new-checkout","userId":"user-42"}'
# {"flagKey":"new-checkout","value":true,"reason":"ROLLOUT_IN","environment":"development"}
curl "localhost:8080/api/v1/evaluate?userId=user-42" -H "Authorization: Bearer $SDK_KEY"   # all flags
```
Reasons: `DISABLED`, `ROLLOUT_ALL`, `ROLLOUT_NONE`, `ROLLOUT_IN`, `ROLLOUT_OUT`, `USER_REQUIRED` (partial rollout without a userId evaluates to false).

## Tests
`cd backend && ./mvnw test` runs the evaluator unit tests (determinism, monotonic rollout, distribution, independence).
`./mvnw test -Dtest=ApiIT` runs the end-to-end API test (auth, validation, evaluation, audit, key rotation) and needs a PostgreSQL
database `switchboard_test` (defaults `localhost:5434`, user `postgres`; override with `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`).

## Notes
- One shared admin token (no user accounts); the optional `X-Actor` header lets callers label audit entries. Put the UI behind HTTPS in production.
- Flags are read from the database on each evaluation; add caching if you need very high throughput.
