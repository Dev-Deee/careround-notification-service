# CareRound - Notification (Postman)

Import both files into Postman (**File → Import**, or drag them in), then pick the **CareRound - Notification** environment.

1. Set `superAdminPassword` in the environment (the seed admin's password; never commit it).
2. Run the folders in order. Each run uses a fresh `runId`, so emails never clash and the collection can be re-run.
   **00 - Bootstrap** creates a ward for the run and captures `wardPublicId`: patient admission and round creation
   now verify the ward with ward-service, so a made-up ward id returns 404.
3. At **MANUAL STEP - activate…**: set `doctorPassword` / `nursePassword` in the environment, then open each
   account's link from the notification-service console (`NOTIFICATION [EMAIL -> …] … Activate your account: <link>`)
   and submit that password on the page.
4. **MANUAL CHECK** steps tell you what to look for in the notification-service console and give the `psql` query.

Access tokens last 15 minutes. A collection-level pre-request script logs each role in again when its token is
missing or about to expire, and stops a request with a clear message when an id it needs (e.g. `{{patientPublicId}}`)
has not been captured yet, instead of sending `"null"`.

# CareRound - Alerts (Postman)

`CareRound-Alerts.postman_collection.json` + `CareRound-Alerts.postman_environment.json`: an emergency declared in
round-service becomes an Alert; the collection replays it (`GET /api/v1/wards/{wardId}/alerts`), checks the admin
unresolved-ward list, acknowledges it (and again, idempotently) and covers wrong-ward 403, admin access, 400s and 404.

1. Set `superAdminPassword`. **00 - Bootstrap** creates two real wards (`wardPublicId`, `otherWardPublicId`).
2. **01** creates a nurse and a doctor in the ward and a nurse in the other ward.
3. **MANUAL STEP**: set `nursePassword`, `doctorPassword`, `otherNursePassword` and activate the three accounts from
   the console links, as in the other collections.
4. Run **02 → 06**. The replay request waits 6 s for notification-service to consume the event.
5. **MANUAL CHECK** steps: with realtime-service running, a STOMP client on `/topic/wards/{{wardPublicId}}`
   (`careround-realtime-service/tools/ws-test.html`) shows `ALERT_RAISED`, then `ALERT_ACKNOWLEDGED`, then
   `ALERT_RESOLVED` after cleanup.

Under newman, run 00–01, activate the accounts, then 02–06 with the exported environment
(`--export-environment`).
