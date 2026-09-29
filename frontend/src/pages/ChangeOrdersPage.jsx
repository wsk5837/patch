import React,{useMemo,useState} from 'react'
import { RefreshCw } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {changeStatus,fmtDate} from '../utils/format'

export default function ChangeOrdersPage(){
 const {t,lang,localize}=useI18n();const nav=useNavigate();const {data=[],loading,reload}=useApiData('/api/work-orders/changes',{initial:[],poll:15000});const [q,setQ]=useState(''),[status,setStatus]=useState('ALL')
 const rows=useMemo(()=>data.filter(x=>(status==='ALL'||x.status===status)&&(!q||(`${x.changeNo} ${x.incidentNo} ${x.cveId} ${x.assetName} ${x.summary}`).toLowerCase().includes(q.toLowerCase()))),[data,q,status])
 const columns=[{key:'changeNo',label:t('changeNo'),render:r=><span className="mono linkish">{r.changeNo}</span>},{key:'summary',label:t('summary'),render:r=><div className="cell-main"><b>{localize(r.summary)}</b><small>{r.incidentNo} · {r.cveId}</small></div>},{key:'changeType',label:t('changeType'),render:r=><StatusBadge tone={r.changeType==='EMERGENCY'?'critical':r.changeType==='MAJOR'?'high':'purple'}>{t(r.changeType)}</StatusBadge>},{key:'assetName',label:t('asset'),render:r=>localize(r.assetName)},{key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{changeStatus(t,r.status)}</StatusBadge>},{key:'maintenanceStart',label:t('maintenanceWindow'),render:r=>fmtDate(r.maintenanceStart,lang)},{key:'updatedAt',label:t('updatedAt'),render:r=>fmtDate(r.updatedAt,lang)}]
 return <><PageHeader title={t('changeOrders')}><button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button></PageHeader><div className="toolbar"><input className="search-input" value={q} onChange={e=>setQ(e.target.value)} placeholder={t('searchChanges')}/><select value={status} onChange={e=>setStatus(e.target.value)}><option value="ALL">{t('all')} · {t('status')}</option>{['DRAFT','PENDING_APPROVAL','APPROVED','IMPLEMENTING','VALIDATING','CLOSED','REJECTED','CANCELLED'].map(s=><option key={s} value={s}>{changeStatus(t,s)}</option>)}</select><span className="toolbar-count">{rows.length}</span></div><DataTable columns={columns} rows={rows} onRowClick={r=>nav(`/work-orders/changes/${r.id}`)} empty={loading?t('loading'):t('noData')}/></>
}
