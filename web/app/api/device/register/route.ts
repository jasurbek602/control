import { NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { db } from '@/lib/db';
import { assertDeviceSecret } from '@/lib/auth';

function genCode() {
  return randomBytes(3).toString('hex').toUpperCase();
}

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const d = await db();

    if (body.deviceId) {
      assertDeviceSecret(req);
      const existing = await d.collection('devices').findOne({ deviceId: body.deviceId });
      if (existing) {
        return NextResponse.json({ ok: true, pairingCode: existing.pairingCode });
      }
      const pairingCode = genCode();
      await d.collection('devices').insertOne({
        deviceId: body.deviceId,
        name: body.name || 'Child device',
        pairingCode,
        parentConnected: false,
        lastSeen: new Date(),
      });
      return NextResponse.json({ ok: true, pairingCode });
    }

    if (body.pairingCode) {
      const found = await d.collection('devices').findOne({ pairingCode: body.pairingCode });
      if (!found) return NextResponse.json({ error: 'Invalid pairing code' }, { status: 404 });
      await d.collection('devices').updateOne(
        { _id: found._id },
        { $set: { name: body.name || found.name, parentConnected: true } }
      );
      return NextResponse.json({ ok: true });
    }

    return NextResponse.json({ error: 'Bad request' }, { status: 400 });
    } catch (e) {
    return NextResponse.json({ error: 'DEBUG: ' + (e instanceof Error ? e.message : String(e)) }, { status: 401 });
  }
}

export async function GET() {
  const d = await db();
  const devices = await d.collection('devices').find({}).sort({ lastSeen: -1 }).limit(100).toArray();
  return NextResponse.json({
    devices: devices.map(x => ({
      ...x,
      _id: String(x._id),
      online: Date.now() - new Date(x.lastSeen ?? 0).getTime() < 5000,
    })),
  });
}
