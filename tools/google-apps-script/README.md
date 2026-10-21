# Employee presence deployment

Set `SPREADSHEET_ID` in the Apps Script project's Script Properties before deploying
`employee-presence.gs`. Keep the real spreadsheet ID and deployment URL outside the
repository.

The desktop client has no default presence endpoint and no separate endpoint field.
In the contact spreadsheet, create a sheet named `QTalkConfig`. Put `key,value` in
the first row and `presence_url,<deployed script URL>` in the second row (two cells).
The client reads this sheet when synchronizing the directory and caches the endpoint
locally. All employees keep the same directory link. An absent or empty `presence_url`
disables presence requests. Only HTTPS Google Apps Script `/exec` URLs are accepted.
Hide the sheet if desired; hiding is not an access-control mechanism. The endpoint is
visible to readers of the spreadsheet, so it must not be treated as a secret.

This example assumes the employee sheet layout defined at the top of the script.
It does not implement authentication; review access controls before making the
deployment available to clients.

## Temporary conference signalling

Add `conference-rooms.gs` to the same project; the `doPost` room dispatcher in
`employee-presence.gs` must also be deployed. Run `setupConferenceRooms` once.
It adds only `QTalkRooms` with six service columns. Existing contact/status tabs
and `QTalkConfig` remain unchanged. For older deployments the existing server-side
`SPREADSHEET_ID` constant is accepted as a fallback to Script Properties.

Update the existing web deployment to a new version without changing its URL or
access permissions. Every participant needs the new client. The host publishes
membership every three seconds; guests find a room by their current SIP peer and
own extension and publish their microphone flag. No audio, passwords or profile
data are sent. Ended rooms are deleted; expired rooms are cleaned on the next
request after 45 seconds without a host heartbeat. No scheduled trigger is needed.

Run `node tools/google-apps-script/conference-rooms.test.cjs` for isolated storage
tests. This transport deliberately inherits the existing endpoint's anonymous
trust model; extension claims are not authenticated. Do not treat spreadsheet
sharing, hidden tabs, or an owner token as employee authentication. See
`docs/conferencing.md` before deploying outside the temporary test environment.
