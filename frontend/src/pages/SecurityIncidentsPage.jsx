import React,{useMemo,useState} from 'react'
import { RefreshCw } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {envLabel,incidentStatus,severityLabel,fmtDate} from '../utils/format'

export default function SecurityIncidentsPage(){
 const {t,pick,lang}=useI18n();const nav=useNavigate();const {data=[],loading,reload}=useApiData('/api/work-orders/incidents',{initial:[],poll:15000});const [q,setQ]=useState(''),[status,setStatus]=useState('ALL'),[priority,setPriority]=useState('ALL')
 const rows=useMemo(()=>data.filter(x=>(status==='ALL'||x.status===status)&&(priority==='ALL'||x.priority===priority)&&(!q||(`${x.incidentNo} ${x.cveId} ${x.assetName} ${x.ownerName} ${pick(x)}`).toLowerCase().includes(q.toLowerCase()))),[data,q,status,priority,pick])
 const columns=[
  {key:'priority',label:t('priority'),render:r=><StatusBadge tone={r.priority==='P1'?'critical':r.priority==='P2'?'high':r.priority==='P3'?'medium':'low'}>{r.priority}</StatusBadge>},
  {key:'incidentNo',label:t('incidentNo'),render:r=><span className="mono linkish">{r.incidentNo}</span>},
  {key:'cveId',label:t('vulnerability'),render:r=><div className="cell-main"><b className="mono">{r.cveId}</b><small>{pick(r)}</small></div>},
  {key:'assetName',label:t('asset'),render:r=><div className="cell-main"><b>{r.assetName}</b><small>{r.assetCode} · {envLabel(t,r.environment)}</small></div>},
  {key:'severity',label:t('severity'),render:r=><StatusBadge tone={severityTone(r.severity)}>{severityLabel(t,r.severity)}</StatusBadge>},
  {key:'ownerName',label:t('owner')},{key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{incidentStatus(t,r.status)}</StatusBadge>},
  {key:'dueAt',label:t('due'),render:r=>fmtDate(r.dueAt,lang)}
 ]
 return <><PageHeader title={t('securityIncidents')}><button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button></PageHeader><div className="toolbar"><input className="search-input" value={q} onChange={e=>setQ(e.target.value)} placeholder={t('searchIncidents')}/><select value={priority} onChange={e=>setPriority(e.target.value)}><option value="ALL">{t('all')} · {t('priority')}</option>{['P1','P2','P3','P4'].map(p=><option key={p}>{p}</option>)}</select><select value={status} onChange={e=>setStatus(e.target.value)}><option value="ALL">{t('all')} · {t('status')}</option>{['OPEN','ASSIGNED','IN_REMEDIATION','PENDING_CHANGE','IMPLEMENTING','CLOSED','EXEMPTED','FALSE_POSITIVE'].map(s=><option key={s} value={s}>{incidentStatus(t,s)}</option>)}</select><span className="toolbar-count">{rows.length}</span></div><DataTable columns={columns} rows={rows} onRowClick={r=>nav(`/work-orders/incidents/${r.id}`)} empty={loading?t('loading'):t('noData')}/></>
}
