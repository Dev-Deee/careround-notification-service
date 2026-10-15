# careround-notification-service

Kafka consumer that turns CareRound events into notifications. HTTP 8087 (actuator and swagger only), DB `careround_notification_db`.

| Topic | Sends |
|---|---|
| `user.registered` / `user.verification_resent` | Activation email. The link is fetched from auth-service over gRPC; tokens never travel through Kafka. |
| `PATIENT_EMERGENCY_DECLARED` / `PATIENT_REVIEW_COMPLETED` | SMS (or email) to the patient's primary next-of-kin, with no clinical detail. |

Every notification is recorded in `sent_notifications`, keyed by the Kafka record key (the producer's outbox idempotency key). A redelivered or replayed event is therefore skipped ("already sent") rather than sent twice. Notifications that could not be sent are recorded with status `FAILED`.

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

`Alert`, `Escalation` and `NotificationTemplate` (and their repositories) are scaffolding for the deferred alert acknowledgement and escalation sweep (FR-7.3) and for templates and language preference. They are not dead code to delete.

## Postman

See `postman/` (on the `docs/postman-notification` branch until merged).
