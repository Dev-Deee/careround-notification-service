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
