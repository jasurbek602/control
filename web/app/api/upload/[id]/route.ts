import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { ObjectId } from 'mongodb';

export async function GET(
  _req: Request,
  { params }: { params: { id: string } }
) {
  try {
    const d = await db();
    const doc = await d.collection('uploads').findOne({ _id: new ObjectId(params.id) });
    if (!doc) return NextResponse.json({ error: 'Topilmadi' }, { status: 404 });

    const buffer = Buffer.from(doc.data, 'base64');
    return new Response(buffer, {
      headers: {
        'Content-Type': doc.mimeType || 'image/jpeg',
        'Cache-Control': 'public, max-age=86400',
      },
    });
  } catch {
    return NextResponse.json({ error: 'Topilmadi' }, { status: 404 });
  }
}
