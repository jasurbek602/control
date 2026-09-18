import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { ObjectId } from 'mongodb';

export async function POST(req: Request) {
  const body = await req.json();
  const d = await db();
  const doc = {
    deviceId: body.deviceId,
    type: body.type,
    status: 'PENDING',
    createdAt: new Date(),
    resultUrl: null,
  };
  const r = await d.collection('requests').insertOne(doc);
  return NextResponse.json({ ok: true, id: String(r.insertedId) });
}

export async function GET(req: Request) {
  const url = new URL(req.url);
  if (url.searchParams.get('mode') !== 'list')
    return NextResponse.json({ error: 'mode=list kerak' }, { status: 400 });
  const d = await db();
  const rows = await d.collection('requests').find({})
    .sort({ createdAt: -1 }).limit(200).toArray();
  return NextResponse.json({ requests: rows.map(x => ({ ...x, _id: String(x._id) })) });
}

export async function DELETE(req: Request) {
  const body = await req.json();
  const d = await db();
  if (body.id) {
    await d.collection('requests').deleteOne({ _id: new ObjectId(body.id) });
  } else if (body.all) {
    await d.collection('requests').deleteMany({});
  }
  return NextResponse.json({ ok: true });
}
