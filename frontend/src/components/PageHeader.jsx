import React from 'react'
export default function PageHeader({title,children}){return <div className="page-head"><h1>{title}</h1><div className="page-actions">{children}</div></div>}
