import React from 'react'
export default function PageHeader({title,subtitle,children}){return <div className="page-head"><div className="page-title"><h1>{title}</h1>{subtitle&&<p>{subtitle}</p>}</div><div className="page-actions">{children}</div></div>}
