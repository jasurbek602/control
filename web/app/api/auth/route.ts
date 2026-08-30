import { NextResponse } from 'next/server';
import crypto from 'node:crypto';

const PWD = process.env.ADMIN_PASSWORD ?? '';
const SEC = process.env.SESSION_SECRET ?? 'fg-default-secret';

function token() {
  return crypto.createHmac('sha256', SEC).update(PWD).digest('hex');
}

// Tekshirish
export async function GET(req: Request) {
  const cookie = req.headers.get('cookie') ?? '';
  const t = cookie.match(/fg_session=([^;]+)/)?.[1];
  if (!PWD || !t || t !== token())
    return NextResponse.json({ ok: false }, { status: 401 });
  return NextResponse.json({ ok: true });
}

// Login
export async function POST(req: Request) {
  if (!PWD)
    return NextResponse.json({ error: 'ADMIN_PASSWORD sozlanmagan' }, { status: 500 });
  const { password } = await req.json();
  const a = Buffer.from(password ?? '');
  const b = Buffer.from(PWD);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b))
    return NextResponse.json({ error: "Parol noto'g'ri" }, { status: 401 });
  const res = NextResponse.json({ ok: true });
  res.cookies.set('fg_session', token(), {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 60 * 60 * 24 * 30,
    path: '/',
  });
  return res;
}

// Logout
export async function DELETE() {
  const res = NextResponse.json({ ok: true });
  res.cookies.set('fg_session', '', { maxAge: 0, path: '/' });
  return res;
}
