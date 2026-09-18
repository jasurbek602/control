import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { ObjectId } from 'mongodb';
import { assertDeviceSecret } from '@/lib/auth';

export async function POST(req: Request) {
  try {
    assertDeviceSecret(req);
    const body = await req.json();
    if (!body.id || !body.status) return NextResponse.json({ error: 'id va status kerak' }, { status: 400 });

    const d = await db();
    await d.collection('requests').updateOne(
      { _id: new ObjectId(body.id) },
      { $set: { status: body.status, resultUrl: body.resultUrl ?? null, updatedAt: new Date() } }
    );
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }
}
