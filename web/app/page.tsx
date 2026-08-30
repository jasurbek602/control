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
  | { kind: 'apps'; data: AppEntry[] }
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
  if (sec < 3600) return `${Math.floor(sec / 60)}d oldin`;
  if (sec < 86400) return `${Math.floor(sec / 3600)}s oldin`;
  return `${Math.floor(sec / 86400)}k oldin`;
}
function batteryColor(b?: number) {
  if (b == null) return '#6b7280';
  if (b > 50) return '#10b981';
  if (b > 20) return '#f59e0b';
  return '#ef4444';
}
function isLatLng(url: string) {
  return /^-?\d+\.?\d*,-?\d+\.?\d*$/.test(url.trim());
}
function isImageType(t: string) {
  return ['SCREENSHOT','CAMERA_FRONT','CAMERA_BACK','SCREEN_SHARE'].includes(t);
}
function isJsonType(t: string) {
  return ['APP_LIST','APP_USAGE'].includes(t);
}

const S = {
  page: {
    minHeight: '100vh',
    background: 'linear-gradient(135deg, #0f0f1a 0%, #1a1a2e 50%, #16213e 100%)',
    fontFamily: 'system-ui, -apple-system, sans-serif',
    color: '#e2e8f0',
    padding: '0 0 60px',
  } as React.CSSProperties,
  header: {
    background: 'rgba(255,255,255,0.03)',
    backdropFilter: 'blur(20px)',
    borderBottom: '1px solid rgba(255,255,255,0.08)',
    padding: '20px 32px',
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    position: 'sticky' as const,
    top: 0,
    zIndex: 100,
  },
  wrap: { maxWidth: 960, margin: '0 auto', padding: '32px 20px' },
  card: {
    background: 'rgba(255,255,255,0.05)',
    backdropFilter: 'blur(10px)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 20,
    padding: 24,
    marginBottom: 20,
  } as React.CSSProperties,
  input: {
    padding: '11px 16px',
    background: 'rgba(255,255,255,0.07)',
    border: '1px solid rgba(255,255,255,0.12)',
    borderRadius: 10,
    fontSize: 14,
    color: '#e2e8f0',
    outline: 'none',
    flex: 1,
  } as React.CSSProperties,
  btnPrimary: {
    padding: '11px 24px',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff',
    border: 'none',
    borderRadius: 10,
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: 14,
    boxShadow: '0 4px 15px rgba(99,102,241,0.4)',
  } as React.CSSProperties,
  btnAction: {
    padding: '7px 14px',
    background: 'rgba(255,255,255,0.07)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 8,
    cursor: 'pointer',
    fontSize: 12,
    color: '#cbd5e1',
    fontWeight: 500,
    transition: 'all 0.2s',
  } as React.CSSProperties,
};

