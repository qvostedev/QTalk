# Support feature and isolated review

Open **Settings → Technical support** in the desktop app. This is a dedicated
scrollable panel with Back, a choice between **Report a problem** and
**Suggest an improvement**, title, description and optional explicit PNG/JPEG
selection. Cancelling the image chooser retains the previous attachment. No
desktop capture occurs. Drafts live in memory for the running app session,
including leaving/re-entering Settings; they are not saved in account/preferences
storage.

Send requires 3–160 trimmed title characters and 10–12000 description characters.
A problem report carries a bounded, cleaned diagnostics archive; an improvement
suggestion is sent with no logs and no system information. Busy actions block
repeat clicks. Errors retain the draft.
Only a validated matching service receipt displays a ticket. New tickets use a
persistent sequential counter: users see `№00001` in the app and Telegram, and
`QTalk-00001.zip` as the attachment name. The API retains its `QTK-` prefix and
internal report UUID for compatibility and deduplication; neither is shown in new
Telegram captions. Retrying does not consume a new number; rejected/uncertain
attempts may leave gaps, and report retention cleanup does not reset the counter.
Confirmed reports
cannot be resent by another click; start a new report intentionally. Uncertain
retries reuse the one original UUID, metadata and ZIP bytes. Editing the content
creates a new snapshot. The retry snapshot is held only in memory: restarting the
app loses it. After uncertainty, check with support before making a new report.

The desktop includes the public HTTPS support receiver address; no account or bot
credentials are shipped. Normal installed builds work without a custom launcher.
`QTALK_SUPPORT_ENDPOINT` overrides the address; an explicitly empty value disables
sending. `QTALK_SUPPORT_ALLOW_LOCAL=1` permits loopback-only HTTP for development.
There is no local ZIP export button; the Android/default implementation reports
unsupported behavior honestly.

Diagnostics, included only for problem reports, contain report.json (the user-authored
form), allowlisted system.json, manifest.json, cleaned text logs and optional
metadata-stripped screenshot.png. An improvement suggestion produces report.json,
manifest.json and the optional screenshot only; the collector is not run at all.
The collector considers only exact `qtalk-[0..3].log` and
`qtalk-app-[0..3].log` files in the platform log directory:

* Windows: `%LOCALAPPDATA%/QTalk/logs` (with a user-home fallback).
* Linux/NixOS: `$XDG_STATE_HOME/QTalk/logs`, or `~/.local/state/QTalk/logs`.
* macOS: `~/Library/Logs/QTalk`.

Handoff and application logging share this policy; unrelated Windows environment
variables are ignored on Linux. The Nix install-method marker is recorded without
dumping environment variables.
It does not recurse, follow symlinks or Windows directory junctions (including
ancestors), or read preferences, databases, recordings,
contacts, environment dumps, machine/user names or process arguments. Collection
is bounded to 4 MiB raw tails, 512 KiB per log, 24 entries and an 8 MiB final ZIP.
The manifest records omitted/truncated/unreadable candidate logs. Native SIP
debug collection is deliberately deferred. Missing or unsafe directories are not traversed.

QTalk's own JUL logger rotates four 256 KiB files, cleans before persistence, and
does not intercept stderr or enable third-party DEBUG. Handoff records also clean
identifiers before persistence. Export cleans again, conservatively omitting raw
SIP/auth blocks and masking credentials, tokens, URLs and common identifiers.
Known account secret literals are provided at runtime by an in-memory callback;
no tool reads account data during implementation/tests. Cleaning is best effort,
not a guarantee of anonymization. User text and chosen images are not semantically
scrubbed; review them before sending.

Images must have PNG/JPEG magic, be at most 4 MiB, have dimensions no larger than
16384 on either axis and at most 16 million pixels before decoding. Re-encoding
to PNG strips metadata and also enforces a 4 MiB output bound. The image chooser
runs on the AWT event thread, while file reads, image processing, ZIP preparation
and HTTP work avoid Compose's UI thread. No automatic screenshot is performed.

For isolated visual review, launch the built app image with `--support-preview`.
This early entrypoint opens the real form without the dial bridge, SIP, account
stores, user preferences, updater, or real log collection. It has no remote
endpoint, and its local ZIP contains only allowlisted system metadata and the
explicitly entered text/image. It does not touch the running installed QTalk.

Build and test from the repository root, with JDK 21 and the project build prerequisites configured:

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test --no-configuration-cache
.\gradlew.bat :desktopApp:packageQTalkInstaller '-PqtalkVersion=1.1.3-review' --no-configuration-cache
```

For source-only NixOS review (not executed on a real NixOS host here):

```sh
nix develop --command bash ./gradlew :desktopApp:run --args=--support-preview -PqtalkVersion=1.1.3-review --no-configuration-cache
```

Use Gradle for the uncommitted source build: plain `nix run` still selects the
published application archive, not these local changes. The preview does not
load live SIP settings or collect real logs. To test the full application after
review, close the older instance and omit `--args=--support-preview`.

The canonical release version comes from root `qtalkVersion`. Review overrides are
command-local and do not change that file. Built packages, test results and other
local evidence stay in ignored build directories, not in the published sources.
Cloudflare setup and limitations are documented in
[the relay README](../support-worker/README.md).
