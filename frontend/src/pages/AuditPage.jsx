import React,{useMemo,useState} from 'react'
import { Download,RefreshCw } from 'lucide-react'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import { useAuth } from '../contexts/AuthContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import { fmtDate } from '../utils/format'

const csvCell=value=>`"${String(value??'').replaceAll('"','""')}"`

export default function AuditPage(){
  const {t,lang}=useI18n();const {has}=useAuth()
  const {data=[],loading,reload}=useApiData('/api/audit',{initial:[],poll:30000})
  const [filters,setFilters]=useState({actor:'',entityType:'ALL',action:'',from:'',to:''})
  const entityTypes=useMemo(()=>[...new Set(data.map(x=>x.entityType).filter(Boolean))].sort(),[data])
  const rows=useMemo(()=>data.filter(x=>{
    const day=String(x.createdAt||'').slice(0,10)
    return (!filters.actor||String(x.actor||'').toLowerCase().includes(filters.actor.toLowerCase()))
      &&(filters.entityType==='ALL'||x.entityType===filters.entityType)
      &&(!filters.action||String(x.action||'').toLowerCase().includes(filters.action.toLowerCase()))
      &&(!filters.from||day>=filters.from)&&(!filters.to||day<=filters.to)
  }),[data,filters])
  const exportCsv=()=>{
    const headers=[t('time'),t('entity'),'ID',t('operation'),t('message'),t('actor'),t('sourceIp'),t('userAgent')]
    const lines=[headers,...rows.map(r=>[r.createdAt,r.entityType,r.entityId,r.action,lang==='zh'?(r.messageZh||r.messageEn):(r.messageEn||r.messageZh),r.actor,r.sourceIp,r.userAgent])]
    const blob=new Blob(['\ufeff'+lines.map(line=>line.map(csvCell).join(',')).join('\n')],{type:'text/csv;charset=utf-8'})
    const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`gazellio-audit-${new Date().toISOString().slice(0,10)}.csv`;a.click();URL.revokeObjectURL(url)
  }
  const cols=[
    {key:'createdAt',label:t('time'),render:r=>fmtDate(r.createdAt,lang)},
    {key:'entityType',label:t('entity')},
    {key:'entityId',label:'ID',render:r=><span className="mono">{r.entityId}</span>},
    {key:'action',label:t('operation')},
    {key:'message',label:t('message'),render:r=><span>{lang==='zh'?(r.messageZh||r.messageEn):(r.messageEn||r.messageZh)}</span>},
    {key:'actor',label:t('actor')},
    {key:'sourceIp',label:t('sourceIp'),render:r=><span className="mono">{r.sourceIp||'—'}</span>},
    {key:'userAgent',label:t('userAgent'),render:r=><span className="audit-ua" title={r.userAgent}>{r.userAgent||'—'}</span>}
  ]
  return <>
    <PageHeader title={t('audit')}>
      <button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button>
      {has('AUDIT_EXPORT')&&<button className="btn primary" disabled={!rows.length} onClick={exportCsv}><Download size={15}/>{t('exportCsv')}</button>}
    </PageHeader>
    <div className="toolbar audit-filters">
      <input value={filters.actor} onChange={e=>setFilters({...filters,actor:e.target.value})} placeholder={t('filterActor')}/>
      <select value={filters.entityType} onChange={e=>setFilters({...filters,entityType:e.target.value})}><option value="ALL">{t('all')} · {t('entity')}</option>{entityTypes.map(x=><option key={x}>{x}</option>)}</select>
      <input value={filters.action} onChange={e=>setFilters({...filters,action:e.target.value})} placeholder={t('filterAction')}/>
      <label className="date-filter"><span>{t('from')}</span><input type="date" value={filters.from} onChange={e=>setFilters({...filters,from:e.target.value})}/></label>
      <label className="date-filter"><span>{t('to')}</span><input type="date" value={filters.to} onChange={e=>setFilters({...filters,to:e.target.value})}/></label>
      <span className="toolbar-count">{rows.length}</span>
    </div>
    <DataTable columns={cols} rows={rows} empty={loading?t('loading'):t('noData')}/>
  </>
}
