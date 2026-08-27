'use client';
import { useEffect, useRef, useState } from 'react';

type Device = {
  _id: string; name: string; deviceId: string;
  pairingCode: string; online: boolean;
  lastSeen: string; battery?: number;
};
type Req = {
  _id: string; deviceId: string; type: string;
  status: string; createdAt: string; resultUrl?: string;
};

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#f59e0b', DONE: '#10b981', FAILED: '#ef4444',
};
const TYPE_ICON: Record<string, string> = {
  SCREENSHOT: '📸', CAMERA_FRONT: '🤳', CAMERA_BACK: '📷', SCREEN_SHARE: '🖥️',
};

function timeAgo(dateStr: string) {
  const sec = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
  if (sec < 60) return `${sec}s oldin`;
  if (sec < 3600) return `${Math.floor(sec / 60)}m oldin`;
  return `${Math.floor(sec / 3600)}s oldin`;
}

function batteryIcon(b?: number) {
  if (b == null) return '';
  if (b > 70) return '🔋';
  if (b > 30) return '🪫';
  return '🔴';
}

export default function Home() {
  const [devices, setDevices]   = useState<Device[]>([]);
  const [requests, setRequests] = useState<Req[]>([]);
  const [pairCode, setPairCode] = useState('');
  const [name, setName]         = useState('My child');
  const [busy, setBusy]         = useState(false);
  const [preview, setPreview]   = useState<string | null>(null);
  const [filter, setFilter]     = useState<'ALL'|'PENDING'|'DONE'|'FAILED'>('ALL');
  const prevDoneIds = useRef<Set<string>>(new Set());
  const audioRef = useRef<AudioContext | null>(null);

  function playBeep() {
    try {
      const ctx = new AudioContext();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain); gain.connect(ctx.destination);
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);
      osc.start(); osc.stop(ctx.currentTime + 0.4);
    } catch (_) {}
  }

  async function refresh() {
    const d = await fetch('/api/device/register').then(r => r.json()).catch(() => ({}));
    setDevices(d.devices ?? []);
    const x = await fetch('/api/request?mode=list').then(r => r.json()).catch(() => ({}));
    const reqs: Req[] = x.requests ?? [];
    setRequests(reqs);

    // Yangi DONE so'rov bo'lsa — beep
    const newDone = reqs.filter(r => r.status === 'DONE' && !prevDoneIds.current.has(r._id));
    if (newDone.length > 0) {
      playBeep();
      newDone.forEach(r => prevDoneIds.current.add(r._id));
    }
    reqs.forEach(r => { if (r.status !== 'PENDING') prevDoneIds.current.add(r._id); });
  }

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 5000);
    return () => clearInterval(t);
  }, []);

  async function connect() {
    if (!pairCode.trim()) return;
    setBusy(true);
    await fetch('/api/device/register', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ pairingCode: pairCode.trim(), name }),
    });
    setBusy(false); setPairCode(''); refresh();
  }

  async function sendReq(deviceId: string, type: string) {
    await fetch('/api/request', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ deviceId, type }),
    });
    refresh();
  }

  async function deleteReq(id: string) {
    await fetch('/api/request', {
      method: 'DELETE',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id }),
    });
    refresh();
  }

  async function clearAll() {
    if (!confirm('Barcha so\'rovlarni o\'chirishni xohlaysizmi?')) return;
    await fetch('/api/request', {
      method: 'DELETE',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ all: true }),
    });
    refresh();
  }

  const filtered = requests.filter(r => filter === 'ALL' || r.status === filter);
  const counts = {
    ALL: requests.length,
    PENDING: requests.filter(r => r.status === 'PENDING').length,
    DONE: requests.filter(r => r.status === 'DONE').length,
    FAILED: requests.filter(r => r.status === 'FAILED').length,
  };

  return (
    <main style={{ maxWidth: 920, margin: '0 auto', padding: '32px 16px', fontFamily: 'system-ui,sans-serif', background: '#f9fafb', minHeight: '100vh' }}>

      {/* Header */}
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 26, fontWeight: 700, margin: 0 }}>🛡️ Family Guard</h1>
        <p style={{ color: '#6b7280', margin: '4px 0 0' }}>Ota-ona nazorat paneli</p>
      </div>

      {/* Qurilma ulash */}
      <section style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24, marginBottom: 20 }}>
        <h2 style={{ fontSize: 16, fontWeight: 600, margin: '0 0 14px' }}>Qurilma ulash</h2>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <input placeholder="Bolaning ismi" value={name} onChange={e => setName(e.target.value)}
            style={{ padding: '10px 14px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, flex: 1, minWidth: 130 }}/>
          <input placeholder="Pairing code" value={pairCode}
            onChange={e => setPairCode(e.target.value.toUpperCase())}
            onKeyDown={e => e.key === 'Enter' && connect()}
            style={{ padding: '10px 14px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, flex: 1, minWidth: 160, letterSpacing: 2, fontWeight: 600 }}/>
          <button onClick={connect} disabled={busy || !pairCode.trim()}
            style={{ padding: '10px 24px', background: busy ? '#9ca3af' : '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: busy ? 'not-allowed' : 'pointer', fontWeight: 600, fontSize: 14 }}>
            {busy ? '...' : 'Ulash'}
          </button>
        </div>
      </section>

      {/* Qurilmalar */}
      {devices.length > 0 && (
        <section style={{ display: 'grid', gap: 16, marginBottom: 20 }}>
          {devices.map(d => (
            <article key={d._id} style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                <span style={{ width: 10, height: 10, borderRadius: '50%', background: d.online ? '#10b981' : '#9ca3af', boxShadow: d.online ? '0 0 0 3px #d1fae5' : 'none', flexShrink: 0 }}/>
                <h3 style={{ fontSize: 17, fontWeight: 600, margin: 0 }}>{d.name}</h3>
                <span style={{ fontSize: 12, color: d.online ? '#10b981' : '#9ca3af', fontWeight: 500 }}>{d.online ? 'online' : 'offline'}</span>
                {d.battery != null && (
                  <span style={{ fontSize: 12, color: '#6b7280', marginLeft: 'auto' }}>
                    {batteryIcon(d.battery)} {d.battery}%
                  </span>
                )}
              </div>
              <p style={{ fontSize: 11, color: '#9ca3af', margin: '0 0 16px' }}>
                ID: {d.deviceId} · Oxirgi: {timeAgo(d.lastSeen)}
              </p>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {[
                  { type: 'SCREENSHOT',   label: '📸 Screenshot' },
                  { type: 'CAMERA_FRONT', label: '🤳 Oldingi kamera' },
                  { type: 'CAMERA_BACK',  label: '📷 Orqa kamera' },
                  { type: 'SCREEN_SHARE', label: '🖥️ Screen share' },
                ].map(({ type, label }) => (
                  <button key={type} onClick={() => sendReq(d.deviceId, type)}
                    style={{ padding: '8px 14px', background: '#f3f4f6', border: '1px solid #e5e7eb', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 500 }}>
                    {label}
                  </button>
                ))}
              </div>
            </article>
          ))}
        </section>
      )}

      {/* So'rovlar */}
      <section style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, margin: 0, flex: 1 }}>So'rovlar tarixi</h2>
          {requests.length > 0 && (
            <button onClick={clearAll}
              style={{ fontSize: 12, color: '#ef4444', background: 'none', border: 'none', cursor: 'pointer', padding: '4px 8px' }}>
              Hammasini o'chirish
            </button>
          )}
        </div>

        {/* Filter tabs */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
          {(['ALL','PENDING','DONE','FAILED'] as const).map(f => (
            <button key={f} onClick={() => setFilter(f)}
              style={{ padding: '5px 14px', borderRadius: 20, border: '1px solid #e5e7eb', fontSize: 12, fontWeight: 600, cursor: 'pointer',
                background: filter === f ? '#2563eb' : '#f3f4f6',
                color: filter === f ? '#fff' : '#6b7280' }}>
              {f} {counts[f] > 0 && <span>({counts[f]})</span>}
            </button>
          ))}
        </div>

        {filtered.length === 0 && (
          <p style={{ color: '#9ca3af', fontSize: 14, textAlign: 'center', padding: '24px 0' }}>
            {filter === 'ALL' ? 'Hali so\'rov yo\'q' : `${filter} so'rovlar yo'q`}
          </p>
        )}

        {filtered.map(r => (
          <div key={r._id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0', borderBottom: '1px solid #f3f4f6' }}>

            {/* Thumbnail */}
            {r.resultUrl && r.status === 'DONE' ? (
              <img src={r.resultUrl} alt="" onClick={() => setPreview(r.resultUrl!)}
                style={{ width: 48, height: 36, objectFit: 'cover', borderRadius: 6, cursor: 'pointer', flexShrink: 0, border: '1px solid #e5e7eb' }}/>
            ) : (
              <span style={{ width: 48, textAlign: 'center', fontSize: 22, flexShrink: 0 }}>
                {TYPE_ICON[r.type] ?? '📋'}
              </span>
            )}

            <span style={{ fontSize: 13, fontWeight: 500, minWidth: 110 }}>{r.type}</span>
            <span style={{ fontSize: 12, fontWeight: 700, color: STATUS_COLOR[r.status] ?? '#6b7280', minWidth: 60 }}>{r.status}</span>
            <span style={{ fontSize: 11, color: '#9ca3af', flex: 1 }}>{timeAgo(r.createdAt)}</span>

            {r.resultUrl && r.status === 'DONE' && (
              <button onClick={() => setPreview(r.resultUrl!)}
                style={{ padding: '4px 10px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 12, flexShrink: 0 }}>
                Ko'rish
              </button>
            )}
            <button onClick={() => deleteReq(r._id)}
              style={{ padding: '4px 8px', background: 'none', border: '1px solid #e5e7eb', borderRadius: 6, cursor: 'pointer', fontSize: 12, color: '#9ca3af', flexShrink: 0 }}>
              ✕
            </button>
          </div>
        ))}
      </section>

      {/* Preview modal */}
      {preview && (
        <div onClick={() => setPreview(null)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.9)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999, cursor: 'zoom-out' }}>
          <div onClick={e => e.stopPropagation()} style={{ position: 'relative' }}>
            <img src={preview} alt="preview"
              style={{ maxWidth: '92vw', maxHeight: '88vh', borderRadius: 12, display: 'block', boxShadow: '0 25px 60px rgba(0,0,0,0.5)' }}/>
            <button onClick={() => setPreview(null)}
              style={{ position: 'absolute', top: -14, right: -14, width: 30, height: 30, borderRadius: '50%', background: '#fff', border: 'none', cursor: 'pointer', fontSize: 16, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              ×
            </button>
            <a href={preview} download target="_blank"
              style={{ position: 'absolute', bottom: -14, right: -14, background: '#2563eb', color: '#fff', borderRadius: 8, padding: '6px 12px', fontSize: 12, textDecoration: 'none', fontWeight: 600 }}>
              ⬇ Yuklab olish
            </a>
          </div>
        </div>
      )}
    </main>
  );
}