export default function Home() {
  const [devices, setDevices]   = useState<Device[]>([]);
  const [requests, setRequests] = useState<Req[]>([]);
  const [pairCode, setPairCode] = useState('');
  const [name, setName]         = useState('My child');
  const [busy, setBusy]         = useState(false);
  const [modal, setModal]       = useState<ModalContent | null>(null);
  const [filter, setFilter]     = useState<'ALL'|'PENDING'|'DONE'|'FAILED'>('ALL');
  const [loadingId, setLoadingId]   = useState<string|null>(null);
  const [deletingId, setDeletingId] = useState<string|null>(null);
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

  async function deleteDevice(id: string, dName: string) {
    if (!confirm(`"${dName}" qurilmasini o'chirishni xohlaysizmi?\nBarcha so'rovlar ham o'chadi.`)) return;
    setDeletingId(id);
    await fetch('/api/device/register', {
      method: 'DELETE',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id }),
    });
    setDeletingId(null);
    refresh();
  }

  async function deleteAllOffline() {
    const offline = devices.filter(d => !d.online);
    if (offline.length === 0) return;
    if (!confirm(`${offline.length} ta offline qurilmani o'chirishni xohlaysizmi?`)) return;
    for (const d of offline) {
      await fetch('/api/device/register', {
        method: 'DELETE',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ id: d._id }),
      });
    }
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
      setLoadingId(null); return;
    }
    if (isJsonType(r.type)) {
      try {
        const res = await fetch(r.resultUrl);
        const data: AppEntry[] = await res.json();
        setModal({ kind: r.type === 'APP_LIST' ? 'apps' : 'usage', data });
      } catch (_) {}
      setLoadingId(null); return;
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
  const offlineCount = devices.filter(d => !d.online).length;

  const BTNS = [
    { type: 'SCREENSHOT',   label: '📸 Screenshot' },
    { type: 'CAMERA_FRONT', label: '🤳 Selfie' },
    { type: 'CAMERA_BACK',  label: '📷 Orqa kamera' },
    { type: 'SCREEN_SHARE', label: '🖥️ Screen share' },
    { type: 'LOCATION',     label: '📍 Lokatsiya' },
    { type: 'APP_LIST',     label: '📋 Ilovalar' },
    { type: 'APP_USAGE',    label: '📊 Foydalanish' },
  ];

  return (
    <div style={S.page}>
      {/* Header */}
      <header style={S.header}>
        <span style={{ fontSize: 24 }}>🛡️</span>
        <div>
          <div style={{ fontSize: 18, fontWeight: 700, color: '#f1f5f9' }}>Family Guard</div>
          <div style={{ fontSize: 11, color: '#64748b' }}>Ota-ona nazorat paneli</div>
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 8px #10b981', display: 'inline-block' }}/>
          <span style={{ fontSize: 12, color: '#64748b' }}>{devices.filter(d=>d.online).length} online</span>
        </div>
      </header>

      <div style={S.wrap}>

        {/* Pairing */}
        <div style={S.card}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#94a3b8', marginBottom: 14, textTransform: 'uppercase', letterSpacing: 1 }}>
            Qurilma ulash
          </div>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <input placeholder="Bolaning ismi" value={name} onChange={e => setName(e.target.value)}
              style={{ ...S.input, minWidth: 130 }}/>
            <input placeholder="Pairing code" value={pairCode}
              onChange={e => setPairCode(e.target.value.toUpperCase())}
              onKeyDown={e => e.key === 'Enter' && connect()}
              style={{ ...S.input, minWidth: 160, letterSpacing: 3, fontWeight: 700 }}/>
            <button onClick={connect} disabled={busy || !pairCode.trim()}
              style={{ ...S.btnPrimary, opacity: busy || !pairCode.trim() ? 0.5 : 1 }}>
              {busy ? '...' : 'Ulash'}
            </button>
          </div>
        </div>

        {/* Offline tozalash */}
        {offlineCount > 0 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12, marginTop: -8 }}>
            <button onClick={deleteAllOffline}
              style={{ ...S.btnAction, color: '#f87171', borderColor: 'rgba(248,113,113,0.3)', background: 'rgba(248,113,113,0.08)' }}>
              🗑 {offlineCount} ta offline qurilmani o'chirish
            </button>
          </div>
        )}

        {/* Qurilmalar */}
        {devices.map(d => (
          <div key={d._id} style={{
            ...S.card,
            borderColor: d.online ? 'rgba(16,185,129,0.3)' : 'rgba(255,255,255,0.07)',
            background: d.online
              ? 'rgba(16,185,129,0.05)'
              : 'rgba(255,255,255,0.03)',
          }}>
            {/* Device header */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
              <div style={{ position: 'relative' }}>
                <span style={{ fontSize: 32 }}>📱</span>
                <span style={{
                  position: 'absolute', bottom: 0, right: -2,
                  width: 10, height: 10, borderRadius: '50%',
                  background: d.online ? '#10b981' : '#475569',
                  boxShadow: d.online ? '0 0 8px #10b981' : 'none',
                  border: '2px solid #1a1a2e',
                  display: 'block',
                }}/>
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 16, fontWeight: 700, color: '#f1f5f9' }}>{d.name}</div>
                <div style={{ fontSize: 11, color: '#475569' }}>
                  {d.online ? '🟢 Online' : `⚫ Offline · ${timeAgo(d.lastSeen)}`}
                </div>
              </div>

              {/* Battery */}
              {d.battery != null && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'rgba(255,255,255,0.06)', padding: '4px 10px', borderRadius: 8 }}>
                  <div style={{ width: 22, height: 11, border: `1.5px solid ${batteryColor(d.battery)}`, borderRadius: 3, position: 'relative' }}>
                    <div style={{ position: 'absolute', right: -4, top: '50%', transform: 'translateY(-50%)', width: 3, height: 5, background: batteryColor(d.battery), borderRadius: '0 2px 2px 0' }}/>
                    <div style={{ height: '100%', width: `${d.battery}%`, background: batteryColor(d.battery), borderRadius: 2, transition: 'width 0.5s' }}/>
                  </div>
                  <span style={{ fontSize: 12, fontWeight: 600, color: batteryColor(d.battery) }}>{d.battery}%</span>
                </div>
              )}

              {/* O'chirish */}
              <button onClick={() => deleteDevice(d._id, d.name)}
                disabled={deletingId === d._id}
                style={{ ...S.btnAction, color: '#f87171', borderColor: 'rgba(248,113,113,0.3)', background: 'rgba(248,113,113,0.08)' }}>
                {deletingId === d._id ? '...' : '🗑 O\'chirish'}
              </button>
            </div>

            {/* Tugmalar */}
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {BTNS.map(({ type, label }) => (
                <button key={type} onClick={() => sendReq(d.deviceId, type)}
                  disabled={!d.online}
                  style={{
                    ...S.btnAction,
                    opacity: d.online ? 1 : 0.4,
                    cursor: d.online ? 'pointer' : 'not-allowed',
                  }}>
                  {label}
                </button>
              ))}
            </div>
          </div>
        ))}

        {/* So'rovlar */}
        <div style={S.card}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, flex: 1 }}>
              So'rovlar tarixi
            </div>
            {requests.length > 0 && (
              <button onClick={clearAll}
                style={{ ...S.btnAction, color: '#f87171', borderColor: 'rgba(248,113,113,0.3)', background: 'rgba(248,113,113,0.08)', fontSize: 11 }}>
                Hammasini o'chirish
              </button>
            )}
          </div>

          {/* Filter */}
          <div style={{ display: 'flex', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
            {(['ALL','PENDING','DONE','FAILED'] as const).map(f => (
              <button key={f} onClick={() => setFilter(f)}
                style={{
                  padding: '5px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer',
                  border: filter === f ? 'none' : '1px solid rgba(255,255,255,0.1)',
                  background: filter === f
                    ? 'linear-gradient(135deg, #6366f1, #8b5cf6)'
                    : 'rgba(255,255,255,0.05)',
                  color: filter === f ? '#fff' : '#64748b',
                  boxShadow: filter === f ? '0 2px 10px rgba(99,102,241,0.4)' : 'none',
                }}>
                {f} {counts[f] > 0 && <span style={{ opacity: 0.8 }}>({counts[f]})</span>}
              </button>
            ))}
          </div>

          {filtered.length === 0 && (
            <div style={{ textAlign: 'center', padding: '32px 0', color: '#475569', fontSize: 14 }}>
              So'rovlar yo'q
            </div>
          )}

          {filtered.map(r => {
            const meta = TYPE_META[r.type] ?? { icon: '📋', label: r.type };
            const hasResult = r.resultUrl && r.status === 'DONE';
            const dev = devices.find(d => d.deviceId === r.deviceId);
            return (
              <div key={r._id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                {hasResult && isImageType(r.type) ? (
                  <img src={r.resultUrl} alt="" onClick={() => openResult(r)}
                    style={{ width: 52, height: 38, objectFit: 'cover', borderRadius: 8, cursor: 'pointer', flexShrink: 0, border: '1px solid rgba(255,255,255,0.1)' }}/>
                ) : (
                  <span style={{ width: 52, textAlign: 'center', fontSize: 24, flexShrink: 0 }}>{meta.icon}</span>
                )}

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#e2e8f0' }}>{meta.label}</span>
                    <span style={{ fontSize: 11, fontWeight: 700, color: STATUS_COLOR[r.status] ?? '#6b7280',
                      background: `${STATUS_COLOR[r.status]}20`, padding: '2px 8px', borderRadius: 10 }}>
                      {r.status}
                    </span>
                  </div>
                  <div style={{ fontSize: 11, color: '#475569', marginTop: 2 }}>
                    {dev?.name ?? r.deviceId.slice(0,8)} · {timeAgo(r.createdAt)}
                  </div>
                </div>

                {hasResult && (
                  <button onClick={() => openResult(r)} disabled={loadingId === r._id}
                    style={{ ...S.btnPrimary, padding: '5px 12px', fontSize: 12, flexShrink: 0 }}>
                    {loadingId === r._id ? '...' : 'Ko\'rish'}
                  </button>
                )}
                <button onClick={() => deleteReq(r._id)}
                  style={{ ...S.btnAction, padding: '5px 8px', flexShrink: 0, color: '#475569' }}>✕</button>
              </div>
            );
          })}
        </div>
      </div>

      {/* Modal */}
      {modal && (
        <div onClick={() => setModal(null)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.92)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999, padding: 20 }}>
          <div onClick={e => e.stopPropagation()}
            style={{ background: '#1e1e2e', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 20, overflow: 'hidden', maxWidth: '95vw', maxHeight: '90vh', overflowY: 'auto', position: 'relative' }}>

            <button onClick={() => setModal(null)}
              style={{ position: 'sticky', top: 8, float: 'right', margin: '8px 8px 0 0', width: 32, height: 32, borderRadius: '50%', background: 'rgba(255,255,255,0.1)', border: 'none', cursor: 'pointer', fontSize: 18, fontWeight: 700, color: '#e2e8f0', zIndex: 10 }}>×</button>

            {modal.kind === 'image' && (
              <div>
                <img src={modal.url} alt="result" style={{ maxWidth: '88vw', maxHeight: '80vh', display: 'block' }}/>
                <div style={{ padding: '12px 16px' }}>
                  <a href={modal.url} download target="_blank"
                    style={{ ...S.btnPrimary, display: 'inline-block', textDecoration: 'none', fontSize: 13 }}>
                    ⬇ Yuklab olish
                  </a>
                </div>
              </div>
            )}

            {modal.kind === 'map' && (
              <div style={{ padding: 24 }}>
                <h3 style={{ fontSize: 16, fontWeight: 700, margin: '0 0 16px', color: '#f1f5f9' }}>📍 Bolaning joylashuvi</h3>
                <iframe
                  src={`https://maps.google.com/maps?q=${modal.lat},${modal.lng}&z=16&output=embed`}
                  width="100%" height="380"
                  style={{ border: 'none', borderRadius: 12, display: 'block', minWidth: 320 }}/>
                <div style={{ marginTop: 12, display: 'flex', gap: 10, alignItems: 'center' }}>
                  <a href={`https://maps.google.com/?q=${modal.lat},${modal.lng}`} target="_blank"
                    style={{ ...S.btnPrimary, display: 'inline-block', textDecoration: 'none', fontSize: 13 }}>
                    🗺 Google Maps da ochish
                  </a>
                  <span style={{ fontSize: 12, color: '#475569' }}>
                    {modal.lat.toFixed(6)}, {modal.lng.toFixed(6)}
                  </span>
                </div>
              </div>
            )}

            {modal.kind === 'apps' && (
              <div style={{ padding: 24, minWidth: 340 }}>
                <h3 style={{ fontSize: 16, fontWeight: 700, margin: '0 0 4px', color: '#f1f5f9' }}>📋 O'rnatilgan ilovalar</h3>
                <p style={{ fontSize: 12, color: '#475569', margin: '0 0 16px' }}>Jami: {modal.data.length} ta</p>
                <div style={{ display: 'grid', gap: 3 }}>
                  {modal.data.map((app, i) => (
                    <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', background: i % 2 === 0 ? 'rgba(255,255,255,0.04)' : 'transparent', borderRadius: 6 }}>
                      <span style={{ fontSize: 13, fontWeight: 500, color: '#e2e8f0', flex: 1 }}>{app.name}</span>
                      <span style={{ fontSize: 10, color: '#475569' }}>{app.package}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {modal.kind === 'usage' && (
              <div style={{ padding: 24, minWidth: 380 }}>
                <h3 style={{ fontSize: 16, fontWeight: 700, margin: '0 0 4px', color: '#f1f5f9' }}>📊 So'nggi 24 soat</h3>
                <p style={{ fontSize: 12, color: '#475569', margin: '0 0 16px' }}>Eng ko'p ishlatiladigan ilovalar</p>
                <div style={{ display: 'grid', gap: 8 }}>
                  {modal.data.map((app, i) => {
                    const max   = modal.data[0]?.minutes ?? 1;
                    const pct   = Math.round(((app.minutes ?? 0) / max) * 100);
                    const hours = Math.floor((app.minutes ?? 0) / 60);
                    const mins  = (app.minutes ?? 0) % 60;
                    const label = hours > 0 ? `${hours}s ${mins}d` : `${mins} daqiqa`;
                    const colors = ['#6366f1','#8b5cf6','#06b6d4','#10b981','#f59e0b'];
                    const clr = colors[i % colors.length];
                    return (
                      <div key={i} style={{ padding: '10px 14px', background: 'rgba(255,255,255,0.04)', borderRadius: 10, border: '1px solid rgba(255,255,255,0.06)' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                          <span style={{ fontSize: 13, fontWeight: 600, color: '#e2e8f0' }}>{app.name}</span>
                          <span style={{ fontSize: 12, color: clr, fontWeight: 700 }}>{label}</span>
                        </div>
                        <div style={{ height: 5, background: 'rgba(255,255,255,0.08)', borderRadius: 3, overflow: 'hidden' }}>
                          <div style={{ height: '100%', width: `${pct}%`, background: clr, borderRadius: 3, boxShadow: `0 0 8px ${clr}80` }}/>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
