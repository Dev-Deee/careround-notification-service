# careround-notification-service

Kafka consumer that turns CareRound events into notifications. HTTP 8087 (actuator and swagger only), DB `careround_notification_db`.

| Topic | Sends |
|---|---|
| `user.registered` / `user.verification_resent` | Activation email. The link is fetched from auth-service over gRPC; tokens never travel through Kafka. |
| `PATIENT_EMERGENCY_DECLARED` / `PATIENT_REVIEW_COMPLETED` | SMS (or email) to the patient's primary next-of-kin, with no clinical detail. |
| `PATIENT_EMERGENCY_DECLARED` | Also saves an `Alert` and publishes `ALERT_RAISED` to Redis for realtime-service (below). |
| `PATIENT_EMERGENCY_RESOLVED` | No SMS, no row: publishes `ALERT_RESOLVED` to Redis. |

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

Message (`schemaVersion: 1`, additive only, ids only):

```json
{ "schemaVersion": 1, "kind": "ALERT_RAISED | ALERT_RESOLVED | ALERT_ACKNOWLEDGED",
  "alertId": "<Alert.publicId, null for ALERT_RESOLVED>", "eventId": "<Kafka record key>",
  "type": "PATIENT_EMERGENCY_DECLARED", "wardId": "<ward publicId or null>", "patientId": "<patient publicId>",
  "occurredAt": "<ISO-8601>", "acknowledgedBy": "<userId or null>" }
```

## Alert REST (replay and acknowledge)

JWT as in the other services. Responses carry ids and state only (`publicId, type, wardId, patientId, createdAt,
acknowledgedBy, acknowledgedAt, escalationState`).

| Endpoint | Who | |
|---|---|---|
| `GET /api/v1/wards/{wardId}/alerts?since=<ISO-8601>&unacknowledgedOnly=<bool>` | DOCTOR/NURSE of that ward (token `assignedWardId`), ADMIN any ward | newest first, max 200, default last 24 h. How a tablet recovers alerts it missed (Redis keeps nothing) |
| `GET /api/v1/alerts/unresolved-ward` | ADMIN | alerts with `wardUnresolved=true`, not acknowledged |
| `POST /api/v1/alerts/{publicId}/acknowledge` | DOCTOR/NURSE of the alert's ward, ADMIN (only ADMIN when the alert has no ward) | first ack records who/when, `escalationState=ACKNOWLEDGE`, publishes `ALERT_ACKNOWLEDGED`; a repeat returns the original ack and publishes nothing. Unknown → 404 |

Escalation timers (FR-7.3) are not implemented yet.

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
