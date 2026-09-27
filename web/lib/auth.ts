import crypto from 'node:crypto';
export function assertDeviceSecret(req: Request) {
  const provided = req.headers.get('x-device-secret') ?? '';
  const expected = process.env.DEVICE_SHARED_SECRET ?? '';
  if (!provided || !expected) throw new Error('Unauthorized');
  const a = Buffer.from(provided); const b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a,b)) throw new Error('Unauthorized');
}
