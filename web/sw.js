const C='stundenplan-v3',A=['./','index.html','icon.png','favicon.png'];
const key=r=>{const u=new URL(r.url);u.search='';return u.href};
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(A)).catch(()=>{}));self.skipWaiting()});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C).map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{const r=e.request;if(r.method!=='GET'||new URL(r.url).origin!==location.origin)return;
    e.respondWith(fetch(r).then(res=>{if(res.ok){const cp=res.clone();caches.open(C).then(c=>c.put(key(r),cp))}return res}).catch(()=>caches.match(key(r))))});