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
type AppEntry = { name: string; package: string; minutes?: number };
type ModalContent =
  | { kind: 'image'; url: string }
  | { kind: 'map'; lat: number; lng: number }
  | { kind: 'apps'; data: AppEntry[]; title: string }
  | { kind: 'usage'; data: AppEntry[] };

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#f59e0b', DONE: '#10b981', FAILED: '#ef4444',
};
const TYPE_META: Record<string, { icon: string; label: string }> = {
  SCREENSHOT:   { icon: '📸', label: 'Screenshot' },
  CAMERA_FRONT: { icon: '🤳', label: 'Oldingi kamera' },
  CAMERA_BACK:  { icon: '📷', label: 'Orqa kamera' },
  SCREEN_SHARE: { icon: '🖥️', label: 'Screen share' },
  LOCATION:     { icon: '📍', label: 'Lokatsiya' },
  APP_LIST:     { icon: '📋', label: 'Ilovalar' },
  APP_USAGE:    { icon: '📊', label: 'Foydalanish' },
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
function isLatLng(url: string) {
  return /^-?\d+\.?\d*,-?\d+\.?\d*$/.test(url.trim());
}
function isImageType(type: string) {
  return ['SCREENSHOT', 'CAMERA_FRONT', 'CAMERA_BACK', 'SCREEN_SHARE'].includes(type);
}
function isJsonType(type: string) {
  return ['APP_LIST', 'APP_USAGE'].includes(type);
}

export default function Home() {
  const [devices, setDevices]   = useState<Device[]>([]);
  const [requests, setRequests] = useState<Req[]>([]);
  const [pairCode, setPairCode] = useState('');
  const [name, setName]         = useState('My child');
  const [busy, setBusy]         = useState(false);
  const [modal, setModal]       = useState<ModalContent | null>(null);
  const [filter, setFilter]     = useState<'ALL'|'PENDING'|'DONE'|'FAILED'>('ALL');
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [disconnecting, setDisconnecting] = useState<string | null>(null);
  const prevDoneIds = useRef<Set<string>>(new Set());

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
    const newDone = reqs.filter(r => r.status === 'DONE' && !prevDoneIds.current.has(r._id));
    if (newDone.length > 0) { playBeep(); newDone.forEach(r => prevDoneIds.current.add(r._id)); }
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

  // Qurilmani o'chirish
  async function disconnectDevice(id: string, deviceName: string) {
    if (!confirm(`"${deviceName}" qurilmasini uzmoqchimisiz?\nBarcha so'rovlar ham o'chadi.`)) return;
    setDisconnecting(id);
    await fetch('/api/device/register', {
      method: 'DELETE',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id }),
    });
    setDisconnecting(null);
    refresh();
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

  async function openResult(r: Req) {
    if (!r.resultUrl || r.status !== 'DONE') return;
    setLoadingId(r._id);

    if (isLatLng(r.resultUrl)) {
      const [lat, lng] = r.resultUrl.split(',').map(Number);
      setModal({ kind: 'map', lat, lng });
      setLoadingId(null);
      return;
    }

    if (isJsonType(r.type)) {
      try {
        const res  = await fetch(r.resultUrl);
        const data: AppEntry[] = await res.json();
        if (r.type === 'APP_LIST') setModal({ kind: 'apps', data, title: 'O\'rnatilgan ilovalar' });
        else setModal({ kind: 'usage', data });
      } catch (_) {}
      setLoadingId(null);
      return;
    }

    setModal({ kind: 'image', url: r.resultUrl });
    setLoadingId(null);
  }

  const filtered = requests.filter(r => filter === 'ALL' || r.status === filter);
  const counts = {
    ALL: requests.length,
    PENDING: requests.filter(r => r.status === 'PENDING').length,
    DONE: requests.filter(r => r.status === 'DONE').length,
    FAILED: requests.filter(r => r.status === 'FAILED').length,
  };

  const BUTTONS = [
    { type: 'SCREENSHOT',   label: '📸 Screenshot' },
    { type: 'CAMERA_FRONT', label: '🤳 Oldingi kamera' },
    { type: 'CAMERA_BACK',  label: '📷 Orqa kamera' },
    { type: 'SCREEN_SHARE', label: '🖥️ Screen share' },
    { type: 'LOCATION',     label: '📍 Lokatsiya' },
    { type: 'APP_LIST',     label: '📋 Ilovalar ro\'yxati' },
    { type: 'APP_USAGE',    label: '📊 Foydalanish vaqti' },
  ];

  return (
    <main style={{ maxWidth: 960, margin: '0 auto', padding: '32px 16px', fontFamily: 'system-ui,sans-serif', background: '#f9fafb', minHeight: '100vh' }}>

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
            style={{ padding: '10px 24px', background: busy ? '#9ca3af' : '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: busy ? 'not-allowed' : 'pointer', fontWeight: 600 }}>
            {busy ? '...' : 'Ulash'}
          </button>
        </div>
      </section>

      {/* Qurilmalar */}
      {devices.map(d => (
        <section key={d._id} style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24, marginBottom: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <span style={{ width: 10, height: 10, borderRadius: '50%', background: d.online ? '#10b981' : '#9ca3af', boxShadow: d.online ? '0 0 0 3px #d1fae5' : 'none', flexShrink: 0 }}/>
            <h3 style={{ fontSize: 17, fontWeight: 600, margin: 0 }}>{d.name}</h3>
            <span style={{ fontSize: 12, color: d.online ? '#10b981' : '#9ca3af', fontWeight: 500 }}>{d.online ? 'online' : 'offline'}</span>
            {d.battery != null && (
              <span style={{ fontSize: 13, color: '#6b7280' }}>
                {batteryIcon(d.battery)} {d.battery}%
              </span>
            )}
            {/* Uzish tugmasi */}
            <button
              onClick={() => disconnectDevice(d._id, d.name)}
              disabled={disconnecting === d._id}
              style={{
                marginLeft: 'auto',
                padding: '5px 12px',
                background: 'none',
                border: '1px solid #fca5a5',
                borderRadius: 8,
                cursor: disconnecting === d._id ? 'not-allowed' : 'pointer',
                fontSize: 12,
                color: '#ef4444',
                fontWeight: 600,
              }}>
              {disconnecting === d._id ? '...' : '🔌 Uzish'}
            </button>
          </div>
          <p style={{ fontSize: 11, color: '#9ca3af', margin: '0 0 16px' }}>
            Oxirgi: {timeAgo(d.lastSeen)}
          </p>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {BUTTONS.map(({ type, label }) => (
              <button key={type} onClick={() => sendReq(d.deviceId, type)}
                style={{ padding: '8px 14px', background: '#f3f4f6', border: '1px solid #e5e7eb', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 500 }}>
                {label}
              </button>
            ))}
          </div>
        </section>
      ))}

      {/* So'rovlar */}
      <section style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, margin: 0, flex: 1 }}>So'rovlar tarixi</h2>
          {requests.length > 0 && (
            <button onClick={clearAll} style={{ fontSize: 12, color: '#ef4444', background: 'none', border: 'none', cursor: 'pointer' }}>
              Hammasini o'chirish
            </button>
          )}
        </div>

        <div style={{ display: 'flex', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
          {(['ALL','PENDING','DONE','FAILED'] as const).map(f => (
            <button key={f} onClick={() => setFilter(f)}
              style={{ padding: '5px 14px', borderRadius: 20, border: '1px solid #e5e7eb', fontSize: 12, fontWeight: 600, cursor: 'pointer',
                background: filter === f ? '#2563eb' : '#f3f4f6',
                color: filter === f ? '#fff' : '#6b7280' }}>
              {f} {counts[f] > 0 && `(${counts[f]})`}
            </button>
          ))}
        </div>

        {filtered.length === 0 && (
          <p style={{ color: '#9ca3af', fontSize: 14, textAlign: 'center', padding: '24px 0' }}>So'rovlar yo'q</p>
        )}

        {filtered.map(r => {
          const meta = TYPE_META[r.type] ?? { icon: '📋', label: r.type };
          const hasResult = r.resultUrl && r.status === 'DONE';
          return (
            <div key={r._id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 0', borderBottom: '1px solid #f3f4f6' }}>
              {hasResult && isImageType(r.type) ? (
                <img src={r.resultUrl} alt="" onClick={() => openResult(r)}
                  style={{ width: 48, height: 36, objectFit: 'cover', borderRadius: 6, cursor: 'pointer', flexShrink: 0, border: '1px solid #e5e7eb' }}/>
              ) : (
                <span style={{ width: 48, textAlign: 'center', fontSize: 22, flexShrink: 0 }}>{meta.icon}</span>
              )}
              <span style={{ fontSize: 13, fontWeight: 500, minWidth: 120 }}>{meta.label}</span>
              <span style={{ fontSize: 12, fontWeight: 700, color: STATUS_COLOR[r.status] ?? '#6b7280', minWidth: 55 }}>{r.status}</span>
              <span style={{ fontSize: 11, color: '#9ca3af', flex: 1 }}>{timeAgo(r.createdAt)}</span>
              {hasResult && (
                <button onClick={() => openResult(r)} disabled={loadingId === r._id}
                  style={{ padding: '4px 10px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 12, flexShrink: 0 }}>
                  {loadingId === r._id ? '...' : 'Ko\'rish'}
                </button>
              )}
              <button onClick={() => deleteReq(r._id)}
                style={{ padding: '4px 8px', background: 'none', border: '1px solid #e5e7eb', borderRadius: 6, cursor: 'pointer', fontSize: 12, color: '#9ca3af', flexShrink: 0 }}>✕</button>
            </div>
          );
        })}
      </section>

      {/* Modal */}
