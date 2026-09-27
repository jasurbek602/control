import React from 'react';
export default function RootLayout({children}:{children:React.ReactNode}){
  return <html lang="uz"><body style={{fontFamily:'Arial, sans-serif',margin:0,background:'#f5f7fb'}}>{children}</body></html>
}
