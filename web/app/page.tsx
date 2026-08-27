'use client';
import { useEffect, useState } from 'react';

type Device = { _id: string; name: string; deviceId: string; pairingCode: string; online: boolean; lastSeen: string };
type Req = { _id: string; deviceId: string; type: string; status: string; createdAt: string; resultUrl?: string };

export default function Home() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [requests, setRequests] = useState<Req[]>([]);
  const [pairCode, setPairCode] = useState('');
  const [name, setName] = useState('My child');
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);

  async function refresh() {
    const d = await fetch('/api/device/register').then(r => r.json()).catch(() => ({}));
    setDevices(d.devices ?? []);
    const x = await fetch('/api/request?mode=list').then(r => r.json()).catch(() => ({}));
    setRequests(x.requests ?? []);
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
    setBusy(false);
    setPairCode('');
    refresh();
  }

  async function sendRequest(deviceId: string, type: string) {
    await fetch('/api/request', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ deviceId, type }),
    });
    refresh();
  }

  const statusColor: Record<string, string> = {
    PENDING: '#f59e0b',
    DONE: '#10b981',
    FAILED: '#ef4444',
  };

  return (
    <main style={{ maxWidth: 900, margin: '0 auto', padding: '32px 16px', fontFamily: 'system-ui, sans-serif' }}>
      <h1 style={{ fontSize: 28, fontWeight: 700, marginBottom: 4 }}>Family Guard</h1>
      <p style={{ color: '#6b7280', marginBottom: 28 }}>Ota-ona nazorat paneli</p>

      {/* Qurilma ulash */}
      <section style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24, marginBottom: 20 }}>
        <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>Qurilma ulash</h2>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <input
            placeholder="Bolaning ismi"
            value={name}
            onChange={e => setName(e.target.value)}
            style={{ padding: '10px 14px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, flex: 1, minWidth: 140 }}
          />
          <input
            placeholder="Pairing code (masalan: A1B2C3)"
            value={pairCode}
            onChange={e => setPairCode(e.target.value.toUpperCase())}
            style={{ padding: '10px 14px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, flex: 1, minWidth: 180 }}
          />
          <button
            onClick={connect}
            disabled={busy || !pairCode.trim()}
            style={{ padding: '10px 24px', background: busy ? '#9ca3af' : '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: busy ? 'not-allowed' : 'pointer', fontWeight: 600 }}
          >
            {busy ? 'Ulanmoqda...' : 'Ulash'}
          </button>
        </div>
      </section>

      {/* Qurilmalar */}
      {devices.length > 0 && (
        <section style={{ display: 'grid', gap: 16, marginBottom: 20 }}>
          {devices.map(d => (
            <article key={d._id} style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <span style={{ width: 10, height: 10, borderRadius: '50%', background: d.online ? '#10b981' : '#9ca3af', display: 'inline-block' }}/>
                <h3 style={{ fontSize: 18, fontWeight: 600, margin: 0 }}>{d.name}</h3>
                <span style={{ fontSize: 12, color: '#9ca3af' }}>{d.online ? 'online' : 'offline'}</span>
              </div>
              <p style={{ fontSize: 12, color: '#9ca3af', marginBottom: 16 }}>ID: {d.deviceId}</p>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {['SCREENSHOT', 'CAMERA_FRONT', 'CAMERA_BACK', 'SCREEN_SHARE'].map(t => (
                  <button
                    key={t}
                    onClick={() => sendRequest(d.deviceId, t)}
                    style={{ padding: '8px 16px', background: '#f3f4f6', border: '1px solid #e5e7eb', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 500 }}
                  >
                    {t === 'SCREENSHOT' ? '📸 Screenshot' :
                     t === 'CAMERA_FRONT' ? '🤳 Oldingi kamera' :
                     t === 'CAMERA_BACK' ? '📷 Orqa kamera' : '🖥 Screen share'}
                  </button>
                ))}
              </div>
            </article>
          ))}
        </section>
      )}

      {/* So'rovlar */}
      <section style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 16, padding: 24 }}>
        <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>So'rovlar tarixi</h2>
        {requests.length === 0 && <p style={{ color: '#9ca3af', fontSize: 14 }}>Hali so'rov yo'q</p>}
        {requests.map(r => (
          <div key={r._id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0', borderBottom: '1px solid #f3f4f6' }}>
            <span style={{ fontSize: 13, fontWeight: 500, minWidth: 130 }}>{r.type}</span>
            <span style={{ fontSize: 12, fontWeight: 700, color: statusColor[r.status] ?? '#6b7280' }}>{r.status}</span>
            <span style={{ fontSize: 11, color: '#9ca3af', flex: 1 }}>{new Date(r.createdAt).toLocaleString()}</span>
            {r.resultUrl && r.status === 'DONE' && (
              <button
                onClick={() => setPreview(r.resultUrl!)}
                style={{ padding: '4px 12px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 12 }}
              >
                Ko'rish
              </button>
            )}
          </div>
        ))}
      </section>

      {/* Rasm preview modal */}
      {preview && (
        <div
          onClick={() => setPreview(null)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, cursor: 'pointer' }}
        >
          <div onClick={e => e.stopPropagation()} style={{ position: 'relative', maxWidth: '90vw', maxHeight: '90vh' }}>
            <img src={preview} alt="natija" style={{ maxWidth: '90vw', maxHeight: '85vh', borderRadius: 12, display: 'block' }}/>
            <button
              onClick={() => setPreview(null)}
              style={{ position: 'absolute', top: -16, right: -16, width: 32, height: 32, borderRadius: '50%', background: '#fff', border: 'none', cursor: 'pointer', fontSize: 18, fontWeight: 700 }}
            >×</button>
          </div>
        </div>
      )}
    </main>
  );
}
