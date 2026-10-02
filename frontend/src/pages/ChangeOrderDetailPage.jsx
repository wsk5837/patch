import React,{useEffect,useState} from 'react'
import { ArrowLeft, ExternalLink, RotateCcw } from 'lucide-react'
import { useNavigate,useParams } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { api } from '../api/client'
import { useI18n } from '../contexts/I18nContext'
import { useToast } from '../components/ToastContext'
import { useAuth } from '../contexts/AuthContext'
import Modal from '../components/Modal'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {changeStatus,fmtDate} from '../utils/format'
import WorkOrderInsightTabs from '../components/WorkOrderInsightTabs'

const toLocalInput=value=>{
 if(!value)return ''
 const d=new Date(value)
 if(Number.isNaN(d.getTime()))return ''
 return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16)
}

export default function ChangeOrderDetailPage(){
 const {id}=useParams();const nav=useNavigate();const {t,lang,localize}=useI18n();const toast=useToast();const {has}=useAuth()
 const {data:x,loading,reload}=useApiData(`/api/work-orders/changes/${id}`,{poll:10000})
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false)
 const [form,setForm]=useState({changeType:'NORMAL',summary:'',riskAssessment:'',implementationPlan:'',rollbackPlan:'',maintenanceStart:'',maintenanceEnd:''})
 useEffect(()=>{if(x)setForm({changeType:x.changeType||'NORMAL',summary:x.summary||'',riskAssessment:x.riskAssessment||'',implementationPlan:x.implementationPlan||'',rollbackPlan:x.rollbackPlan||'',maintenanceStart:toLocalInput(x.maintenanceStart),maintenanceEnd:toLocalInput(x.maintenanceEnd)})},[x?.id,x?.updatedAt])
 if(loading&&!x)return <div className="loading">{t('loading')}</div>;if(!x)return null
 const resubmit=async()=>{setBusy(true);try{await api(`/api/work-orders/changes/${id}/resubmit`,{method:'POST',body:form});setOpen(false);await reload();toast.push(t('changeResubmitted'))}catch(e){toast.push(e.message||t('operationFailed'),'red')}finally{setBusy(false)}}
 const approvalIsNext=x.status==='PENDING_APPROVAL'&&!!x.approvalId
 const runIsNext=['APPROVED','IMPLEMENTING','VALIDATING'].includes(x.status)&&!!x.latestRunId
 return <>
  <div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions" role="group" aria-label={t('availableActions')}>{x.status==='REJECTED'&&(has('CHANGE_RESUBMIT')||has('CHANGE_MANAGE'))&&<button className="btn primary next-action" onClick={()=>setOpen(true)}><RotateCcw size={15}/>{t('reviseAndResubmit')}</button>}<button className="btn" onClick={()=>nav(`/work-orders/incidents/${x.incidentId}`)}><ExternalLink size={15}/>{t('securityIncident')}</button>{x.remediationTaskId&&<button className="btn" onClick={()=>nav(`/tasks/${x.remediationTaskId}`)}><ExternalLink size={15}/>{t('remediationTask')}</button>}{x.approvalId&&<button className={approvalIsNext?'btn primary next-action':'btn'} onClick={()=>nav(`/approvals/${x.approvalId}`)}><ExternalLink size={15}/>{t('linkedApproval')}</button>}{x.latestRunId&&<button className={runIsNext?'btn primary next-action':'btn'} onClick={()=>nav(`/automation/runs/${x.latestRunId}`)}><ExternalLink size={15}/>{t('linkedRun')}</button>}</div></div>
  <section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={x.changeType==='EMERGENCY'?'critical':x.changeType==='MAJOR'?'high':'purple'}>{t(x.changeType)}</StatusBadge><StatusBadge tone={statusTone(x.status)}>{changeStatus(t,x.status)}</StatusBadge></div><h1>{x.changeNo}</h1><h2>{localize(x.summary)}</h2></div></section>
  <WorkOrderInsightTabs record={x} overview={<div className="kv-grid three"><div><span>{t('triggerSource')}</span><b>{x.incidentNo}</b></div><div><span>{t('patch')}</span><b>{x.patchCode||'—'}</b></div><div><span>{t('approvalNo')}</span><b>{x.approvalNo||'—'}</b></div><div><span>{t('maintenanceStart')}</span><b>{fmtDate(x.maintenanceStart,lang)}</b></div><div><span>{t('maintenanceEnd')}</span><b>{fmtDate(x.maintenanceEnd,lang)}</b></div></div>}/>
  <div className="detail-grid"><section className="panel span-2"><div className="kv-grid three"><button className="kv-link" onClick={()=>nav(`/work-orders/incidents/${x.incidentId}`)}><span>{t('triggerSource')}</span><b>{x.incidentNo} <ExternalLink size={11}/></b></button><div><span>{t('cve')}</span><b>{x.cveId}</b></div><div><span>{t('asset')}</span><b>{localize(x.assetName)}</b></div><div><span>{t('businessService')}</span><b>{localize(x.businessService)}</b></div><div><span>{t('patch')}</span><b>{x.patchCode||'—'}</b></div><div><span>{t('approvalNo')}</span><b>{x.approvalNo||'—'}</b></div><div><span>{t('maintenanceStart')}</span><b>{fmtDate(x.maintenanceStart,lang)}</b></div><div><span>{t('maintenanceEnd')}</span><b>{fmtDate(x.maintenanceEnd,lang)}</b></div><div><span>{t('syncStatus')}</span><b>{x.syncStatus}</b></div></div></section>{[['riskAssessment',x.riskAssessment],['implementationPlan',x.implementationPlan],['rollbackPlan',x.rollbackPlan]].map(([key,value])=><section className="panel" key={key}><div className="panel-head"><h2>{t(key)}</h2></div><div className="panel-body"><p className="text-block">{localize(value)||'—'}</p></div></section>)}</div>
  <Modal open={open} title={t('reviseAndResubmit')} size="lg" onClose={()=>setOpen(false)} footer={<><button className="btn" onClick={()=>setOpen(false)}>{t('cancel')}</button><button className="btn primary" disabled={!form.summary.trim()||!form.riskAssessment.trim()||!form.implementationPlan.trim()||!form.rollbackPlan.trim()||!form.maintenanceStart||!form.maintenanceEnd||busy} onClick={resubmit}>{t('resubmitApproval')}</button></>}><div className="form-grid"><label className="form-field"><span>{t('changeType')}</span><select value={form.changeType} onChange={e=>setForm({...form,changeType:e.target.value})}>{['EMERGENCY','MAJOR','NORMAL','STANDARD'].map(v=><option key={v} value={v}>{t(v)}</option>)}</select></label><label className="form-field full"><span>{t('summary')} *</span><input value={form.summary} onChange={e=>setForm({...form,summary:e.target.value})}/></label><label className="form-field"><span>{t('maintenanceStart')} *</span><input type="datetime-local" value={form.maintenanceStart} onChange={e=>setForm({...form,maintenanceStart:e.target.value})}/></label><label className="form-field"><span>{t('maintenanceEnd')} *</span><input type="datetime-local" value={form.maintenanceEnd} onChange={e=>setForm({...form,maintenanceEnd:e.target.value})}/></label><label className="form-field full"><span>{t('riskAssessment')} *</span><textarea rows={3} value={form.riskAssessment} onChange={e=>setForm({...form,riskAssessment:e.target.value})}/></label><label className="form-field full"><span>{t('implementationPlan')} *</span><textarea rows={3} value={form.implementationPlan} onChange={e=>setForm({...form,implementationPlan:e.target.value})}/></label><label className="form-field full"><span>{t('rollbackPlan')} *</span><textarea rows={3} value={form.rollbackPlan} onChange={e=>setForm({...form,rollbackPlan:e.target.value})}/></label></div></Modal>
 </>
}
