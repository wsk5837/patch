import React,{useMemo,useState} from 'react'
import { useLocation,useNavigate } from 'react-router-dom'
import { CheckCircle2,Clock3,RefreshCw,ShieldOff } from 'lucide-react'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import { useToast } from '../components/ToastContext'
import { api } from '../api/client'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {envLabel,findingStatus,severityLabel} from '../utils/format'

export default function FindingsPage(){
  const {t,pick,localize}=useI18n(),toast=useToast(),nav=useNavigate(),loc=useLocation()
  const initialQ=new URLSearchParams(loc.search).get('q')||'',initialSev=new URLSearchParams(loc.search).get('severity')||'ALL'
  const {data=[],loading,reload}=useApiData('/api/vulnerabilities/findings',{initial:[],poll:30000})
  const [q,setQ]=useState(initialQ),[status,setStatus]=useState('ALL'),[sev,setSev]=useState(initialSev)
  const [selected,setSelected]=useState(new Set()),[dialog,setDialog]=useState(null),[reason,setReason]=useState(''),[expiresAt,setExpiresAt]=useState(''),[compensatingControl,setCompensatingControl]=useState(''),[residualRisk,setResidualRisk]=useState(''),[busy,setBusy]=useState(false)
  const rows=useMemo(()=>data.filter(f=>(!q||(`${f.cveId} ${f.assetName} ${f.assetCode} ${f.businessService} ${pick(f)}`).toLowerCase().includes(q.toLowerCase()))&&(status==='ALL'||f.status===status)&&(sev==='ALL'||f.severity===sev)),[data,q,status,sev,pick])
  const allSelected=rows.length>0&&rows.every(x=>selected.has(x.id))
  const toggle=id=>setSelected(current=>{const next=new Set(current);next.has(id)?next.delete(id):next.add(id);return next})
  const toggleAll=()=>setSelected(current=>{const next=new Set(current);allSelected?rows.forEach(x=>next.delete(x.id)):rows.forEach(x=>next.add(x.id));return next})
  const openBulk=action=>{setDialog(action);setReason('');setExpiresAt('');setCompensatingControl('');setResidualRisk('')}
  const submitBulk=async()=>{
    if(!selected.size)return
    if(dialog!=='CONFIRM'&&!reason.trim())return toast.push(t('reasonRequired'),'red')
    setBusy(true)
    try{
      const result=await api('/api/vulnerabilities/findings/bulk',{method:'POST',body:{findingIds:[...selected],action:dialog,reason:reason.trim(),expiresAt,compensatingControl,residualRisk}})
      toast.push(t('bulkResult',result.succeeded,result.failed),result.failed?'high':'ok');setDialog(null);setSelected(new Set());await reload()
    }catch(error){toast.push(error.message||t('operationFailed'),'red')}finally{setBusy(false)}
  }
  const cols=[
    {key:'select',width:44,label:<input type="checkbox" checked={allSelected} onChange={toggleAll} aria-label={t('selectAll')}/>,render:r=><input type="checkbox" checked={selected.has(r.id)} onChange={()=>toggle(r.id)} onClick={e=>e.stopPropagation()} aria-label={t('select')}/>},
    {key:'cveId',label:t('cve'),render:r=><div className="cell-main"><b className="mono">{r.cveId}</b><small>{pick(r)}</small></div>},
    {key:'severity',label:t('severity'),render:r=><StatusBadge tone={severityTone(r.severity)}>{severityLabel(t,r.severity)}</StatusBadge>},
    {key:'asset',label:t('asset'),render:r=><div className="cell-main"><b>{localize(r.assetName)}</b><small>{r.assetCode} · {envLabel(t,r.environment)}</small></div>},
    {key:'businessService',label:t('businessService'),render:r=>localize(r.businessService)},
    {key:'ownerName',label:t('owner'),render:r=>localize(r.ownerName)},
    {key:'riskScore',label:t('riskScore')},
    {key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{findingStatus(t,r.status)}</StatusBadge>},
    {key:'scanJobNo',label:t('scanSource')}
  ]
  return <>
    <PageHeader title={t('findings')}><button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button></PageHeader>
    <div className="toolbar">
      <input className="search-input" value={q} onChange={e=>setQ(e.target.value)} placeholder={t('globalSearch')}/>
      <select value={sev} onChange={e=>setSev(e.target.value)}><option value="ALL">{t('all')} · {t('severity')}</option><option value="CRITICAL">{t('critical')}</option><option value="HIGH">{t('high')}</option><option value="MEDIUM">{t('medium')}</option><option value="LOW">{t('low')}</option></select>
      <select value={status} onChange={e=>setStatus(e.target.value)}><option value="ALL">{t('all')} · {t('status')}</option>{['NEW','CONFIRMED','IN_REMEDIATION','REOPENED','EXEMPTED','RESOLVED','FALSE_POSITIVE'].map(s=><option value={s} key={s}>{findingStatus(t,s)}</option>)}</select>
      <span className="toolbar-count">{rows.length}</span>
    </div>
    {selected.size>0&&<div className="bulk-action-bar"><b>{t('selectedCount',selected.size)}</b><button className="btn primary" onClick={()=>openBulk('CONFIRM')}><CheckCircle2 size={15}/>{t('bulkConfirm')}</button><button className="btn" onClick={()=>openBulk('FALSE_POSITIVE')}><ShieldOff size={15}/>{t('bulkFalsePositive')}</button><button className="btn" onClick={()=>openBulk('EXEMPT')}><Clock3 size={15}/>{t('bulkExempt')}</button><button className="btn text" onClick={()=>setSelected(new Set())}>{t('cancel')}</button></div>}
    <DataTable columns={cols} rows={rows} onRowClick={r=>nav(`/vulnerabilities/findings/${r.id}`)} empty={loading?t('loading'):t('noData')}/>
    <Modal open={Boolean(dialog)} title={dialog==='CONFIRM'?t('bulkConfirm'):dialog==='FALSE_POSITIVE'?t('bulkFalsePositive'):t('bulkExempt')} onClose={()=>!busy&&setDialog(null)} footer={<><button className="btn" onClick={()=>setDialog(null)}>{t('cancel')}</button><button className="btn primary" disabled={busy||(dialog!=='CONFIRM'&&!reason.trim())||(dialog==='EXEMPT'&&(!compensatingControl.trim()||!residualRisk.trim()||!expiresAt))} onClick={submitBulk}>{busy?t('processing'):t('confirm')}</button></>}>
      <div className="form-grid"><div className="selection-summary">{t('selectedCount',selected.size)}</div>{dialog!=='CONFIRM'&&<label className="form-field span-2"><span>{t('reason')} *</span><textarea value={reason} onChange={e=>setReason(e.target.value)} rows={4}/></label>}{dialog==='EXEMPT'&&<><label className="form-field span-2"><span>{t('compensatingControl')} *</span><textarea value={compensatingControl} onChange={e=>setCompensatingControl(e.target.value)} rows={3}/></label><label className="form-field span-2"><span>{t('residualRisk')} *</span><textarea value={residualRisk} onChange={e=>setResidualRisk(e.target.value)} rows={3}/></label><label className="form-field"><span>{t('expiresAt')}</span><input type="date" value={expiresAt} onChange={e=>setExpiresAt(e.target.value)}/></label></>}</div>
    </Modal>
  </>
}
