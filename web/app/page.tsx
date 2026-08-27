'use client';
import { useEffect, useState } from 'react';

type Device={_id:string;name:string;deviceId:string;pairingCode:string;online:boolean;lastSeen:string};
type Req={_id:string;deviceId:string;type:string;status:string;createdAt:string;resultUrl?:string};
export default function Home(){
 const [devices,setDevices]=useState<Device[]>([]); const [requests,setRequests]=useState<Req[]>([]); const [pair,setPair]=useState(''); const [name,setName]=useState('My child'); const [busy,setBusy]=useState(false);
 async function refresh(){const d=await fetch('/api/device/register?mode=list').then(r=>r.json());setDevices(d.devices??[]); const x=await fetch('/api/request?mode=list').then(r=>r.json());setRequests(x.requests??[])}
 useEffect(()=>{refresh();const t=setInterval(refresh,5000);return()=>clearInterval(t)},[])
 async function connect(){setBusy(true);await fetch('/api/device/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({pairingCode:pair,name})});setBusy(false);refresh()}
 async function request(deviceId:string,type:string){await fetch('/api/request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({deviceId,type})});refresh()}
 return <main style={{maxWidth:1100,margin:'40px auto',padding:24}}>
  <h1>Family Guard</h1><p>Ota-ona paneli</p>
  <section style={{background:'#fff',padding:20,borderRadius:16,marginBottom:20}}><h2>Qurilma ulash</h2><input placeholder="Child name" value={name} onChange={e=>setName(e.target.value)} style={{padding:10,marginRight:8}}/><input placeholder="Pairing code" value={pair} onChange={e=>setPair(e.target.value)} style={{padding:10,marginRight:8}}/><button onClick={connect} disabled={busy}>Ulash</button></section>
  <section style={{display:'grid',gap:16}}>{devices.map(d=><article key={d._id} style={{background:'#fff',padding:20,borderRadius:16}}><h3>{d.name}</h3><p>ID: {d.deviceId} · {d.online?'🟢 online':'⚪ offline'}</p><div style={{display:'flex',gap:8,flexWrap:'wrap'}}>{['SCREENSHOT','CAMERA_FRONT','CAMERA_BACK','SCREEN_SHARE'].map(t=><button key={t} onClick={()=>request(d.deviceId,t)}>{t}</button>)}</div></article>)}</section>
  <section style={{background:'#fff',padding:20,borderRadius:16,marginTop:20}}><h2>So‘rovlar</h2>{requests.map(r=><div key={r._id} style={{padding:'8px 0',borderBottom:'1px solid #eee'}}>{r.type} — <b>{r.status}</b>{r.resultUrl&&<a href={r.resultUrl} target="_blank"> Ko‘rish</a>}</div>)}</section>
 </main>
}
