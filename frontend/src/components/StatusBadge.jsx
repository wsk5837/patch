import React from 'react'
export default function StatusBadge({tone='gray',children}){return <span className={`badge badge-${tone}`}>{children}</span>}
export function severityTone(v){return ({CRITICAL:'critical',HIGH:'high',MEDIUM:'medium',LOW:'low'})[v]||'gray'}
export function statusTone(v){
  if(['SUCCEEDED','COMPLETED','APPROVED','CLOSED','RESOLVED','ONLINE'].includes(v))return 'ok'
  if(['RUNNING','IN_PROGRESS','IMPLEMENTING','PENDING','CONFIRMED','IN_REMEDIATION'].includes(v))return 'purple'
  if(['FAILED','REJECTED','BLOCKED','OFFLINE'].includes(v))return 'red'
  if(['PAUSED','WAITING','QUEUED','REOPENED','EXEMPTED'].includes(v))return 'warn'
  return 'gray'
}
