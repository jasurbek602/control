import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';

const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'admin';
const COOKIE = 'fg_auth';

export async function GET() {
  const store = await cookies();
  return store.get(COOKIE)?.value === ADMIN_PASSWORD
    ? NextResponse.json({ ok: true })
    : NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
}

export async function POST(req: Request) {
  const { password } = await req.json();
  if (password !== ADMIN_PASSWORD)
    return NextResponse.json({ error: 'Noto\'g\'ri parol' }, { status: 401 });
  const res = NextResponse.json({ ok: true });
  res.cookies.set(COOKIE, ADMIN_PASSWORD, { httpOnly: true, path: '/', maxAge: 60 * 60 * 24 * 30 });
  return res;
}

export async function DELETE() {
  const res = NextResponse.json({ ok: true });
  res.cookies.delete(COOKIE);
  return res;
}
