# Employee presence deployment

Set `SPREADSHEET_ID` in the Apps Script project's Script Properties before deploying
`employee-presence.gs`. Keep the real spreadsheet ID and deployment URL outside the
repository.

The desktop client has no default presence endpoint. Configure the deployed script
URL in Settings > Database > Presence service. It is saved in local user preferences,
separately from the contact-directory URL. An empty endpoint disables remote presence
requests. The optional JVM property `qtalk.presenceUrl` supplies an initial endpoint
for managed deployments; the database settings can change it for the current session
and save the new value locally. Remove the JVM property to use that saved value on
subsequent launches.

This example assumes the employee sheet layout defined at the top of the script.
It does not implement authentication; review access controls before making the
deployment available to clients.
