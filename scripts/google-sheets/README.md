# Directory avatar copies

QTalk reads the optional `Аватар` column (`Avatar` / `Photo` also work) as a direct
HTTPS image URL. Existing directories without this column still use public Telegram
avatars. The URL is saved with the contact and new call-history records.

## Google-only synchronization

1. Add `Аватар` to the existing header row of your contact spreadsheet.
2. Open **Extensions → Apps Script** and paste `avatar-sync.gs`.
3. Run `testDriveAvatar` and authorize the script as the spreadsheet owner.
4. Verify the logged download URL returns an image without a Google sign-in.
5. Run `syncAvatars` for the initial import.
6. Run `installAvatarTrigger` once to install the daily refresh at approximately
   05:00 in Asia/Krasnoyarsk. Re-running it does not create duplicate triggers.

The script discovers the header and bound spreadsheet; no employee names, spreadsheet
URLs, account credentials or API tokens need to be committed. Timed runs use the
spreadsheet ID stored in Script Properties. It creates a private **QTalk avatars**
folder and shares only the copied image files as read-only with anyone holding the
link. Anyone with an image URL can download that image.

Only public Telegram avatars are available. A missing or restricted public avatar
is skipped. Transient errors leave the last good URL intact. Duplicate usernames
reuse the same file; a SHA-256 comparison prevents creating a new file when the photo
has not changed. Previous photo versions are retained rather than automatically
deleted. The script stops after four minutes to stay below the execution deadline;
the next daily/manual run can finish the remaining rows.

## Client cache

The app displays disk-cached images immediately, refreshes them after seven days,
and retains the last valid image if the network is down. Cache keys include the full
image URL, so a new photo URL is loaded immediately. Download size is limited to
5 MB; images must decode successfully and be no larger than 4096 × 4096 before they
replace a cache entry. Image requests and redirects require HTTPS.

Drive download links are suitable for a small directory, but remain subject to
Google access restrictions and download quotas. Verify accessibility from the
users' actual network; this setup removes their dependency on Telegram connectivity.
