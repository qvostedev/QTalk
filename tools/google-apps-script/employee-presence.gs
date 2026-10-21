const SHEET_NAME = 'Сотрудники';
const HEADER_ROW = 4;
const EXTENSION_COLUMN = 6;
const STATUS_COLUMN = 9;
const UPDATED_COLUMN = 10;
const OFFLINE_AFTER_MS = 150000;
const ALLOWED_STATUSES = ['Доступен', 'Занят', 'Не в сети'];

function doPost(event) {
  if (event.parameter.action === 'roomSync' || event.parameter.action === 'roomClose') {
    return roomRequest(event.parameter);
  }
  const extension = String(event.parameter.extension || '').replace(/\D/g, '');
  const status = String(event.parameter.status || '');
  if (!extension || ALLOWED_STATUSES.indexOf(status) === -1) {
    return textResponse('invalid request');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sheet = presenceSheet();
    const lastRow = sheet.getLastRow();
    const extensions = sheet.getRange(HEADER_ROW + 1, EXTENSION_COLUMN, lastRow - HEADER_ROW, 1)
      .getDisplayValues();
    const rowOffset = extensions.findIndex(row => String(row[0]).replace(/\D/g, '') === extension);
    if (rowOffset < 0) return textResponse('unknown extension');
    const row = HEADER_ROW + 1 + rowOffset;
    sheet.getRange(row, STATUS_COLUMN, 1, 2).setValues([[status, new Date()]]);
    return textResponse('ok');
  } finally {
    lock.releaseLock();
  }
}

function doGet() {
  const sheet = presenceSheet();
  const lastRow = sheet.getLastRow();
  const values = sheet.getRange(HEADER_ROW + 1, EXTENSION_COLUMN, lastRow - HEADER_ROW, 5).getValues();
  const now = Date.now();
  const rows = ['extension,status'];
  values.forEach(row => {
    const extension = String(row[0]).replace(/\D/g, '');
    if (!extension) return;
    const recordedStatus = String(row[3] || 'Не в сети');
    const updatedAt = row[4] instanceof Date ? row[4].getTime() : 0;
    const status = updatedAt && now - updatedAt <= OFFLINE_AFTER_MS ? recordedStatus : 'Не в сети';
    rows.push(extension + ',' + status);
  });
  return ContentService.createTextOutput(rows.join('\n')).setMimeType(ContentService.MimeType.TEXT);
}

function textResponse(message) {
  return ContentService.createTextOutput(message).setMimeType(ContentService.MimeType.TEXT);
}

function presenceSheet() {
  const spreadsheetId = PropertiesService.getScriptProperties().getProperty('SPREADSHEET_ID');
  if (!spreadsheetId) throw new Error('Set SPREADSHEET_ID in Script Properties');
  const sheet = SpreadsheetApp.openById(spreadsheetId).getSheetByName(SHEET_NAME);
  if (!sheet) throw new Error('Configured employee sheet was not found');
  return sheet;
}
