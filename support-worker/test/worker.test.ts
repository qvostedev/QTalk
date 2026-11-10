import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { readFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { Miniflare, Log, LogLevel, FormData, Request, Response } from 'miniflare';
import { MockAgent, fetch as mockedFetch } from 'undici';
import { build } from 'esbuild';
import { cheapValidation } from '../src/index';

// Every upstream response in this suite is an explicit test double.
// Outbound networking is disabled; these are not real Telegram delivery receipts.
const BOT = '123456789:' + 'x'.repeat(35);
let script: string;
const instances: Miniflare[] = [];
beforeAll(async () => {
  script = (await build({entryPoints: ['src/index.ts'], bundle: true, write: false,
    format: 'esm', platform: 'browser', external: ['cloudflare:workers']})).outputFiles[0].text;
});
afterEach(async () => { await Promise.all(instances.splice(0).map(mf => mf.dispose())); });
function harness(configured = true, persistence?: string) {
  const mock = new MockAgent(); mock.disableNetConnect();
  const observed = {outbound:0, destinationMatches:true};
  const mf = new Miniflare({ modules: true, script, compatibilityDate: '2026-07-30',
    durableObjectsPersist: persistence,
    durableObjects: { COORDINATOR: { className: 'SupportCoordinator', useSQLite: true } },
    bindings: configured ? { TELEGRAM_BOT_TOKEN: BOT, TELEGRAM_CHAT_ID: '-1001234567890' } : {},
    // Normalize across Node/Miniflare's Request implementations. This callback
    // exists only in the test harness; no caller-supplied upstream in production.
    outboundService: async request => {
      observed.outbound++;
      observed.destinationMatches &&= new URL(request.url).origin === 'https://api.telegram.org' && new URL(request.url).pathname === path;
      const response = await mockedFetch(request.url, {method:request.method,
        headers:Object.fromEntries(request.headers),body:Buffer.from(await request.arrayBuffer()),dispatcher:mock});
      return new Response(await response.arrayBuffer(), {status:response.status,headers:Object.fromEntries(response.headers)});
    }, log: new Log(LogLevel.NONE) });
  instances.push(mf);
  const upstream = mock.get('https://api.telegram.org');
  return { mf, mock, upstream, observed,
    post: (body: FormData | string | Uint8Array | ReadableStream, headers: Record<string,string> = {}) =>
      mf.dispatchFetch('https://support.test/v1/reports', {method: 'POST', body: body as never,
        duplex:'half', headers: {'CF-Connecting-IP': '192.0.2.10', ...headers}}) };
}
function metadata(overrides: Record<string, unknown> = {}) {
  return {schemaVersion: 1, reportId: crypto.randomUUID(), title: 'Synthetic title', description: 'Synthetic description',
    appVersion: '1.1.2-support-local', platform: 'windows', createdAt: '2026-11-09T00:00:00Z', screenshotAttached: false, ...overrides};
}
function form(report = metadata(), zip = new Uint8Array([80,75,3,4,0,0])) {
  const data = new FormData(); data.set('report', JSON.stringify(report));
  data.set('diagnostics', new Blob([zip], {type: 'application/zip'}), '../../untrusted.zip'); return data;
}
const path = `/bot${BOT}/sendDocument`;

describe('local relay with SQLite coordinator and mocked Telegram', () => {
  it('allocates durable sequential numbers without consuming numbers for duplicates', async () => {
    mkdirSync('build/test-state', {recursive:true});
    const storage = mkdtempSync('build/test-state/ticket-');
    const dispose = async (h: ReturnType<typeof harness>) => {
      await h.mf.dispose(); instances.splice(instances.indexOf(h.mf), 1);
    };
    let h = harness(true, storage);
    try {
      h.upstream.intercept({path,method:'POST'}).reply(200,{ok:true,result:{message_id:1,document:{file_id:'test'}}}).times(3);
      const first = metadata();
      expect(await (await h.post(form(first))).json()).toMatchObject({ticketId:'QTK-00001'});
      expect(await (await h.post(form(first))).json()).toMatchObject({ticketId:'QTK-00001'});
      expect((await h.post(form(metadata({title:'ab'})))).status).toBe(400);
      const next = await Promise.all([h.post(form()),h.post(form())]);
      const receipts = await Promise.all(next.map(r=>r.json() as Promise<{ticketId:string}>));
      expect(receipts.map(r=>r.ticketId).sort()).toEqual(['QTK-00002','QTK-00003']);
      expect(h.observed.outbound).toBe(3);
      await dispose(h);
      h = harness(true, storage);
      h.upstream.intercept({path,method:'POST'}).reply(200,{ok:true,result:{message_id:2,document:{file_id:'test'}}});
      expect(await (await h.post(form())).json()).toMatchObject({ticketId:'QTK-00004'});
      expect(await (await h.post(form(first))).json()).toMatchObject({ticketId:'QTK-00001'});
      expect(h.observed.outbound).toBe(1);
    } finally {
      await dispose(h); rmSync(storage,{recursive:true,force:true});
    }
  });
  it.skipIf(!process.env.QTALK_SUPPORT_NATIVE_BODY_FIXTURE)('accepts multipart bytes emitted by the real JVM transport', async () => {
    const bytes = readFileSync(process.env.QTALK_SUPPORT_NATIVE_BODY_FIXTURE!);
    const bodyText = bytes.toString('latin1');
    const boundary = bodyText.slice(2,bodyText.indexOf('\r\n'));
    const metadataText = bodyText.slice(bodyText.indexOf('\r\n\r\n')+4).split('\r\n--')[0];
    const report = JSON.parse(metadataText);
    const h = harness(); let captured = '';
    h.upstream.intercept({path,method:'POST'}).reply(200, options => {
      captured = Buffer.from(options.body as Uint8Array).toString('utf8');
      return JSON.stringify({ok:true,result:{message_id:321,document:{file_id:'synthetic-native-fixture'}}});
    });
    const response = await h.post(bytes,{'Content-Type':`multipart/form-data; boundary=${boundary}`});
    const receipt = await response.json() as any;
    expect(response.status).toBe(200);
    expect(receipt).toMatchObject({ok:true,reportId:report.reportId});
    expect(captured).toContain(`Обращение №${receipt.ticketId.slice(4)}`);
    expect(captured).toContain(`QTalk-${receipt.ticketId.slice(4)}.zip`);
    expect(captured).not.toContain(`Report: ${report.reportId}`);
    expect(h.observed.outbound).toBe(1);
  });
  it('fails closed without secrets or coordinator; validates method/path', async () => {
    const h = harness(false);
    expect((await h.post(form())).status).toBe(503);
    expect((await h.mf.dispatchFetch('https://support.test/else')).status).toBe(404);
    expect((await h.mf.dispatchFetch('https://support.test/v1/reports')).status).toBe(405);
    const noBinding = new Miniflare({modules: true, script, compatibilityDate: '2026-07-30',
      bindings: {TELEGRAM_BOT_TOKEN: BOT, TELEGRAM_CHAT_ID: '123'}, log: new Log(LogLevel.NONE)});
    instances.push(noBinding);
    expect((await noBinding.dispatchFetch('https://support.test/v1/reports', {method: 'POST', body: 'x'})).status).toBe(503);
  });
  it('forwards one fixed-destination document and returns a validated receipt; retries deduplicate', async () => {
    const h = harness(); let sent = 0; let captured = '';
    h.upstream.intercept({path, method: 'POST'}).reply(200, opts => {
      sent++; captured = Buffer.from(opts.body as Uint8Array).toString('utf8');
      return JSON.stringify({ok: true, result: {message_id: 123, document: {file_id: 'synthetic-file'}}});
    });
    const report = metadata({title: '<b>User title</b>'});
    const response = await h.post(form(report)); const receipt = await response.json() as any;
    expect(h.observed).toEqual({outbound:1,destinationMatches:true});
    expect({status:response.status,sent,receipt}).toMatchObject({status:200,sent:1,receipt:{ok: true, reportId: report.reportId}});
    expect(receipt.ticketId).toBe('QTK-00001'); expect(sent).toBe(1);
    expect(captured).toContain('Обращение №00001');
    expect(captured).not.toContain(receipt.ticketId);
    expect(captured).not.toContain(report.reportId);
    expect(captured).not.toContain('Report:');
    expect(captured).not.toContain('parse_mode'); expect(captured).not.toContain('untrusted.zip');
    expect(captured).toContain('QTalk-00001.zip');
    expect(captured).toContain('-1001234567890');
    expect(await (await h.post(form(report))).json()).toEqual(receipt); expect(sent).toBe(1);
  });
  it('rejects schema, lengths, extra/duplicate parts, content types and ZIP magic', async () => {
    const h = harness();
    for (const change of [{schemaVersion: 2}, {reportId: 'bad'}, {title:'ab'}, {title:'x'.repeat(161)},
      {description:'short'}, {description:'x'.repeat(12001)}, {platform:'ios'}, {createdAt:'yesterday'},
      {screenshotAttached:'yes'}, {appVersion:''}, {arbitrary:'x'}]) {
      expect((await h.post(form(metadata(change)))).status, JSON.stringify(change).slice(0,100)).toBe(400);
    }
    const extra = form(); extra.set('extra', 'x'); expect((await h.post(extra)).status).toBe(400);
    const duplicate = form(); duplicate.append('report', '{}'); expect((await h.post(duplicate)).status).toBe(400);
    const absent = new FormData(); absent.set('report', '{}'); expect((await h.post(absent)).status).toBe(400);
    expect((await h.post(form(metadata(), new Uint8Array([1,2,3,4])))).status).toBe(400);
    expect((await h.post('invalid', {'Content-Type':'application/json'})).status).toBe(415);
    expect((await h.post('invalid', {'Content-Type':'multipart/form-data; boundary=x'})).status).toBe(400);
  });
  it('bounds streaming input even without content-length; rejects oversized ZIPs', async () => {
    const h = harness();
    expect(cheapValidation(new Request('https://support.test/v1/reports', {method:'POST',body:'x',
      headers:{'Content-Type':'multipart/form-data; boundary=x','Content-Length':'99999999'}}) as never)?.status).toBe(413);
    // dispatchFetch streams chunked input; no reliance on Content-Length.
    const stream = new ReadableStream({start(controller) {
      for (let i = 0; i < 10; i++) controller.enqueue(new Uint8Array(1024*1024)); controller.close();
    }});
    expect((await h.post(stream, {'Content-Type':'multipart/form-data; boundary=x'})).status).toBe(413);
    const bigZip = new Uint8Array(8 * 1024 * 1024 + 1); bigZip.set([80,75,3,4]);
    expect((await h.post(form(metadata(), bigZip))).status).toBe(413);
  });
  it('claims before awaiting upstream, rejects changed payload under same ID', async () => {
    const h = harness(); let sent = 0;
    h.upstream.intercept({path, method:'POST'}).reply(200, () => {
      sent++; return JSON.stringify({ok:true,result:{message_id:1,document:{file_id:'test'}}});
    }).delay(150);
    const report = metadata();
    const responses = await Promise.all([h.post(form(report)), h.post(form(report))]);
    expect(responses.map(r => r.status).sort()).toEqual([200,202]); expect(sent).toBe(1);
    const collision = await h.post(form({...report, description:'Different synthetic body'}));
    expect(collision.status).toBe(409); expect(sent).toBe(1);
  });
  it('enforces per-IP quota after cheap validation; duplicate receipt bypasses quota', async () => {
    const h = harness();
    h.upstream.intercept({path, method:'POST'}).reply(200, {ok:true,result:{message_id:1,document:{file_id:'test'}}}).times(5);
    let first: ReturnType<typeof metadata> | undefined;
    for (let i=0;i<5;i++) { const report = metadata(); first ??= report; expect((await h.post(form(report))).status).toBe(200); }
    expect((await h.post(form())).status).toBe(429);
    expect((await h.post(form(first!))).status).toBe(200);
  });
  it('requires upstream ok:true and receipt, maps failures and clamps retry_after', async () => {
    const h = harness();
    for (const [upstreamStatus, body, expectedStatus, code] of [
      [200, {ok:false, description:'do not expose'}, 502,'upstream_rejected'],
      [200, {ok:true,result:{}}, 502,'delivery_uncertain'],
      [500, {ok:false}, 502,'delivery_uncertain'],
      [429, {ok:false,error_code:429,parameters:{retry_after:999999999}}, 429,'rate_limited'],
    ] as const) {
      h.upstream.intercept({path,method:'POST'}).reply(upstreamStatus, body);
      const response = await h.post(form());
      expect(response.status).toBe(expectedStatus);
      expect(await response.json()).toEqual({ok:false,code});
      if (expectedStatus===429) expect(Number(response.headers.get('Retry-After'))).toBeLessThanOrEqual(3600);
    }
  });
  it('makes network exceptions terminal uncertain without leaking secrets or retrying', async () => {
    const h = harness();
    h.upstream.intercept({path,method:'POST'}).replyWithError(new Error(`secret URL https://api.telegram.org/bot${BOT}/sendDocument`));
    const report = metadata();
    const response = await h.post(form(report));
    expect(response.status).toBe(502);
    const text = await response.text(); expect(text).not.toContain(BOT); expect(text).not.toContain('https');
    expect(JSON.parse(text)).toEqual({ok:false,code:'delivery_uncertain'});
    const retry = await h.post(form(report)); expect(retry.status).toBe(502); expect(await retry.text()).toBe(text);
  });
  it('applies a worldwide day limit across different IPs', async () => {
    const h = harness();
    h.upstream.intercept({path,method:'POST'}).reply(200,{ok:true,result:{message_id:1,document:{file_id:'test'}}}).times(500);
    for (let i=0;i<500;i++) {
      expect((await h.post(form(),{'CF-Connecting-IP':`198.51.${Math.floor(i/250)}.${i%250+1}`})).status).toBe(200);
    }
    expect((await h.post(form(),{'CF-Connecting-IP':'203.0.113.200'})).status).toBe(429);
  });
  it('upstream timeout is terminal uncertainty; never auto-duplicates', async () => {
    const h = harness();
    h.upstream.intercept({path,method:'POST'}).reply(200,{ok:true,result:{message_id:1,document:{file_id:'test'}}}).delay(21000);
    const report = metadata(); const response = await h.post(form(report));
    expect(response.status).toBe(502); expect(await response.json()).toEqual({ok:false,code:'delivery_uncertain'});
    expect((await h.post(form(report))).status).toBe(502); expect(h.observed.outbound).toBe(1);
  });
});
