import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

// Signal saqlash (offer, answer, ice candidate)
export async function POST(
  req: Request,
  { params }: { params: { deviceId: string } }
) {
  const body = await req.json();
  const d = await db();
  await d.collection('signals').insertOne({
    deviceId: params.deviceId,
    ...body,
    createdAt: new Date(),
  });
  return NextResponse.json({ ok: true });
}

// Signal olish
export async function GET(
  req: Request,
  { params }: { params: { deviceId: string } }
) {
  const url  = new URL(req.url);
  const type = url.searchParams.get('type');
  const d    = await db();

  const signal = await d.collection('signals').findOneAndDelete({
    deviceId: params.deviceId,
    type,
  });

  return NextResponse.json({ signal: signal ?? null });
}

// Signallarni tozalash
export async function DELETE(
  req: Request,
  { params }: { params: { deviceId: string } }
) {
  const d = await db();
  await d.collection('signals').deleteMany({
    deviceId: params.deviceId,
  });
  return NextResponse.json({ ok: true });
}
