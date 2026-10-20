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
