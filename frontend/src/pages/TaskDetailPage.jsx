import React,{useEffect,useState} from 'react'
import { ArrowLeft, Play, CheckCircle2, RefreshCw, ExternalLink, UserRoundCog, ScanSearch, UserCheck } from 'lucide-react'
import { useNavigate,useParams } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { api } from '../api/client'
import { useI18n } from '../contexts/I18nContext'
import { useToast } from '../components/ToastContext'
import Modal from '../components/Modal'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {envLabel,taskStage,taskStatus,fmtDate} from '../utils/format'

export default function TaskDetailPage(){
 const {id}=useParams()
 const nav=useNavigate()
 const {t,pick,lang,localize}=useI18n()
 const toast=useToast()
 const {data:task,loading,reload}=useApiData(`/api/tasks/${id}`,{poll:8000})
 const {data:assignees=[]}=useApiData('/api/users/assignees',{initial:[]})
 const [verify,setVerify]=useState(null)
 const [result,setResult]=useState('PASS')
 const [comment,setComment]=useState('')
 const [busy,setBusy]=useState(false)
 const [assignOpen,setAssignOpen]=useState(false)
 const [manualRetest,setManualRetest]=useState(false)
 const [ownerSelection,setOwnerSelection]=useState('')

 useEffect(()=>{if(task?.ownerId)setOwnerSelection(String(task.ownerId))},[task?.ownerId])
 if(loading&&!task)return <div className="loading">{t('loading')}</div>
 if(!task)return null

 const doAction=async(action,body={})=>{
  setBusy(true)
  try{await api(`/api/tasks/${id}/actions/${action}`,{method:'POST',body});await reload();toast.push(t('operationSuccess'));return true}
  catch(e){toast.push(e.message||t('operationFailed'),'red');return false}
  finally{setBusy(false)}
 }
 const verifySubmit=async()=>{
  const action=verify==='TEST'?'verify-test':verify==='PREPROD'?'verify-preprod':'verify-prod'
  if(await doAction(action,{result,comment})){setVerify(null);setComment('')}
 }
 const manualRetestSubmit=async()=>{
  if(await doAction('submit-manual-retest',{result,comment,retestMode:'MANUAL'})){setManualRetest(false);setComment('')}
 }
 const openManualRetest=()=>{setResult('PASS');setComment('');setManualRetest(true)}
 const submitAssign=async()=>{
  const u=assignees.find(x=>String(x.id)===String(ownerSelection));if(!u)return
  setBusy(true)
  try{await api(`/api/tasks/${id}/assign`,{method:'POST',body:{ownerId:u.id,ownerName:u.displayName}});setAssignOpen(false);await reload();toast.push(t('assignmentUpdated'))}
  catch(e){toast.push(e.message||t('operationFailed'),'red')}
  finally{setBusy(false)}
 }

 const patchRunActive=task.latestRunId&&task.status==='IN_PROGRESS'&&['TEST_PATCH','PREPROD_PATCH','PROD_PATCH'].includes(task.stage)
 const retestRunActive=task.latestRunId&&task.lastRetestMode==='AUTO'&&task.lastRetestResult==='RUNNING'
 const runIsNext=patchRunActive||retestRunActive
 const approvalIsNext=task.stage==='RELEASE_APPROVAL'&&!!task.approvalId

 const actions=[]
 if(task.stage!=='CLOSED')actions.push(<button key="assign" className="btn" onClick={()=>setAssignOpen(true)}><UserRoundCog size={15}/>{t('reassign')}</button>)
 if(task.assetId)actions.push(<button key="asset" className="btn" onClick={()=>nav(`/assets/${task.assetId}`)}><ExternalLink size={15}/>{t('viewAsset')}</button>)
 if(task.stage==='ASSIGNED')actions.push(<button key="start" className="btn primary" disabled={busy} onClick={()=>doAction('start-test')}><Play size={15}/>{t('startTestPatch')}</button>)
 if(task.stage==='APP_VERIFY')actions.push(<button key="vt" className="btn primary" onClick={()=>setVerify('TEST')}><CheckCircle2 size={15}/>{t('verifyTest')}</button>)
 if(task.stage==='PREPROD_VERIFY')actions.push(<button key="vp" className="btn primary" onClick={()=>setVerify('PREPROD')}><CheckCircle2 size={15}/>{t('verifyPreprod')}</button>)
 if(task.stage==='PROD_VERIFY')actions.push(<button key="vprod" className="btn primary" onClick={()=>setVerify('PROD')}><CheckCircle2 size={15}/>{t('verifyProd')}</button>)
 if(['TEST_RESCAN','PREPROD_RESCAN','PROD_RESCAN'].includes(task.stage)){
  const running=task.lastRetestMode==='AUTO'&&task.lastRetestResult==='RUNNING'
  actions.push(<button key="auto-retest" className="btn primary" disabled={busy||running} onClick={()=>doAction('start-auto-retest',{retestMode:'AUTO'})}><ScanSearch size={15}/>{running?t('retestRunning'):t('automaticRetest')}</button>)
  actions.push(<button key="manual-retest" className="btn" disabled={busy||running} onClick={openManualRetest}><UserCheck size={15}/>{t('manualRetest')}</button>)
 }
 if(task.status==='BLOCKED'&&['TEST_PATCH','PREPROD_PATCH','PROD_PATCH'].includes(task.stage))actions.push(<button key="retry" className="btn primary" onClick={()=>doAction('retry')}><RefreshCw size={15}/>{t('retry')}</button>)
 if(task.securityIncidentId)actions.push(<button key="incident" className={task.stage==='RELEASE_APPROVAL'&&!task.changeOrderId?'btn primary':'btn'} onClick={()=>nav(`/work-orders/incidents/${task.securityIncidentId}`)}><ExternalLink size={15}/>{t(task.stage==='RELEASE_APPROVAL'&&!task.changeOrderId?'createChange':'securityIncident')}</button>)
 if(task.changeOrderId)actions.push(<button key="change" className="btn" onClick={()=>nav(`/work-orders/changes/${task.changeOrderId}`)}><ExternalLink size={15}/>{t('changeOrder')}</button>)
 if(task.approvalId)actions.push(<button key="approval" className={approvalIsNext?'btn primary next-action':'btn'} onClick={()=>nav(`/approvals/${task.approvalId}`)}><ExternalLink size={15}/>{t('linkedApproval')}</button>)
 if(task.latestRunId)actions.push(<button key="run" className={runIsNext?'btn primary next-action':'btn'} onClick={()=>nav(`/automation/runs/${task.latestRunId}`)}><ExternalLink size={15}/>{t('linkedRun')}</button>)

 return <>
  <div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions">{actions}</div></div>
  <section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={task.priority==='P1'?'critical':task.priority==='P2'?'high':task.priority==='P3'?'medium':'low'}>{task.priority}</StatusBadge><StatusBadge tone="purple">{taskStage(t,task.stage)}</StatusBadge><StatusBadge tone={statusTone(task.status)}>{taskStatus(t,task.status)}</StatusBadge></div><h1>{task.taskNo}</h1><h2>{task.cveId} · {pick(task)}</h2></div></section>
  <div className="detail-grid"><section className="panel span-2"><div className="kv-grid three"><div><span>{t('asset')}</span><b>{localize(task.assetName)}</b></div><div><span>{t('assetCode')}</span><b>{task.assetCode}</b></div><div><span>{t('environment')}</span><b>{envLabel(t,task.environment)}</b></div><div><span>{t('businessService')}</span><b>{localize(task.businessService)}</b></div><div><span>{t('owner')}</span><b>{localize(task.ownerName)}</b></div><div><span>{t('patch')}</span><b>{task.patchCode||'—'}</b></div><div><span>{t('changeType')}</span><b>{task.changeType?t(task.changeType):'—'}</b></div><div><span>{t('due')}</span><b>{fmtDate(task.dueAt,lang)}</b></div><div><span>{t('updatedAt')}</span><b>{fmtDate(task.updatedAt,lang)}</b></div></div></section>{task.lastRetestMode&&<section className="panel span-2"><div className="panel-head"><h2>{t('retestRecord')}</h2></div><div className="kv-grid four"><div><span>{t('retestMode')}</span><b>{t(task.lastRetestMode==='AUTO'?'automaticRetest':'manualRetest')}</b></div><div><span>{t('retestResult')}</span><b>{task.lastRetestResult==='RUNNING'?t('retestRunning'):task.lastRetestResult==='PASSED'?t('retestPassed'):t('retestFailed')}</b></div><div><span>{t('retestedBy')}</span><b>{localize(task.lastRetestedBy)||'—'}</b></div><div><span>{t('retestedAt')}</span><b>{fmtDate(task.lastRetestedAt,lang)}</b></div>{task.lastRetestComment&&<div className="span-all"><span>{t('comment')}</span><b>{localize(task.lastRetestComment)}</b></div>}</div></section>}</div>
  <Modal open={!!verify} title={verify==='TEST'?t('verifyTest'):verify==='PREPROD'?t('verifyPreprod'):t('verifyProd')} onClose={()=>setVerify(null)} footer={<><button className="btn" onClick={()=>setVerify(null)}>{t('cancel')}</button><button className="btn primary" onClick={verifySubmit} disabled={busy||!comment.trim()}>{t('submit')}</button></>}><div className="form-grid"><label className="form-field"><span>{t('validationResult')}</span><select value={result} onChange={e=>setResult(e.target.value)}><option value="PASS">{t('pass')}</option><option value="FAIL">{t('fail')}</option></select></label><label className="form-field full"><span>{t('comment')} *</span><textarea rows={4} value={comment} onChange={e=>setComment(e.target.value)} placeholder={t('verificationBasis')}/></label></div></Modal>
  <Modal open={manualRetest} title={t('manualRetestResult')} onClose={()=>setManualRetest(false)} footer={<><button className="btn" onClick={()=>setManualRetest(false)}>{t('cancel')}</button><button className="btn primary" onClick={manualRetestSubmit} disabled={busy||!comment.trim()}>{t('submit')}</button></>}><div className="form-grid"><label className="form-field"><span>{t('retestResult')}</span><select value={result} onChange={e=>setResult(e.target.value)}><option value="PASS">{t('pass')}</option><option value="FAIL">{t('fail')}</option></select></label><label className="form-field full"><span>{t('comment')} *</span><textarea rows={4} value={comment} onChange={e=>setComment(e.target.value)} placeholder={t('verificationBasis')}/></label></div></Modal>
  <Modal open={assignOpen} title={t('reassign')} onClose={()=>setAssignOpen(false)} footer={<><button className="btn" onClick={()=>setAssignOpen(false)}>{t('cancel')}</button><button className="btn primary" disabled={!ownerSelection||busy} onClick={submitAssign}>{t('confirm')}</button></>}><label className="form-field"><span>{t('assignTo')}</span><select value={ownerSelection} onChange={e=>setOwnerSelection(e.target.value)}><option value="">—</option>{assignees.map(u=><option key={u.id} value={u.id}>{localize(u.displayName)} · {t(`role_${u.role}`)}</option>)}</select></label></Modal>
 </>
}
