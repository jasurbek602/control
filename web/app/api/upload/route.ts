import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { assertDeviceSecret } from '@/lib/auth';

export async function POST(req: Request) {
  try {
    assertDeviceSecret(req);
    const body = await req.json();
    if (!body.data) return NextResponse.json({ error: 'data kerak' }, { status: 400 });

    const d = await db();
    const r = await d.collection('uploads').insertOne({
      data: body.data,
      mimeType: body.mimeType || 'image/jpeg',
      createdAt: new Date(),
    });

    return NextResponse.json({ ok: true, url: `/api/upload/${String(r.insertedId)}` });
  } catch {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }
}
