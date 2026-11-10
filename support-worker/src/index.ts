// No console logging: Telegram's URL contains a secret. Never include an upstream
// exception, URL, response body, report text or archive in a response or storage.
export interface Env {
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: string;
  TELEGRAM_MESSAGE_THREAD_ID?: string;
  COORDINATOR?: DurableObjectNamespace;
}
const MiB = 1024 * 1024;
const MAX_ZIP = 8 * MiB;
const MAX_BODY = MAX_ZIP + 160 * 1024;
const DAY = 86400000;
const RETENTION = 30 * DAY;
const GLOBAL_DAY = 500;
const IP_HOUR = 5;
const IP_DAY = 20;
const MAX_STATES = 20000;
const fields = ['schemaVersion','reportId','title','description','appVersion','platform','createdAt','screenshotAttached'] as const;
interface Report {
  schemaVersion: 1; reportId: string; title: string; description: string; appVersion: string;
  platform: 'windows'|'linux'|'macos'|'other'; createdAt: string; screenshotAttached: boolean;
}
class InputFailure {
  constructor(readonly status: number, readonly code: string) {}
}
function error(status: number, code: string, retryAfter?: number): Response {
  return Response.json({ok:false,code}, {status, headers:{'Cache-Control':'no-store',
    ...(retryAfter ? {'Retry-After': String(retryAfter)} : {})}});
}
function configured(env: Env): boolean {
  return !!env.COORDINATOR && /^\d{5,20}:[A-Za-z0-9_-]{20,100}$/.test(env.TELEGRAM_BOT_TOKEN ?? '') &&
    /^-?\d{1,20}$/.test(env.TELEGRAM_CHAT_ID ?? '') && (env.TELEGRAM_MESSAGE_THREAD_ID === undefined ||
      /^[1-9]\d{0,9}$/.test(env.TELEGRAM_MESSAGE_THREAD_ID));
}
export function cheapValidation(request: Request): Response | undefined {
  if (!/^multipart\/form-data;\s*boundary=(?:"[A-Za-z0-9'()+_,./:=? -]{1,120}"|[A-Za-z0-9'()+_,./:=?-]{1,120})$/i.test(request.headers.get('Content-Type') ?? ''))
    return error(415,'invalid_content_type');
  const length = request.headers.get('Content-Length');
  if (length !== null && (!/^\d+$/.test(length) || Number(length) > MAX_BODY)) return error(413,'too_large');
}
async function boundedRead(body: ReadableStream<Uint8Array> | null, limit: number, timeout = 15000): Promise<Uint8Array> {
  if (!body) throw new InputFailure(400,'invalid_body');
  const reader = body.getReader(); const chunks: Uint8Array[] = []; let size = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const deadline = new Promise<never>((_, reject) => { timer = setTimeout(() => {
    reject(new InputFailure(408,'request_timeout')); void reader.cancel().catch(() => {});
  }, timeout); });
  try {
    while (true) {
      const chunk = await Promise.race([reader.read(), deadline]);
      if (chunk.done) break;
      size += chunk.value.byteLength;
      if (size > limit) { void reader.cancel().catch(() => {}); throw new InputFailure(413,'too_large'); }
      chunks.push(chunk.value);
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk,offset); offset += chunk.length; }
    return bytes;
  } finally { if (timer !== undefined) clearTimeout(timer); reader.releaseLock(); }
}
function validateReport(value: unknown): Report {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new InputFailure(400,'invalid_report');
  const report = value as Record<string,unknown>;
  const validText = (key: string, min: number, max: number) => typeof report[key] === 'string' &&
    (report[key] as string).trim() === report[key] && (report[key] as string).length >= min && (report[key] as string).length <= max;
  if (Object.keys(report).length !== fields.length || fields.some(key => !(key in report)) ||
    report.schemaVersion !== 1 || typeof report.reportId !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(report.reportId) ||
    !validText('title',3,160) || !validText('description',10,12000) || !validText('appVersion',1,100) ||
    !['windows','linux','macos','other'].includes(report.platform as string) || typeof report.screenshotAttached !== 'boolean' ||
    typeof report.createdAt !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z$/.test(report.createdAt) ||
    !Number.isFinite(Date.parse(report.createdAt)) || new Date(report.createdAt).toISOString().slice(0,19) !== report.createdAt.slice(0,19))
    throw new InputFailure(400,'invalid_report');
  return Object.fromEntries(fields.map(key => [key,report[key]])) as unknown as Report;
}
async function parse(request: Request): Promise<{report:Report; zip:Uint8Array}> {
  const bytes = await boundedRead(request.body,MAX_BODY);
  let form: FormData;
  try { form = await new Response(bytes,{headers:{'Content-Type': request.headers.get('Content-Type')!}}).formData(); }
  catch { throw new InputFailure(400,'invalid_multipart'); }
  const parts = [...form.entries()];
  if (parts.length !== 2 || form.getAll('report').length !== 1 || form.getAll('diagnostics').length !== 1 ||
    parts.some(([name]) => name !== 'report' && name !== 'diagnostics')) throw new InputFailure(400,'invalid_parts');
  const text = form.get('report'); const archive = form.get('diagnostics');
  if (typeof text !== 'string' || new TextEncoder().encode(text).length > 96*1024 || !(archive instanceof File) || archive.type !== 'application/zip')
    throw new InputFailure(400,'invalid_parts');
  if (archive.size > MAX_ZIP) throw new InputFailure(413,'too_large');
  if (archive.size < 4) throw new InputFailure(400,'invalid_zip');
  let report: Report;
  try { report = validateReport(JSON.parse(text)); } catch { throw new InputFailure(400,'invalid_report'); }
  const zip = new Uint8Array(await archive.arrayBuffer());
  if (zip[0] !== 80 || zip[1] !== 75 || zip[2] !== 3 || zip[3] !== 4) throw new InputFailure(400,'invalid_zip');
  // No unzip: bounded ZIP attachment is forwarded as untrusted diagnostics.
  return {report,zip};
}
async function sha(bytes: Uint8Array): Promise<string> {
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(n => n.toString(16).padStart(2,'0')).join('');
}
const encode = (text: string) => new TextEncoder().encode(text);
interface Stored {
  [key: string]: SqlStorageValue;
  id: string; fingerprint: string; state: string; ticket: string; created: number;
  status: number; code: string; retry_at: number;
}
export class SupportCoordinator {
  private active = 0;
  private readonly sql: SqlStorage;
  private readonly salt: string;
  constructor(private readonly ctx: DurableObjectState, private readonly env: Env) {
    this.sql = ctx.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS reports (
      id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, state TEXT NOT NULL, ticket TEXT NOT NULL,
      created INTEGER NOT NULL, status INTEGER NOT NULL, code TEXT NOT NULL, retry_at INTEGER NOT NULL);
      CREATE INDEX IF NOT EXISTS reports_created ON reports(created);
      CREATE TABLE IF NOT EXISTS quotas (key TEXT PRIMARY KEY, count INTEGER NOT NULL, expires INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS private (key TEXT PRIMARY KEY, value TEXT NOT NULL);`);
    this.sql.exec('INSERT OR IGNORE INTO private VALUES (?,?)','salt',crypto.randomUUID()+crypto.randomUUID());
    // Permanent counter, deliberately excluded from report/quotas retention cleanup.
    this.sql.exec('INSERT OR IGNORE INTO private VALUES (?,?)','ticket_sequence','0');
    this.salt = this.sql.exec<{value:string}>('SELECT value FROM private WHERE key=?','salt').one().value;
  }
  async fetch(request: Request): Promise<Response> {
    if (!configured(this.env)) return error(503,'missing_config');
    // One global coordinator also bounds simultaneous archive parsing/forwarding memory.
    if (this.active >= 2) return error(429,'rate_limited',30);
    this.active++;
    try {
      const invalid = cheapValidation(request); if (invalid) return invalid;
      const {report,zip} = await parse(request);
      const fingerprint = await sha(encode(JSON.stringify(report) + ':' + await sha(zip)));
      const ip = await sha(encode(this.salt + ':' + (request.headers.get('CF-Connecting-IP') ?? 'unknown').slice(0,128)));
      const now = Date.now();
      // All reads/claims/quotas are synchronous in one SQLite transaction, before upstream await.
      const decision = this.ctx.storage.transactionSync((): Response | undefined => {
        this.sql.exec('DELETE FROM reports WHERE created < ?',now - RETENTION);
        this.sql.exec('DELETE FROM quotas WHERE expires < ?',now);
        const prior = this.sql.exec<Stored>('SELECT * FROM reports WHERE id=?',report.reportId).toArray()[0];
        if (prior) {
          if (prior.fingerprint !== fingerprint) return error(409,'id_conflict');
          if (prior.state === 'sent') return this.receipt(prior);
          if (prior.state === 'pending') {
            if (now-prior.created < 90000) return error(202,'pending');
            this.sql.exec("UPDATE reports SET state='uncertain',status=502,code='delivery_uncertain' WHERE id=?",report.reportId);
            return error(502,'delivery_uncertain');
          }
          if (!(prior.state === 'rejected' && prior.code === 'rate_limited' && now >= prior.retry_at))
            return error(prior.status,prior.code,prior.retry_at > now ? Math.ceil((prior.retry_at-now)/1000) : undefined);
        }
        const hour = Math.floor(now/3600000); const day = Math.floor(now/DAY);
        const quotas = [[`global:${day}`,GLOBAL_DAY,(day+1)*DAY],
          [`ip-hour:${ip}:${hour}`,IP_HOUR,(hour+1)*3600000], [`ip-day:${ip}:${day}`,IP_DAY,(day+1)*DAY]] as const;
        for (const [key,limit] of quotas) {
          const count = this.sql.exec<{count:number}>('SELECT count FROM quotas WHERE key=?',key).toArray()[0]?.count ?? 0;
          if (count >= limit) return error(429,'rate_limited',3600);
        }
        if (!prior && this.sql.exec<{count:number}>('SELECT COUNT(*) AS count FROM reports').one().count >= MAX_STATES)
          return error(429,'rate_limited',3600);
        for (const [key,,expires] of quotas) this.sql.exec(
          'INSERT INTO quotas VALUES (?,1,?) ON CONFLICT(key) DO UPDATE SET count=count+1',key,expires);
        const ticket = prior?.ticket ?? `QTK-${String(this.sql.exec<{value:string}>(
          "UPDATE private SET value=CAST(value AS INTEGER)+1 WHERE key='ticket_sequence' RETURNING value").one().value).padStart(5,'0')}`;
        this.sql.exec('INSERT INTO reports VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET state=excluded.state,status=excluded.status,code=excluded.code,retry_at=0',
          report.reportId,fingerprint,'pending',ticket,now,202,'pending',0);
      });
      if (decision) return decision;
      // Request-driven cleanup is complemented by alarms when the service is idle.
      // Failure to persist cleanup scheduling leaves the claim pending, never a resend.
      if (await this.ctx.storage.getAlarm() === null) await this.ctx.storage.setAlarm(now + DAY);
      return await this.forward(report,zip);
    } catch (failure) {
      if (failure instanceof InputFailure) return error(failure.status,failure.code);
      return error(503,'temporarily_unavailable');
    } finally { this.active--; }
  }
  async alarm(): Promise<void> {
    const now = Date.now();
    this.sql.exec('DELETE FROM reports WHERE created < ?',now - RETENTION);
    this.sql.exec('DELETE FROM quotas WHERE expires < ?',now);
    if (this.sql.exec<{count:number}>('SELECT COUNT(*) AS count FROM reports').one().count > 0 ||
      this.sql.exec<{count:number}>('SELECT COUNT(*) AS count FROM quotas').one().count > 0)
      await this.ctx.storage.setAlarm(now + DAY);
  }
  private receipt(row: Stored): Response {
    return Response.json({ok:true,ticketId:row.ticket,reportId:row.id},{headers:{'Cache-Control':'no-store'}});
  }
  private finish(id: string, state: string, status: number, code: string, retryAfter = 0): Response {
    this.sql.exec('UPDATE reports SET state=?, status=?, code=?, retry_at=? WHERE id=?',state,status,code,
      retryAfter ? Date.now()+retryAfter*1000 : 0,id);
    return error(status,code,retryAfter || undefined);
  }
  private async forward(report: Report, zip: Uint8Array): Promise<Response> {
    const controller = new AbortController(); const timer = setTimeout(() => controller.abort(),20000);
    try {
      const form = new FormData();
      form.set('chat_id',this.env.TELEGRAM_CHAT_ID!);
      if (this.env.TELEGRAM_MESSAGE_THREAD_ID) form.set('message_thread_id',this.env.TELEGRAM_MESSAGE_THREAD_ID);
      const ticket = this.sql.exec<Stored>('SELECT * FROM reports WHERE id=?',report.reportId).one().ticket;
      const number = ticket.replace(/^QTK-/, '');
      const label = /^\d{5,}$/.test(number) ? `Обращение №${number}` : 'Обращение в поддержку';
      form.set('caption',Array.from(`QTalk support\n${label}\n${report.title}\n${report.description}`).slice(0,950).join(''));
      form.set('document',new File([zip],/^\d{5,}$/.test(number) ? `QTalk-${number}.zip` : 'QTalk-diagnostics.zip',{type:'application/zip'}));
      const response = await fetch(`https://api.telegram.org/bot${this.env.TELEGRAM_BOT_TOKEN}/sendDocument`,
        {method:'POST',body:form,signal:controller.signal,redirect:'manual'});
      let body: any;
      try { body = JSON.parse(new TextDecoder().decode(await boundedRead(response.body,16*1024,20000))); }
      catch { return this.finish(report.reportId,'uncertain',502,'delivery_uncertain'); }
      if (response.status === 429 && body?.ok === false) {
        const delay = body?.parameters?.retry_after;
        const retryAfter = typeof delay === 'number' && Number.isFinite(delay) ? Math.max(1,Math.min(3600,Math.ceil(delay))) : 60;
        return this.finish(report.reportId,'rejected',429,'rate_limited',retryAfter);
      }
      if (response.status >= 500) return this.finish(report.reportId,'uncertain',502,'delivery_uncertain');
      if (body?.ok === false) return this.finish(report.reportId,'rejected',502,'upstream_rejected');
      if (response.status !== 200 || body?.ok !== true || !Number.isSafeInteger(body?.result?.message_id) ||
        body.result.message_id < 1 || typeof body?.result?.document?.file_id !== 'string' || !body.result.document.file_id)
        return this.finish(report.reportId,'uncertain',502,'delivery_uncertain');
      this.sql.exec("UPDATE reports SET state='sent',status=200,code='' WHERE id=?",report.reportId);
      return this.receipt(this.sql.exec<Stored>('SELECT * FROM reports WHERE id=?',report.reportId).one());
    } catch { return this.finish(report.reportId,'uncertain',502,'delivery_uncertain'); }
    finally { clearTimeout(timer); }
  }
}
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname !== '/v1/reports' || url.search || url.hash) return error(404,'not_found');
    if (request.method !== 'POST') return error(405,'method_not_allowed');
    if (!configured(env)) return error(503,'missing_config');
    const invalid = cheapValidation(request); if (invalid) return invalid;
    try {
      const coordinator = env.COORDINATOR!.get(env.COORDINATOR!.idFromName('global-support-v1'));
      return await coordinator.fetch(request);
    } catch { return error(503,'temporarily_unavailable'); }
  }
};
