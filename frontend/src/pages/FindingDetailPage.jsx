import React,{useMemo,useState} from 'react'
import { ArrowLeft, CheckCircle2, Ban, ShieldCheck, ExternalLink } from 'lucide-react'
import { useNavigate,useParams } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { api } from '../api/client'
import { useI18n } from '../contexts/I18nContext'
import { useToast } from '../components/ToastContext'
import Modal from '../components/Modal'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {envLabel,findingStatus,severityLabel,fmtDate} from '../utils/format'

function dateAfter(days){const d=new Date();d.setDate(d.getDate()+days);return d.toISOString().slice(0,10)}

export default function FindingDetailPage(){
 const {id}=useParams();const nav=useNavigate();const {t,pick,lang}=useI18n();const toast=useToast()
 const {data:f,loading,reload}=useApiData(`/api/vulnerabilities/findings/${id}`)
 const [fp,setFp]=useState(false),[exempt,setExempt]=useState(false),[reason,setReason]=useState(''),[expiresAt,setExpiresAt]=useState(()=>dateAfter(30)),[busy,setBusy]=useState(false)
 const closed=useMemo(()=>f&&['RESOLVED','FALSE_POSITIVE','EXEMPTED','IN_REMEDIATION'].includes(f.status),[f])
 if(loading&&!f)return <div className="loading">{t('loading')}</div>;if(!f)return null
 const confirm=async()=>{setBusy(true);try{await api(`/api/vulnerabilities/findings/${id}/confirm`,{method:'POST',body:{}});await reload();toast.push(t('operationSuccess'))}catch(e){toast.push(t('operationFailed'),'red')}finally{setBusy(false)}}
 const falsePositive=async()=>{setBusy(true);try{await api(`/api/vulnerabilities/findings/${id}/false-positive`,{method:'POST',body:{reason}});setFp(false);setReason('');await reload();toast.push(t('operationSuccess'))}catch(e){toast.push(t('operationFailed'),'red')}finally{setBusy(false)}}
 const submitExemption=async()=>{setBusy(true);try{await api(`/api/vulnerabilities/findings/${id}/exempt`,{method:'POST',body:{reason,expiresAt}});setExempt(false);setReason('');await reload();toast.push(t('operationSuccess'))}catch(e){toast.push(t('operationFailed'),'red')}finally{setBusy(false)}}
 return <>
  <div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions">
   {f.securityIncidentId&&<button className="btn" onClick={()=>nav(`/work-orders/incidents/${f.securityIncidentId}`)}><ExternalLink size={15}/>{t('securityIncident')}</button>}{f.remediationTaskId&&<button className="btn" onClick={()=>nav(`/tasks/${f.remediationTaskId}`)}><ExternalLink size={15}/>{t('remediationTask')}</button>}
   {!closed&&<><button className="btn" onClick={()=>{setReason('');setFp(true)}}><Ban size={15}/>{t('markFalsePositive')}</button><button className="btn" onClick={()=>{setReason('');setExempt(true)}}><ShieldCheck size={15}/>{t('exemptFinding')}</button><button className="btn primary" onClick={confirm} disabled={busy}><CheckCircle2 size={15}/>{t('confirmFinding')}</button></>}
  </div></div>
  <section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={severityTone(f.severity)}>{severityLabel(t,f.severity)}</StatusBadge><StatusBadge tone={statusTone(f.status)}>{findingStatus(t,f.status)}</StatusBadge>{f.kev&&<StatusBadge tone="critical">CISA KEV</StatusBadge>}</div><h1>{f.cveId}</h1><h2>{pick(f)}</h2></div><div className="score-box"><b>{f.riskScore}</b><span>{t('riskScore')}</span></div></section>
  <div className="detail-grid"><section className="panel"><div className="kv-grid"><div><span>{t('asset')}</span><b>{f.assetName}</b></div><div><span>{t('assetCode')}</span><b>{f.assetCode}</b></div><div><span>{t('environment')}</span><b>{envLabel(t,f.environment)}</b></div><div><span>{t('businessService')}</span><b>{f.businessService}</b></div><div><span>{t('owner')}</span><b>{f.ownerName||'—'}</b></div><div><span>{t('scanSource')}</span><b>{f.scanJobNo||'—'}</b></div><div><span>{t('firstSeen')}</span><b>{fmtDate(f.firstSeenAt,lang)}</b></div><div><span>{t('lastSeen')}</span><b>{fmtDate(f.lastSeenAt,lang)}</b></div>{f.status==='EXEMPTED'&&<><div><span>{t('exemptionExpires')}</span><b>{fmtDate(f.exemptionExpiresAt,lang)}</b></div><div><span>{t('exemptionReason')}</span><b>{f.exemptionReason||'—'}</b></div></>}</div></section>
   <section className="panel"><div className="panel-head"><h2>{t('patchAvailable')}</h2></div><div className="patch-candidate-grid compact">{f.patchCandidates?.length?f.patchCandidates.map(p=><button className="patch-candidate" key={p.id} onClick={()=>nav(`/patches/${p.id}`)}><div><b>{p.patchId}</b><span>{pick(p,'titleZh','titleEn')}</span></div><ExternalLink size={13}/></button>):<span className="muted panel-body">—</span>}</div></section>
   <section className="panel span-2"><div className="panel-head"><h2>{t('evidence')}</h2></div><pre className="evidence-box">{f.evidence||'—'}</pre></section></div>
  <Modal open={fp} title={t('confirmFalsePositive')} onClose={()=>setFp(false)} footer={<><button className="btn" onClick={()=>setFp(false)}>{t('cancel')}</button><button className="btn primary" disabled={!reason.trim()||busy} onClick={falsePositive}>{t('confirm')}</button></>}><label className="form-field"><span>{t('falsePositiveReason')}</span><textarea value={reason} onChange={e=>setReason(e.target.value)} rows={4}/></label></Modal>
  <Modal open={exempt} title={t('confirmExemption')} onClose={()=>setExempt(false)} footer={<><button className="btn" onClick={()=>setExempt(false)}>{t('cancel')}</button><button className="btn primary" disabled={!reason.trim()||!expiresAt||busy} onClick={submitExemption}>{t('confirm')}</button></>}><div className="form-grid"><label className="form-field full"><span>{t('exemptionReason')}</span><textarea value={reason} onChange={e=>setReason(e.target.value)} rows={4}/></label><label className="form-field"><span>{t('exemptionExpires')}</span><input type="date" value={expiresAt} min={new Date().toISOString().slice(0,10)} onChange={e=>setExpiresAt(e.target.value)}/></label></div></Modal>
 </>
}
