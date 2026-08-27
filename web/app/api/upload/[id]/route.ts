import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { ObjectId } from 'mongodb';

export async function GET(
  _req: Request,
  context: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await context.params;

    if (!ObjectId.isValid(id)) {
      return NextResponse.json({ error: 'Noto\'g\'ri ID' }, { status: 400 });
    }

    const d = await db();
    const doc = await d.collection('uploads').findOne({ _id: new ObjectId(id) });

    if (!doc) {
      return NextResponse.json({ error: 'Topilmadi' }, { status: 404 });
    }

    const buffer = Buffer.from(doc.data, 'base64');
    return new Response(buffer, {
      headers: {
        'Content-Type': doc.mimeType || 'image/jpeg',
        'Cache-Control': 'public, max-age=86400',
      },
    });
  } catch (e) {
    return NextResponse.json({ error: 'Xato: ' + String(e) }, { status: 500 });
  }
}
