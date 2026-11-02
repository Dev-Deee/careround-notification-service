# careround-notification-service

Kafka consumer that turns CareRound events into notifications. HTTP 8087 (actuator and swagger only), DB `careround_notification_db`.

| Topic | Sends |
|---|---|
| `user.registered` / `user.verification_resent` | Activation email. The link is fetched from auth-service over gRPC; tokens never travel through Kafka. |
| `PATIENT_EMERGENCY_DECLARED` / `PATIENT_REVIEW_COMPLETED` | SMS (or email) to the patient's primary next-of-kin, with no clinical detail. |
| `PATIENT_EMERGENCY_DECLARED` | Also saves an `Alert` and publishes `ALERT_RAISED` to Redis for realtime-service (below). |
| `PATIENT_EMERGENCY_RESOLVED` | No SMS, no row: publishes `ALERT_RESOLVED` to Redis. |
| `MEDICATION_OVERDUE` | Two **targeted** CRITICAL alerts (must be acknowledged): the prescriber, and the ward's active charge nurse. No SMS/email. |
| `TASK_OVERDUE` | One HIGH **ward** alert (must be acknowledged). No SMS/email. |
| `MEDICATION_ADMINISTERED` | One **targeted** INFO alert to the prescriber (`requiresAck=false`). No SMS/email. |

