import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { assertDeviceSecret } from '@/lib/auth';

export async function POST(req: Request) {
  try {
    assertDeviceSecret(req);
    const body = await req.json();
    const d = await db();
    await d.collection('devices').updateOne(
      { deviceId: body.deviceId },
      { $set: { lastSeen: new Date(), battery: body.battery ?? null } },
      { upsert: true }
    );
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }
}
