# Employee presence deployment

Set `SPREADSHEET_ID` in the Apps Script project's Script Properties before deploying
`employee-presence.gs`. Keep the real spreadsheet ID and deployment URL outside the
repository.

The desktop client has no default presence endpoint. Set the JVM property
`qtalk.presenceUrl` explicitly for a deployment that uses this integration.
Without it, remote presence requests are disabled. The contact-directory URL is
configured separately in QTalk's database settings.

This example assumes the employee sheet layout defined at the top of the script.
It does not implement authentication; review access controls before making the
deployment available to clients.