Every notification is recorded in `sent_notifications`, keyed by the Kafka record key (the producer's outbox idempotency key). A redelivered or replayed event is therefore skipped ("already sent") rather than sent twice. Notifications that could not be sent are recorded with status `FAILED`.

## Alerts and Redis

On `PATIENT_EMERGENCY_DECLARED` the next-of-kin SMS goes first, then an `Alert` is saved with `sourceEventId` = the
Kafka record key, and `ALERT_RAISED` is published to the Redis channel `app.alerts.redis-channel` (default
`careround.alerts`) after the transaction commits. realtime-service pushes it to `/topic/wards/{wardId}`.

- `wardId` comes from the event; if it is null, patient-service `PatientExists` (`ward_id`) is asked. If it is still
  unknown the alert has `wardUnresolved=true`, `wardId` null, and realtime routes it to `/topic/admin/alerts`.
- A redelivered or replayed event finds its `sourceEventId` and creates/publishes nothing.
- The alert is raised even if the SMS step fails (e.g. patient-service down); the SMS failure is then rethrown so the
  record is still retried / dead-lettered as before.
- Redis is fire-and-forget (2 s timeout). A failed publish is logged at ERROR and never fails the listener; the Alert
  row stays and clients recover it from the replay endpoint.

### Targeted alerts (Phase 10a)

`Alert` adds `targetUserId` (null = ward alert), `severity` (`CRITICAL | HIGH | INFO`), `requiresAck`,
`taskPublicId` and `acknowledgedAsAdmin`. All are nullable; rows from before read as `CRITICAL`, `requiresAck=true`
(they are all emergencies). A targeted alert is pushed only to `/topic/users/{targetUserId}`, never to the ward topic.

| Event | Alerts (`sourceEventId`) | Severity / ack |
|---|---|---|
| `MEDICATION_OVERDUE` | `<key>:doctor` → `prescribedBy`; `<key>:charge` → the ward's active charge nurse (ward-service `GetActiveChargeNurse`, 5 s deadline) | CRITICAL, requiresAck |
| `TASK_OVERDUE` | `<key>`, ward alert | HIGH, requiresAck |
| `MEDICATION_ADMINISTERED` | `<key>` → `prescribedBy` | INFO, no ack needed |

- One alert per recipient, each keyed `<record key>:<recipient>`, so the unique `sourceEventId` keeps redelivery
  idempotent without new constraints.
- Missing `wardId` → `PatientExists` fallback as above. If the ward is still unknown, or ward-service fails, or nobody
  holds charge, the charge-nurse alert is skipped with a WARN and the prescriber's alert is still raised; the listener
  never fails on these lookups. A targeted alert is not `wardUnresolved` (it still reaches its recipient); a
  `TASK_OVERDUE` without a ward is, and goes to admins.

**Enum CHECK constraints.** Hibernate writes `CHECK (col IN (...))` when it creates an enum column and `ddl-auto`
never widens it. At startup `EnumCheckConstraintVerifier` compares `alerts_type_check`, `alerts_escalation_state_check`
and `alerts_severity_check` with the enums and logs **ERROR** with the exact SQL if a value is missing (startup is
not blocked). Databases created before Phase 10a need, once:

```sql
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS alerts_type_check, ADD CONSTRAINT alerts_type_check CHECK (type IN ('PATIENT_EMERGENCY_DECLARED', 'MEDICATION_OVERDUE', 'ACUITY_ESCALATION', 'STAFF_REACHABILITY_NUDGE', 'TASK_OVERDUE', 'MEDICATION_ADMINISTERED')), DROP CONSTRAINT IF EXISTS alerts_severity_check, ADD CONSTRAINT alerts_severity_check CHECK (severity IN ('CRITICAL', 'HIGH', 'INFO'));
```

Message (`schemaVersion: 1`, additive only, ids only):

```json
{ "schemaVersion": 1, "kind": "ALERT_RAISED | ALERT_RESOLVED | ALERT_ACKNOWLEDGED",
  "alertId": "<Alert.publicId, null for ALERT_RESOLVED>", "eventId": "<Kafka record key>",
  "type": "PATIENT_EMERGENCY_DECLARED", "wardId": "<ward publicId or null>", "patientId": "<patient publicId>",
  "occurredAt": "<ISO-8601>", "acknowledgedBy": "<userId or null>",
  "targetUserId": "<userId or null>", "severity": "CRITICAL | HIGH | INFO", "requiresAck": true,
  "taskPublicId": "<care task publicId or null>" }
```

## Alert REST (replay and acknowledge)

JWT as in the other services. Responses carry ids and state only (`publicId, type, wardId, patientId, createdAt,
acknowledgedBy, acknowledgedAt, escalationState, targetUserId, severity, requiresAck, taskPublicId,
acknowledgedAsAdmin`).

| Endpoint | Who | |
|---|---|---|
| `GET /api/v1/wards/{wardId}/alerts?since=<ISO-8601>&unacknowledgedOnly=<bool>` | DOCTOR/NURSE of that ward (token `assignedWardId`), ADMIN any ward | **ward alerts only** (`targetUserId` null), newest first, max 200, default last 24 h. How a tablet recovers alerts it missed (Redis keeps nothing) |
| `GET /api/v1/alerts/mine?since=&unacknowledgedOnly=` | any DOCTOR/NURSE/ADMIN | the caller's own targeted alerts, same window and cap. How a doctor who was offline recovers a medication alert |
| `GET /api/v1/alerts/unresolved-ward` | ADMIN | alerts with `wardUnresolved=true`, not acknowledged |
| `POST /api/v1/alerts/{publicId}/acknowledge` | ward alert: DOCTOR/NURSE of the alert's ward, ADMIN (only ADMIN when it has no ward). Targeted alert: only its target, or ADMIN (stored as `acknowledgedAsAdmin=true`, logged `AUDIT admin …`); anyone else 403. `requiresAck=false` alerts can be acknowledged too (dismiss) | first ack records who/when, `escalationState=ACKNOWLEDGE`, publishes `ALERT_ACKNOWLEDGED`; a repeat returns the original ack and publishes nothing. Unknown → 404 |

Escalation timers (FR-7.3) are not implemented yet.

## Logging

Recipients are masked in every log line (`0806***0001`, `a***@host.com`). The console delivery line (standing in for
the SMS/email provider) is `NOTIFICATION [<channel> -> <masked>] key=<idempotency key>: <body>`; the body (it holds
the activation link developers copy) is logged only while `app.notification.log-delivery-payload=true` (the dev
default). **Set it to `false` in production**: the line then carries only channel, masked recipient and key. Tokens
are never logged at WARN/ERROR.

## Failed events and the `-dlt` topics

When a listener fails, the record is retried 4 times (1 s, 2 s, 4 s, 8 s) and then published to `<topic>-dlt`, for example `PATIENT_EMERGENCY_DECLARED-dlt`. Unparseable JSON and events without an id go there straight away, without retries. **Nothing consumes the `-dlt` topics.** Reprocessing is manual, and replaying is safe: a record that has been sent since is skipped.

**1. See what is there (key | value):**

```bash
docker exec careround-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic PATIENT_EMERGENCY_DECLARED-dlt --from-beginning --timeout-ms 5000 --formatter-property print.key=true --formatter-property key.separator='|'
```

**2. See why each one failed** (the headers also hold binary values, hence `strings`):

```bash
docker exec careround-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic PATIENT_EMERGENCY_DECLARED-dlt --from-beginning --timeout-ms 5000 --formatter-property print.headers=true --formatter-property print.value=false | strings | grep -oE 'kafka_dlt-exception-cause-fqcn:[^,]+'
```

- A transient cause such as `io.grpc.StatusRuntimeException` (patient or auth service down) can be replayed once the dependency is back.
- A poison message (`JsonParseException`, `IllegalArgumentException`) fails again until the producer data is fixed. Don't replay it.

**3. Replay** the records you want onto the **original** topic, with the same key. Keep only the wanted lines from step 1, in a file such as `replay.txt`:

```bash
docker exec -i careround-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic PATIENT_EMERGENCY_DECLARED --reader-property parse.key=true --reader-property key.separator='|' < replay.txt
```

Then confirm in the console (`NOTIFICATION [...]` or `already sent`) and in the DB:

```bash
docker exec -it careround-postgres psql -U postgres -d careround_notification_db -c "SELECT idempotency_key, channel, status, sent_at FROM sent_notifications ORDER BY sent_at DESC LIMIT 5;"
```

The `-dlt` topics keep records for Kafka's retention period (7 days by default), so reprocess within that window.

## Not used yet

`Escalation` and `NotificationTemplate` (and their repositories) are scaffolding for the deferred escalation sweep (FR-7.3) and for templates and language preference. They are not dead code to delete.

## Postman

See `postman/` (on the `docs/postman-notification` branch until merged).

## Admin: delivery log

`GET /api/v1/admin/notifications?status&channel&page&size` and `GET /api/v1/admin/notifications/failed?channel&page&size`
(= `status=FAILED`) — any ADMIN; routed by the gateway. Newest `sentAt` first; `page` 0-based, `size` 20 (max 100);
`{items, page, size, total}`. Items: `id`, `idempotencyKey`, `eventType`, `channel`, `status`, `recipientMasked`
(`0806***0001`, `a***@host.com`), `failureReason`, `sentAt`. Message bodies are never stored, so never returned.
`failureReason` is recorded for failures from this version on (older FAILED rows have null).

`PATIENT_REVIEW_COMPLETED` is read with the new `roundPublicId`; its `roundId` (round internal id) is
**deprecated**.

PRD `POST /admin/notifications/{id}/resend` is not built yet (not in this phase); the list is the read half.

## Errors

Every error uses the body `{"status", "error", "message", "timestamp"}`.

- A path no endpoint maps (e.g. a collection root with a trailing slash, or a wrong sub-path) → **404**
  `No endpoint <METHOD> <path>`. A wrong HTTP method on a mapped path → 405, unsupported content type → 415,
  malformed JSON → 400.
- Anything unexpected → 500 with a generic message; the exception is logged at ERROR with its stack trace.

## Environment variables

Copy `.env.example` to `.env` (gitignored) and fill in real values. `main()` loads `.env` into system properties;
plain environment variables work too.

| Variable | Purpose |
|---|---|
| `DB_USERNAME` | Postgres user (root docker-compose: careround-postgres) |
| `DB_PASSWORD` | Postgres password |
| `JWT_PUBLIC_KEY` | RSA public key used to verify access tokens; same value in every service. PEM on one line, `|` for newlines |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka (root docker-compose: careround-kafka) |

## Run

Needs the root `docker-compose.yml` stack (Postgres, Kafka, Redis) and a `.env`.

```bash
./mvnw clean package -DskipTests && java -jar target/careround-notification-service-0.0.1-SNAPSHOT.jar
```

Clients reach the REST API through the gateway on `http://localhost:8080`; the service port also works directly.
