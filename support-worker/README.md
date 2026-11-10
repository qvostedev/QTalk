# QTalk support relay

This folder contains the Cloudflare Worker receiver. Telegram credentials and the
fixed destination are configured only as server-side secrets; they are never
included in this repository or the desktop application. The official desktop
build includes its public HTTPS receiver address and supports an environment
override for self-hosting. Tests use synthetic reports and a mock Telegram
upstream with network connections disabled; their receipts alone do not prove
real delivery. Deploy only to an explicitly approved account and Worker.

## Local checks (no account access)

Use Node 22 or newer. On Windows PowerShell, use `npm.cmd` to avoid execution-policy
issues with npm.ps1:

```powershell
npm.cmd ci --ignore-scripts --no-audit --no-fund
npm.cmd test
npm.cmd run typecheck
npm.cmd run bundle:local
```

The lockfile pins Wrangler 4 and all test dependencies. `bundle:local` uses esbuild
only; it does not log in, read Wrangler credentials, upload source maps, or deploy.
Miniflare runs the real Worker and SQLite Durable Object locally. Its mocked
upstream is injected only in the test harness. Production always calls the fixed
Telegram `sendDocument` endpoint and never accepts a caller URL, bot token or chat.

An additional opt-in contract check accepts actual multipart bytes captured from
`DesktopSupportServiceTest`, then runs them through the real local Worker/SQLite
runtime and the explicitly mocked Telegram upstream:

```powershell
$env:QTALK_SUPPORT_NATIVE_BODY_FIXTURE = (Resolve-Path '../shared/build/support-local/native-support-multipart.bin')
npm.cmd test
```

Without that synthetic JVM fixture, this extra test is explicitly skipped. No
real user logs or Telegram credentials are used.

The endpoint is anonymous: rate limits are abuse controls, not client
authentication. Before public rollout, review Cloudflare edge abuse protections
or a human-verification flow appropriate to the intended users. Never substitute
a shared API key embedded in the desktop application.

## Contract and delivery semantics

`POST /v1/reports` accepts exactly two multipart parts:

* `report`: a JSON string with exactly schemaVersion (1), reportId (UUID v4),
  title (3–160 trimmed characters), description (10–12000 trimmed characters),
  appVersion, platform (`windows`, `linux`, `macos`, `other`), createdAt (UTC ISO
  8601), and screenshotAttached (boolean).
* `diagnostics`: an `application/zip` File, at most 8 MiB, starting with ZIP local
  file magic. The filename is ignored. Archives are never extracted on the Worker.

The request stream has an 8 MiB + 160 KiB hard cap and a 15 second read deadline,
independent of Content-Length. Only two requests may parse/forward concurrently in
the one global coordinator. Invalid requests do not consume report quotas.

SQLite synchronously claims a report before any Telegram await. The coordinator
enforces 500 attempts per UTC day worldwide, 5 per IP/hour, and 20 per IP/day.
These are strict shared counters, not the approximate per-location native
RateLimit binding. This intentionally modest global service trades throughput for
worldwide coordination. Raw IPs are salted with a generated, private persisted
random value and SHA-256 hashed. Storage contains only counters, hashes, report
IDs, ticket IDs, timestamps and outcome codes, never archives or form text.

State expires after 30 days; quota buckets expire at the end of their UTC window.
Cleanup runs on requests and daily alarms; a 20000-report hard cap bounds storage. There is no
infinite idempotency guarantee after retention expires. Cloudflare's own backup
and account retention policies also apply.

A repeat ID with identical canonical metadata and ZIP bytes returns the saved
receipt when sent; a different payload returns `409 id_conflict`. In-flight
duplicates return `202 pending`. A pending record older than 90 seconds becomes
uncertain, never an automatic resend. Transport errors, malformed confirmations,
upstream 5xx and the 20 second upstream timeout persist `502 delivery_uncertain`.
Telegram has no request idempotency key: a timeout may mean delivery happened.
Never blindly create another report or reset the coordinator to bypass that state.
Check the support chat before arranging any manual recovery.

