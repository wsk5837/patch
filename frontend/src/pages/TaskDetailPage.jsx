import React,{useEffect,useState} from 'react'
import { ArrowLeft, Play, CheckCircle2, RefreshCw, ExternalLink, UserRoundCog, ScanSearch, UserCheck, Route } from 'lucide-react'
import { useNavigate,useParams } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { api } from '../api/client'
import { useI18n } from '../contexts/I18nContext'
import { useToast } from '../components/ToastContext'
import { useAuth } from '../contexts/AuthContext'
import Modal from '../components/Modal'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {envLabel,taskStage,taskStatus,fmtDate} from '../utils/format'
import LifecycleMap from '../components/LifecycleMap'

export default function TaskDetailPage(){
 const {id}=useParams()
 const nav=useNavigate()
 const {t,pick,lang,localize}=useI18n()
 const toast=useToast()
 const {has}=useAuth()
 const {data:task,loading,reload}=useApiData(`/api/tasks/${id}`,{poll:8000})
 const {data:assignees=[]}=useApiData('/api/users/assignees',{initial:[]})
 const [verify,setVerify]=useState(null)
 const [result,setResult]=useState('PASS')
 const [comment,setComment]=useState('')
 const [busy,setBusy]=useState(false)
 const [assignOpen,setAssignOpen]=useState(false)
 const [manualRetest,setManualRetest]=useState(false)
 const [ownerSelection,setOwnerSelection]=useState('')
 const [targetPicker,setTargetPicker]=useState(null)
 const [targetAssetId,setTargetAssetId]=useState('')
 const [lifecycleOpen,setLifecycleOpen]=useState(false)

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
 const startAutomaticApplicationTest=async()=>{
  setBusy(true)
  try{const updated=await api(`/api/tasks/${id}/actions/start-auto-app-test`,{method:'POST',body:{}});toast.push(t('applicationTestStarted'));if(updated.latestRunId)nav(`/automation/runs/${updated.latestRunId}`);else await reload()}
  catch(e){toast.push(e.message||t('operationFailed'),'red')}
  finally{setBusy(false)}
 }
 const openDeployment=async(action,environment)=>{
  setBusy(true)
  try{
   const candidates=await api(`/api/tasks/${id}/deployment-candidates?environment=${environment}`)
   if(!candidates.length){toast.push(t('noCompatibleValidationAsset'),'red');return}
   const exact=candidates.filter(x=>x.environment===environment&&x.businessService===task.businessService)
   const preferred=(exact.length?exact:candidates)[0]
   setTargetAssetId(String(preferred.id));setTargetPicker({action,environment,candidates,exact:exact.length>0})
  }catch(e){toast.push(e.message||t('operationFailed'),'red')}
  finally{setBusy(false)}
 }
 const startDeployment=async()=>{
  if(!targetPicker||!targetAssetId)return
  if(await doAction(targetPicker.action,{targetAssetId:Number(targetAssetId)})){setTargetPicker(null);setTargetAssetId('')}
 }
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
 actions.push(<button key="lifecycle" className="btn" onClick={()=>setLifecycleOpen(true)}><Route size={15}/>{t('lifecycleOverview')}</button>)
 if(task.stage!=='CLOSED'&&(has('TASK_ASSIGN')||has('TASK_MANAGE')))actions.push(<button key="assign" className="btn" onClick={()=>setAssignOpen(true)}><UserRoundCog size={15}/>{t('reassign')}</button>)
 if(task.assetId)actions.push(<button key="asset" className="btn" onClick={()=>nav(`/assets/${task.assetId}`)}><ExternalLink size={15}/>{t('viewAsset')}</button>)
 if(task.stage==='ASSIGNED'&&(has('TASK_EXECUTE')||has('TASK_MANAGE')))actions.push(<button key="start" className="btn primary" disabled={busy} onClick={()=>openDeployment('start-test','TEST')}><Play size={15}/>{t('startTestPatch')}</button>)
 if(['APP_VERIFY','PREPROD_VERIFY','PROD_VERIFY'].includes(task.stage)&&(has('TASK_RETEST')||has('TASK_MANAGE'))){
  const env=task.stage==='APP_VERIFY'?'TEST':task.stage==='PREPROD_VERIFY'?'PREPROD':'PROD'
  actions.push(<button key="auto-app-test" className="btn primary" disabled={busy} onClick={startAutomaticApplicationTest}><Play size={15}/>{t('automaticApplicationTest')}</button>)
  actions.push(<button key="manual-app-test" className="btn" disabled={busy} onClick={()=>setVerify(env)}><CheckCircle2 size={15}/>{t('manualApplicationTest')}</button>)
 }
 if(['TEST_RESCAN','PREPROD_RESCAN','PROD_RESCAN'].includes(task.stage)&&(has('TASK_RETEST')||has('TASK_MANAGE'))){
  const running=task.lastRetestMode==='AUTO'&&task.lastRetestResult==='RUNNING'
  actions.push(<button key="auto-retest" className="btn primary" disabled={busy||running} onClick={()=>doAction('start-auto-retest',{retestMode:'AUTO'})}><ScanSearch size={15}/>{running?t('retestRunning'):t('automaticRetest')}</button>)
  actions.push(<button key="manual-retest" className="btn" disabled={busy||running} onClick={openManualRetest}><UserCheck size={15}/>{t('manualRetest')}</button>)
 }
 if(task.status==='BLOCKED'&&['TEST_PATCH','PREPROD_PATCH','PROD_PATCH'].includes(task.stage)&&(has('TASK_EXECUTE')||has('TASK_MANAGE'))){const env=task.stage.replace('_PATCH','');actions.push(<button key="retry" className="btn primary" onClick={()=>openDeployment('retry',env)}><RefreshCw size={15}/>{t('retry')}</button>)}
 if(task.securityIncidentId)actions.push(<button key="incident" className={task.stage==='RELEASE_APPROVAL'&&!task.changeOrderId?'btn primary':'btn'} onClick={()=>nav(`/work-orders/incidents/${task.securityIncidentId}`)}><ExternalLink size={15}/>{t(task.stage==='RELEASE_APPROVAL'&&!task.changeOrderId?'createChange':'securityIncident')}</button>)
 if(task.changeOrderId)actions.push(<button key="change" className="btn" onClick={()=>nav(`/work-orders/changes/${task.changeOrderId}`)}><ExternalLink size={15}/>{t('changeOrder')}</button>)
 if(task.approvalId)actions.push(<button key="approval" className={approvalIsNext?'btn primary next-action':'btn'} onClick={()=>nav(`/approvals/${task.approvalId}`)}><ExternalLink size={15}/>{t('linkedApproval')}</button>)
 if(task.latestRunId)actions.push(<button key="run" className={runIsNext?'btn primary next-action':'btn'} onClick={()=>nav(`/automation/runs/${task.latestRunId}`)}><ExternalLink size={15}/>{t('linkedRun')}</button>)

 return <>
  <div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions" role="group" aria-label={t('operationArea')}>{actions}</div></div>
  <section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={task.priority==='P1'?'critical':task.priority==='P2'?'high':task.priority==='P3'?'medium':'low'}>{task.priority}</StatusBadge><StatusBadge tone="purple">{taskStage(t,task.stage)}</StatusBadge><StatusBadge tone={statusTone(task.status)}>{taskStatus(t,task.status)}</StatusBadge></div><h1>{task.taskNo}</h1><h2>{task.cveId} · {pick(task)}</h2></div></section>
  <div className="detail-grid"><section className="panel span-2"><div className="kv-grid three"><div><span>{t('asset')}</span><b>{localize(task.assetName)}</b></div><div><span>{t('assetCode')}</span><b>{task.assetCode}</b></div><div><span>{t('environment')}</span><b>{envLabel(t,task.environment)}</b></div><div><span>{t('businessService')}</span><b>{localize(task.businessService)}</b></div><div><span>{t('owner')}</span><b>{localize(task.ownerName)}</b></div><div><span>{t('patch')}</span><b>{task.patchCode||'—'}</b></div><div><span>{t('changeType')}</span><b>{task.changeType?t(task.changeType):'—'}</b></div><div><span>{t('due')}</span><b>{fmtDate(task.dueAt,lang)}</b></div><div><span>{t('updatedAt')}</span><b>{fmtDate(task.updatedAt,lang)}</b></div></div></section>{task.lastRetestMode&&<section className="panel span-2"><div className="panel-head"><h2>{t('retestRecord')}</h2></div><div className="kv-grid four"><div><span>{t('retestMode')}</span><b>{t(task.lastRetestMode==='AUTO'?'automaticRetest':'manualRetest')}</b></div><div><span>{t('retestResult')}</span><b>{task.lastRetestResult==='RUNNING'?t('retestRunning'):task.lastRetestResult==='PASSED'?t('retestPassed'):t('retestFailed')}</b></div><div><span>{t('retestedBy')}</span><b>{localize(task.lastRetestedBy)||'—'}</b></div><div><span>{t('retestedAt')}</span><b>{fmtDate(task.lastRetestedAt,lang)}</b></div>{task.lastRetestComment&&<div className="span-all"><span>{t('comment')}</span><b>{localize(task.lastRetestComment)}</b></div>}</div></section>}</div>
  <Modal open={!!verify} title={verify==='TEST'?t('verifyTest'):verify==='PREPROD'?t('verifyPreprod'):t('verifyProd')} onClose={()=>setVerify(null)} footer={<><button className="btn" onClick={()=>setVerify(null)}>{t('cancel')}</button><button className="btn primary" onClick={verifySubmit} disabled={busy||!comment.trim()}>{t('submit')}</button></>}><div className="form-grid"><label className="form-field"><span>{t('validationResult')}</span><select value={result} onChange={e=>setResult(e.target.value)}><option value="PASS">{t('pass')}</option><option value="FAIL">{t('fail')}</option></select></label><label className="form-field full"><span>{t('comment')} *</span><textarea rows={4} value={comment} onChange={e=>setComment(e.target.value)} placeholder={t('verificationBasis')}/></label></div></Modal>
  <Modal open={manualRetest} title={t('manualRetestResult')} onClose={()=>setManualRetest(false)} footer={<><button className="btn" onClick={()=>setManualRetest(false)}>{t('cancel')}</button><button className="btn primary" onClick={manualRetestSubmit} disabled={busy||!comment.trim()}>{t('submit')}</button></>}><div className="form-grid"><label className="form-field"><span>{t('retestResult')}</span><select value={result} onChange={e=>setResult(e.target.value)}><option value="PASS">{t('pass')}</option><option value="FAIL">{t('fail')}</option></select></label><label className="form-field full"><span>{t('comment')} *</span><textarea rows={4} value={comment} onChange={e=>setComment(e.target.value)} placeholder={t('verificationBasis')}/></label></div></Modal>
  <Modal open={assignOpen} title={t('reassign')} onClose={()=>setAssignOpen(false)} footer={<><button className="btn" onClick={()=>setAssignOpen(false)}>{t('cancel')}</button><button className="btn primary" disabled={!ownerSelection||busy} onClick={submitAssign}>{t('confirm')}</button></>}><label className="form-field"><span>{t('assignTo')}</span><select value={ownerSelection} onChange={e=>setOwnerSelection(e.target.value)}><option value="">—</option>{assignees.map(u=><option key={u.id} value={u.id}>{localize(u.displayName)} · {t(`role_${u.role}`)}</option>)}</select></label></Modal>
  <Modal open={!!targetPicker} title={t('selectValidationAsset')} onClose={()=>setTargetPicker(null)} footer={<><button className="btn" onClick={()=>setTargetPicker(null)}>{t('cancel')}</button><button className="btn primary" disabled={!targetAssetId||busy} onClick={startDeployment}>{t('confirmAndExecute')}</button></>}>
   <div className="validation-targets">{targetPicker&&!targetPicker.exact&&<div className="target-warning">{t('compatibleAssetFallback')}</div>}{targetPicker?.candidates.map(asset=>{const isolated=asset.environment!==targetPicker.environment;return <label className={`validation-target ${String(asset.id)===targetAssetId?'selected':''}`} key={asset.id}><input type="radio" name="validationTarget" value={asset.id} checked={String(asset.id)===targetAssetId} onChange={e=>setTargetAssetId(e.target.value)}/><span><b>{localize(asset.name)}</b><small>{asset.assetCode} · {asset.ipAddress||'—'} · {isolated?t('isolatedValidationNote'):localize(asset.businessService)||'—'}</small></span><StatusBadge tone={isolated?'purple':asset.businessService===task.businessService?'ok':'purple'}>{isolated?t('isolatedValidationBaseline'):asset.businessService===task.businessService?t('sameBusinessService'):t('compatibleAsset')}</StatusBadge></label>})}</div>
  </Modal>
  <Modal open={lifecycleOpen} title={t('lifecycleOverview')} size="xl" onClose={()=>setLifecycleOpen(false)}><div className="lifecycle-modal-head"><b>{task.taskNo}</b><span>{t('lifecycleClickHint')}</span></div><LifecycleMap task={task} onNavigate={path=>{setLifecycleOpen(false);nav(path)}}/></Modal>
 </>
}
