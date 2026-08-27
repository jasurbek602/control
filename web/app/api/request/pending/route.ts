import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { assertDeviceSecret } from '@/lib/auth';

export async function GET(req: Request) {
  try {
    assertDeviceSecret(req);
    const url = new URL(req.url);
    const deviceId = url.searchParams.get('deviceId');
    if (!deviceId) return NextResponse.json({ error: 'deviceId kerak' }, { status: 400 });

    const d = await db();
    const request = await d.collection('requests').findOne(
      { deviceId, status: 'PENDING' },
      { sort: { createdAt: 1 } }
    );

    if (!request) return NextResponse.json({ request: null });
    return NextResponse.json({ request: { ...request, _id: String(request._id) } });
  } catch {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }
}
