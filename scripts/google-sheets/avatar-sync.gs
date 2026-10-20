/**
 * Bind this project to the contact spreadsheet (Extensions > Apps Script).
 * Only public Telegram photos are copied. No bot token or Cloudflare account is needed.
 * Run testDriveAvatar first, then syncAvatars, then installAvatarTrigger.
 * The first run asks the owner to authorize Sheets, Drive and external requests.
 */
function testDriveAvatar() {
  const sheet = avatarSheet_();
  const rows = sheet.getDataRange().getDisplayValues();
  const header = avatarHeader_(rows);
  const row = rows.findIndex((values, index) =>
    index > header.row && telegramUsername_(values[header.telegram]));
  if (row < 0) throw new Error('No contact with a valid Telegram username');
  const folder = avatarFolder_();
  const result = updateAvatar_(sheet, row + 1, header.avatar + 1,
    telegramUsername_(rows[row][header.telegram]), folder);
  console.log(JSON.stringify(result));
}

function syncAvatars() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) return;
  try {
    const sheet = avatarSheet_();
    const rows = sheet.getDataRange().getDisplayValues();
    const header = avatarHeader_(rows);
    const folder = avatarFolder_();
    const started = Date.now();
    const properties = PropertiesService.getScriptProperties();
    const seen = {};
    let updated = 0;
    let failed = 0;
    for (let i = header.row + 1; i < rows.length; i++) {
      if (Date.now() - started > 240000) break;
      const username = telegramUsername_(rows[i][header.telegram]);
      if (!username) continue;
      try {
        let result = seen[username];
        if (!result) {
          const previous = JSON.parse(properties.getProperty('avatar.' + username) || '{}');
          if (previous.url && Date.now() - (previous.checkedAt || 0) < 86400000) {
            result = previous;
          } else {
            result = updateAvatar_(sheet, i + 1, header.avatar + 1, username, folder);
            updated++;
          }
          seen[username] = result;
        }
        if (result.url && rows[i][header.avatar] !== result.url) {
          sheet.getRange(i + 1, header.avatar + 1).setValue(result.url);
        }
      } catch (error) {
        failed++;
        console.warn(username + ': ' + String(error.message).slice(0, 180));
        // Keep the last good URL on transient network/Google errors.
      }
    }
    console.log('Updated: ' + updated + '; failed: ' + failed);
  } finally {
    lock.releaseLock();
  }
}

function installAvatarTrigger() {
  avatarSheet_(); // Saves the bound spreadsheet ID for timed runs.
  const exists = ScriptApp.getProjectTriggers().some(t => t.getHandlerFunction() === 'syncAvatars');
  if (!exists) ScriptApp.newTrigger('syncAvatars').timeBased().everyDays(1).atHour(5)
    .inTimezone('Asia/Krasnoyarsk').create();
  console.log(exists ? 'Daily trigger already exists' : 'Daily trigger created');
}

function avatarSheet_() {
  const properties = PropertiesService.getScriptProperties();
  const active = SpreadsheetApp.getActiveSpreadsheet();
  const id = active ? active.getId() : properties.getProperty('spreadsheetId');
  if (!id) throw new Error('Open this script from the contact spreadsheet');
  properties.setProperty('spreadsheetId', id);
  const spreadsheet = active || SpreadsheetApp.openById(id);
  const sheet = spreadsheet.getSheets().find(s => {
    const rows = s.getRange(1, 1, Math.min(s.getLastRow(), 10), s.getLastColumn()).getDisplayValues();
    return rows.some(r => r.some(v => /^(telegram|телеграм)$/i.test(v.trim())));
  });
  if (!sheet) throw new Error('Telegram column not found');
  return sheet;
}

function avatarHeader_(rows) {
  const row = rows.findIndex(r => r.some(v => /^(telegram|телеграм)$/i.test(v.trim())));
  if (row < 0) throw new Error('Telegram column not found');
  const telegram = rows[row].findIndex(v => /^(telegram|телеграм)$/i.test(v.trim()));
  const avatar = rows[row].findIndex(v => /^(аватар|avatar|avatar url|avatarurl)$/i.test(v.trim()));
  if (avatar < 0) throw new Error('Add an Аватар column to the header first');
  return {row, telegram, avatar};
}

function avatarFolder_() {
  const properties = PropertiesService.getScriptProperties();
  const id = properties.getProperty('avatarFolderId');
  if (id) return DriveApp.getFolderById(id);
  const folder = DriveApp.createFolder('QTalk avatars');
  // Folder remains private; only individual copied images are shared by link.
  properties.setProperty('avatarFolderId', folder.getId());
  return folder;
}

function telegramUsername_(value) {
  const normalized = String(value || '').trim().replace(/^(?:https?:\/\/)?t\.me\//i, '').replace(/^@/, '');
  return /^[a-z0-9_]{5,32}$/i.test(normalized) ? normalized.toLowerCase() : '';
}

function updateAvatar_(sheet, row, column, username, folder) {
  const profile = avatarFetch_('https://t.me/' + username);
  const tags = profile.getContentText().match(/<meta\b[^>]*>/gi) || [];
  const tag = tags.find(t => /(?:property|name)=["']og:image["']/i.test(t));
  const match = tag && tag.match(/content=["']([^"']+)["']/i);
  if (!match || /\/img\/t_logo/i.test(match[1])) throw new Error('No public avatar');
  const url = match[1].replace(/&amp;/g, '&');
  if (!/^https:\/\/(?:cdn\d*\.telesco\.pe|[^/]+\.telegram-cdn\.org)\//i.test(url)) {
    throw new Error('Unexpected Telegram image host');
  }
  const response = avatarFetch_(url);
  const blob = response.getBlob();
  const bytes = blob.getBytes();
  const type = blob.getContentType().split(';')[0];
  if (!/^image\/(jpeg|png|webp)$/.test(type) || !bytes.length || bytes.length > 5000000) {
    throw new Error('Unsupported or oversized image');
  }
  const hash = Utilities.base64EncodeWebSafe(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, bytes));
  const properties = PropertiesService.getScriptProperties();
  const previous = JSON.parse(properties.getProperty('avatar.' + username) || '{}');
  let file;
  if (previous.hash === hash && previous.fileId) {
    try { file = DriveApp.getFileById(previous.fileId); } catch (_) {}
    if (file && file.isTrashed()) file = null;
  }
  if (!file) {
    const extension = type === 'image/jpeg' ? 'jpg' : type.split('/')[1];
    file = folder.createFile(blob.setName(username + '-' + hash.slice(0, 10) + '.' + extension));
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  }
  let downloadUrl = 'https://drive.google.com/uc?export=download&id=' + encodeURIComponent(file.getId());
  const resourceKey = file.getResourceKey();
  if (resourceKey) downloadUrl += '&resourcekey=' + encodeURIComponent(resourceKey);
  const result = {fileId:file.getId(), hash, url:downloadUrl, checkedAt:Date.now()};
  properties.setProperty('avatar.' + username, JSON.stringify(result));
  sheet.getRange(row, column).setValue(downloadUrl);
  return result;
}

function avatarFetch_(url) {
  const response = UrlFetchApp.fetch(url, {muteHttpExceptions:true, followRedirects:true});
  if (response.getResponseCode() !== 200) throw new Error('HTTP ' + response.getResponseCode());
  return response;
}

