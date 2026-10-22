// Temporary signalling storage. No audio, contact names, or SIP credentials.
const ROOM_SHEET = 'QTalkRooms';
const ROOM_TTL_MS = 45000;
const ROOM_HEADERS = ['room_id', 'host', 'updated_at', 'owner_hash', 'snapshot', 'microphones'];

function roomSpreadsheet() {
  const id = PropertiesService.getScriptProperties().getProperty('SPREADSHEET_ID') ||
    (typeof SPREADSHEET_ID !== 'undefined' ? SPREADSHEET_ID : '');
  if (!id) throw new Error('Set SPREADSHEET_ID in Script Properties');
  return SpreadsheetApp.openById(id);
}

function setupConferenceRooms() {
  const book = roomSpreadsheet();
  let sheet = book.getSheetByName(ROOM_SHEET);
  if (!sheet) sheet = book.insertSheet(ROOM_SHEET);
  sheet.getRange(1, 1, 1, ROOM_HEADERS.length).setValues([ROOM_HEADERS]);
  sheet.setFrozenRows(1);
  sheet.getRange(1, 1, 1, ROOM_HEADERS.length).setFontWeight('bold');
  sheet.setColumnWidths(1, 4, 160);
  sheet.setColumnWidth(5, 360);
  return sheet;
}

function roomHash(value) {
  return Utilities.base64EncodeWebSafe(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, value));
}

function roomSnapshot(body) {
  if (typeof body !== 'string' || body.length > 4096) return null;
  const p = body.split('\n');
  const number = /^\+?[0-9]{1,20}$/;
  if (![9,10].includes(p.length) || p[0] !== 'QTALK/1' || p[1] !== 'snapshot' ||
      !/^[a-fA-F0-9-]{36}$/.test(p[2]) || !/^\d{1,15}$/.test(p[3]) || !number.test(p[4])) return null;
  if (p.length === 10 && !/^[0-9.]{7,15}:[0-9]{1,5}\/[a-f0-9-]{36}$/.test(p[9])) return null;
  const lists = p.slice(5,9).map(line => line ? line.split(',') : []);
  const members = lists[0], pending = lists[1];
  if (!members.includes(p[4]) || new Set(members).size !== members.length ||
      new Set(members.concat(pending)).size > 8 || lists.flat().some(n => !number.test(n)) ||
      lists[2].concat(lists[3]).some(n => !members.includes(n))) return null;
  return {parts: p, id: p[2], host: p[4], revision: Number(p[3]), members, pending};
}

function roomRequest(p) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) return textResponse('retry');
  try {
    const sheet = roomSpreadsheet().getSheetByName(ROOM_SHEET);
    if (!sheet) return textResponse('unavailable');
    const now = Date.now();
    // Expired rooms are removed on every request; no additional trigger is needed.
    let rows = sheet.getLastRow() > 1 ? sheet.getRange(2, 1, sheet.getLastRow() - 1, 6).getValues() : [];
    for (let i = rows.length - 1; i >= 0; i--) {
      if (now - Number(rows[i][2]) > ROOM_TTL_MS) { sheet.deleteRow(i + 2); rows.splice(i, 1); }
    }
    if (p.role === 'host') {
      if (!/^[a-fA-F0-9-]{36}$/.test(p.token || '')) return textResponse('invalid');
      const snapshot = roomSnapshot(p.snapshot);
      const id = p.action === 'roomClose' ? p.room : snapshot && snapshot.id;
      if (!id) return textResponse('invalid');
      let index = rows.findIndex(row => row[0] === id);
      const hash = roomHash(p.token);
      if (index >= 0 && rows[index][3] !== hash) return textResponse('denied');
      if (p.action === 'roomClose') {
        if (index >= 0) sheet.deleteRow(index + 2);
        return textResponse('ok');
      }
      if (index < 0 && rows.length >= 20) return textResponse('full');
      if (index >= 0) {
        const previous = roomSnapshot(String(rows[index][4]));
        if (!previous || previous.host !== snapshot.host || previous.revision > snapshot.revision) return textResponse('stale');
      }
      const microphones = index >= 0 ? JSON.parse(String(rows[index][5] || '{}')) : {};
      Object.keys(microphones).forEach(n => { if (!snapshot.members.includes(n)) delete microphones[n]; });
      const row = [id, snapshot.host, now, hash, p.snapshot, JSON.stringify(microphones)];
      sheet.getRange(index < 0 ? rows.length + 2 : index + 2, 1, 1, 6).setValues([row]);
      return textResponse('peers\n' + Object.keys(microphones).filter(n => now - microphones[n].at < ROOM_TTL_MS)
        .map(n => n + ',' + (microphones[n].muted ? '1' : '0')).join('\n'));
    }
    if (p.action !== 'roomSync' || !/^\+?[0-9]{1,20}$/.test(p.extension || '') ||
        !/^\+?[0-9]{1,20}$/.test(p.host || '') || !['0', '1'].includes(p.muted)) return textResponse('invalid');
    const matches = rows.map((row, i) => ({row, i, snapshot: roomSnapshot(String(row[4]))}))
      .filter(r => r.snapshot && r.snapshot.host === p.host && r.snapshot.members.includes(p.extension));
    if (matches.length !== 1) return textResponse('none');
    const match = matches[0];
    const microphones = JSON.parse(String(match.row[5] || '{}'));
    microphones[p.extension] = {muted: p.muted === '1', at: now};
    sheet.getRange(match.i + 2, 6).setValue(JSON.stringify(microphones));
    const parts = match.snapshot.parts;
    // Guests own their mute flags. The host owns membership and its own microphone.
    const hostMuted = parts[7].split(',').includes(match.snapshot.host);
    parts[7] = match.snapshot.members.filter(n => n === match.snapshot.host ? hostMuted :
      microphones[n] && now - microphones[n].at < ROOM_TTL_MS && microphones[n].muted).join(',');
    parts[8] = '';
    return textResponse(parts.join('\n'));
  } finally {
    lock.releaseLock();
  }
}