An explicit Telegram rejection is terminal `upstream_rejected`. A confirmed 429
is safely retryable manually under the same ID after its bounded Retry-After
(1–3600 seconds). No automatic retries occur. Only HTTP 200, Telegram `ok:true`,
a positive message ID and document file ID produce `{ok:true,ticketId,reportId}`.
The same human-readable ticket number is shown in the app and Telegram caption.
The server allocates new tickets using a permanent SQLite counter inside the same
transaction as the report claim. The API keeps `ticketId: QTK-00001` for client
compatibility; the application and Telegram display `№00001`. Duplicate retries
keep their original number. The counter is not removed by retention cleanup and
continues across restarts/deployments; failed/uncertain claims may leave gaps.
Old tickets retain their stored IDs so in-flight legacy reports remain retry-safe.
Telegram captions and attachment filenames no longer expose the internal UUID.
The single Telegram document uses a server-generated safe filename and plain
caption of at most 950 code points; full user text is in the ZIP.

## Operator setup — only with explicit deployment approval

These instructions are for a new/self-hosted receiver. Do not overwrite unrelated
Workers or borrow an existing unrelated bot.

1. Obtain a dedicated support bot from Telegram BotFather and a private support
   destination. Add that bot to the destination with the required document-posting
   permission. For forum topics, obtain the intended thread ID separately.
2. Confirm the account and choose a new, unused Worker name in `wrangler.jsonc`.
   Never overwrite an existing Worker or reuse its secrets. Review the
   SQLite migration. Do not put tokens or chat IDs in source or CLI arguments.
3. In this directory, run `npx wrangler login` interactively after approval.
4. Set secrets with Wrangler's interactive hidden-input prompts, one at a time:

   ```powershell
   npx wrangler secret put TELEGRAM_BOT_TOKEN
   npx wrangler secret put TELEGRAM_CHAT_ID
   # Only when using a topic:
   npx wrangler secret put TELEGRAM_MESSAGE_THREAD_ID
   ```

   Do not pipe literal secrets, use command-line values, paste them into chat,
   record the prompts, or commit `.dev.vars`. For a new Worker, Wrangler may
   require creating/deploying it before secrets can be attached. An unconfigured
   deployment fails closed with `503 missing_config` and accepts no reports.
5. Run checks again. With publication approval, run `npx wrangler deploy` to
   apply the SQLite migration and code. Observability and source-map upload are
   disabled in configuration. Do not enable verbose fetch tracing or log
   Telegram URLs, upstream exceptions, archives or user text.
6. For a self-hosted receiver, override the desktop's nonsecret
   `QTALK_SUPPORT_ENDPOINT` with the actual HTTPS URL ending in `/v1/reports`.
   Official builds already include the public receiver address. Restart only a separately
   launched review instance when testing; don't replace a running client.
7. Send one explicitly consented synthetic report and independently inspect its
   receipt and the support chat. Only that later check can establish real delivery.

For loopback development only, `QTALK_SUPPORT_ALLOW_LOCAL=1` enables a desktop
HTTP endpoint on localhost/127.0.0.1/::1. It never allows arbitrary HTTP hosts.
Do not set the opt-in for normal use. `.dev.vars.example` contains placeholders
only; secrets belong in ignored local files or server-side secret bindings.

Primary references used for the implementation:
[SQLite storage](https://raw.githubusercontent.com/cloudflare/cloudflare-docs/production/src/content/docs/durable-objects/api/sqlite-storage-api.mdx),
[Durable Object coordination](https://raw.githubusercontent.com/cloudflare/cloudflare-docs/production/src/content/docs/durable-objects/best-practices/rules-of-durable-objects.mdx),
[Telegram sendDocument](https://core.telegram.org/bots/api#senddocument).
