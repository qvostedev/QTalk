const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
const {createHash} = require('node:crypto');
const assert = require('node:assert/strict');
let now = 100000;
const rows = [['room_id', 'host', 'updated_at', 'owner_hash', 'snapshot', 'microphones']];
const sheet = {
  getLastRow: () => rows.length,
  deleteRow: row => rows.splice(row - 1, 1),
  getRange: (row, col, count = 1, width = 1) => ({
    getValues: () => rows.slice(row - 1, row - 1 + count).map(r => r.slice(col - 1, col - 1 + width)),
    setValues: values => values.forEach((value, i) => {
      rows[row - 1 + i] ||= [];
      value.forEach((v, j) => rows[row - 1 + i][col - 1 + j] = v);
    }),
    setValue: value => rows[row - 1][col - 1] = value
  })
};
const context = {
  Date: {now: () => now},
  PropertiesService: {getScriptProperties: () => ({getProperty: () => 'test'})},
  SpreadsheetApp: {openById: () => ({getSheetByName: () => sheet})},
  LockService: {getScriptLock: () => ({tryLock: () => true, releaseLock() {}})},
  Utilities: {
    DigestAlgorithm: {SHA_256: 'sha256'},
    computeDigest: (algorithm, value) => createHash(algorithm).update(value).digest(),
    base64EncodeWebSafe: bytes => Buffer.from(bytes).toString('base64url')
  },
  textResponse: value => value
};
runInNewContext(readFileSync(__dirname + '/conference-rooms.gs', 'utf8') + '\nthis.request=roomRequest;', context);
const request = context.request;
const room = '11111111-1111-1111-1111-111111111111';
const token = '22222222-2222-2222-2222-222222222222';
const snapshot = ['QTALK/1','snapshot',room,'1','101','101,102,103','','',''].join('\n');
const publish = {action:'roomSync',role:'host',token,snapshot};
assert.equal(request(publish),'peers\n');
assert.equal(rows.length,2);
assert.equal(request({action:'roomSync',extension:'102',host:'101',muted:'1'}).split('\n')[7],'102');
assert.equal(request(publish),'peers\n102,1');
assert.equal(request({action:'roomSync',extension:'999',host:'101',muted:'1'}),'none');
assert.equal(request({action:'roomSync',extension:'102',host:'999',muted:'1'}),'none');
assert.equal(request({...publish,token:'33333333-3333-3333-3333-333333333333'}),'denied');
assert.equal(request({...publish,snapshot:snapshot.replace('\n1\n101','\n0\n101')}),'stale');
assert.equal(request({...publish,snapshot:snapshot + '\nextra'}),'invalid');
const lanSnapshot = snapshot + '\n192.168.100.10:18000/55555555-5555-5555-5555-555555555555';
assert.equal(request({...publish,snapshot:lanSnapshot}),'peers\n102,1');
assert.equal(request({action:'roomSync',extension:'102',host:'101',muted:'0'}).split('\n')[9],
  '192.168.100.10:18000/55555555-5555-5555-5555-555555555555');
assert.equal(request({action:'roomSync',extension:'102',host:'101',muted:'0'}).split('\n')[7],'');
const second = snapshot.replaceAll(room, '44444444-4444-4444-4444-444444444444').replaceAll('101','201').replaceAll('102','202').replaceAll('103','203');
assert.equal(request({...publish,snapshot:second}),'peers\n');
assert.equal(rows.length,3);
assert.equal(request({action:'roomClose',role:'host',token,room}),'ok');
assert.equal(rows.length,2);
assert.equal(request({action:'roomSync',extension:'102',host:'101',muted:'0'}),'none');
now += 46000;
assert.equal(request({action:'roomSync',extension:'202',host:'201',muted:'0'}),'none');
assert.equal(rows.length,1);
console.log('Conference room tests passed: membership, mute, isolation, ownership, validation, revision, close, expiry.');
