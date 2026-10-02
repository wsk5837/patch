import React,{useEffect,useMemo,useRef,useState} from 'react'
import {CheckSquare2,Network,Play,RefreshCw,Search,ShieldCheck,SquareStack,UsersRound} from 'lucide-react'
import {useNavigate,useSearchParams} from 'react-router-dom'
import {api} from '../api/client'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import {useToast} from '../components/ToastContext'
import {useAuth} from '../contexts/AuthContext'
import PageHeader from '../components/PageHeader'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {envLabel} from '../utils/format'

const initialForm={patchId:'',cidrs:[],environment:'TEST',assetType:'ALL',osName:'',businessService:'',onlineOnly:true,batchSize:20,concurrency:10,failureThreshold:5,planName:'',maintenanceWindow:'',changeOrderId:''}
const typeKeys={DATABASE:'database',MIDDLEWARE:'middleware',APPLICATION_PLATFORM:'applicationPlatform',APPLICATION_RUNTIME:'applicationRuntime',SECURITY_COMPONENT:'securityComponent',OBSERVABILITY:'observability',COLLABORATION:'collaboration',FILE_SERVICE:'fileService',SEARCH_PLATFORM:'searchPlatform',UNCLASSIFIED:'unclassified',VIRTUAL_MACHINE:'virtualMachine',PHYSICAL_SERVER:'physicalServer',NETWORK_DEVICE:'networkDevice'}
const typeLabel=(t,value)=>t(typeKeys[value]||value)

export default function BatchPatchPage(){
 const {t,pick,localize}=useI18n();const toast=useToast();const nav=useNavigate();const [params]=useSearchParams();const {has}=useAuth();const canDeploy=has('PATCH_DEPLOY')||has('AUTOMATION_EXECUTE')
 const sourceAssetId=params.get('asset'),sourceCidr=params.get('cidr')
 const {data:patches=[],loading:patchLoading}=useApiData('/api/patches',{initial:[]})
 const {data:changes=[]}=useApiData('/api/work-orders/changes',{initial:[]})
 const {data:scopeOptions={networkSegments:[],assetTypes:[],businessServices:[],osNames:[]}}=useApiData('/api/assets/scope-options',{initial:{networkSegments:[],assetTypes:[],businessServices:[],osNames:[]}})
 const {data:runtimeSettings}=useApiData('/api/settings')
 const [form,setForm]=useState(initialForm),[preview,setPreview]=useState(null),[excluded,setExcluded]=useState(new Set()),[busy,setBusy]=useState(false),[segmentQuery,setSegmentQuery]=useState(''),[sourceAsset,setSourceAsset]=useState(null),[sourceResolved,setSourceResolved]=useState(!sourceAssetId)
 const initializedSegments=useRef(false),initializedPolicy=useRef(false)
 useEffect(()=>{if(!form.patchId&&patches.length){const preferred=patches.find(p=>/openssh/i.test(`${p.product||''} ${p.patchId||''}`))||patches[0];setForm(v=>({...v,patchId:String(preferred.id)}))}},[patches,form.patchId])
 useEffect(()=>{if(!sourceAssetId)return;let active=true;api(`/api/assets/${sourceAssetId}`).then(value=>{if(active)setSourceAsset(value)}).catch(()=>{}).finally(()=>{if(active)setSourceResolved(true)});return()=>{active=false}},[sourceAssetId])
 useEffect(()=>{if(initializedSegments.current||!sourceResolved||!scopeOptions.networkSegments?.length)return;initializedSegments.current=true;const requested=sourceCidr||sourceAsset?.networkSegment,preferred=requested&&scopeOptions.networkSegments.includes(requested)?requested:scopeOptions.networkSegments[0];setForm(v=>({...v,cidrs:preferred?[preferred]:[]}));if(requested)setSegmentQuery(requested)},[scopeOptions.networkSegments,sourceAsset,sourceCidr,sourceResolved])
 useEffect(()=>{if(runtimeSettings&&!initializedPolicy.current){initializedPolicy.current=true;setForm(v=>({...v,batchSize:Number(runtimeSettings.batchSize||v.batchSize),concurrency:Number(runtimeSettings.batchConcurrency||v.concurrency),failureThreshold:Number(runtimeSettings.autoRollbackThreshold||v.failureThreshold),maintenanceWindow:runtimeSettings.maintenanceWindow||v.maintenanceWindow}))}},[runtimeSettings])
 const cidrs=()=>form.cidrs
 const toggleCidr=cidr=>{setForm(v=>({...v,cidrs:v.cidrs.includes(cidr)?v.cidrs.filter(x=>x!==cidr):[...v.cidrs,cidr]}));setPreview(null)}
 const productionScope=form.environment==='ALL'||form.environment==='PROD'
 const eligibleChanges=useMemo(()=>changes.filter(c=>['APPROVED','IMPLEMENTING'].includes(c.status)&&String(c.patchId)===String(form.patchId)),[changes,form.patchId])
 const body=(excludedAssetIds=[...excluded])=>({patchId:Number(form.patchId),cidrs:cidrs(),environments:form.environment==='ALL'?[]:[form.environment],assetTypes:form.assetType==='ALL'?[]:[form.assetType],osName:form.osName,businessService:form.businessService,onlineOnly:form.onlineOnly,excludedAssetIds,batchSize:Number(form.batchSize),concurrency:Number(form.concurrency),failureThreshold:Number(form.failureThreshold),planName:form.planName||t('defaultBatchPlan'),maintenanceWindow:form.maintenanceWindow,changeOrderId:form.changeOrderId?Number(form.changeOrderId):null})
 const previewScope=async()=>{setBusy(true);try{const result=await api('/api/automation/batch/preview',{method:'POST',body:body([]),timeout:30000});setPreview(result);setExcluded(new Set());toast.push(t('scopeMatched'))}catch(e){toast.push(e.message||t('operationFailed'),'red')}finally{setBusy(false)}}
 const execute=async()=>{setBusy(true);try{const result=await api('/api/automation/batch/runs',{method:'POST',body:body(),timeout:30000});toast.push(t('batchRunStarted'));nav(`/automation/runs/${result.runId}`)}catch(e){toast.push(e.message||t('operationFailed'),'red')}finally{setBusy(false)}}
 const toggle=id=>setExcluded(prev=>{const next=new Set(prev);next.has(id)?next.delete(id):next.add(id);return next})
 const visibleSelected=useMemo(()=>preview?.assets?.filter(a=>!excluded.has(a.id)).length||0,[preview,excluded])
 const totalSelected=Math.max(0,(preview?.selectedCount||0)-excluded.size)
 const selectedPatch=patches.find(p=>String(p.id)===String(form.patchId))
 const visibleSegments=useMemo(()=>{const query=segmentQuery.trim().toLowerCase();return (scopeOptions.networkSegments||[]).filter(cidr=>!query||cidr.toLowerCase().includes(query))},[scopeOptions.networkSegments,segmentQuery])
 return <>
  <PageHeader title={t('batchPatch')}>{canDeploy&&<><button className="btn" disabled={busy||productionScope&&!form.changeOrderId} onClick={previewScope}><RefreshCw size={15}/>{t('refreshPreview')}</button><button className="btn primary next-action" disabled={busy||!preview||totalSelected===0||productionScope&&!form.changeOrderId} onClick={execute}><Play size={15}/>{t('startBatchRun')}</button></>}</PageHeader>
  <div className="batch-layout">
   <section className="panel batch-scope-panel"><div className="panel-head"><h2><Network size={18}/>{t('assetScope')}</h2><StatusBadge tone="purple">CIDR</StatusBadge></div><div className="panel-body form-grid">
    <label className="form-field full"><span>{t('choosePatch')}</span><select value={form.patchId} onChange={e=>{setForm({...form,patchId:e.target.value});setPreview(null)}} disabled={patchLoading}>{patches.map(p=><option key={p.id} value={p.id}>{p.patchId} · {pick(p)}</option>)}</select></label>
    <div className="form-field full"><span>{t('networkSegments')} · {t('selectedOf',form.cidrs.length,scopeOptions.networkSegments?.length||0)}</span>{sourceAsset&&form.cidrs.includes(sourceAsset.networkSegment)&&<small className="scope-source-hint">{t('sourceAssetScope')} · {localize(sourceAsset.name)} · {sourceAsset.networkSegment}</small>}<label className="segment-search"><Search size={14}/><input value={segmentQuery} onChange={e=>setSegmentQuery(e.target.value)} placeholder={t('searchNetworkSegments')}/></label><div className="segment-picker">{visibleSegments.map(cidr=><button type="button" key={cidr} className={`segment-option ${form.cidrs.includes(cidr)?'selected':''}`} onClick={()=>toggleCidr(cidr)}><input type="checkbox" readOnly checked={form.cidrs.includes(cidr)}/><b>{cidr}</b></button>)}{!visibleSegments.length&&<div className="segment-empty">{t('noMatchingSegments')}</div>}</div><div className="segment-actions"><button className="text-button" type="button" onClick={()=>{setForm({...form,cidrs:[...(scopeOptions.networkSegments||[])]});setPreview(null)}}>{t('selectAllSegments')}</button><button className="text-button" type="button" onClick={()=>{setForm({...form,cidrs:[]});setPreview(null)}}>{t('clearSegments')}</button></div></div>
    <label className="form-field"><span>{t('environment')}</span><select value={form.environment} onChange={e=>{setForm({...form,environment:e.target.value});setPreview(null)}}><option value="ALL">{t('all')}</option><option value="TEST">{t('test')}</option><option value="PREPROD">{t('preprod')}</option><option value="PROD">{t('production')}</option></select></label>
    <label className="form-field"><span>{t('assetType')}</span><select value={form.assetType} onChange={e=>{setForm({...form,assetType:e.target.value});setPreview(null)}}><option value="ALL">{t('all')}</option>{scopeOptions.assetTypes?.map(value=><option key={value} value={value}>{typeLabel(t,value)}</option>)}</select></label>
    <label className="form-field"><span>{t('os')}</span><select value={form.osName} onChange={e=>{setForm({...form,osName:e.target.value});setPreview(null)}}><option value="">{t('all')}</option>{scopeOptions.osNames?.map(value=><option key={value} value={value}>{value}</option>)}</select></label>
    <label className="form-field"><span>{t('businessService')}</span><select value={form.businessService} onChange={e=>{setForm({...form,businessService:e.target.value});setPreview(null)}}><option value="">{t('all')}</option>{scopeOptions.businessServices?.map(value=><option key={value} value={value}>{localize(value)}</option>)}</select></label>
    {productionScope&&<label className="form-field full"><span>{t('approvedChange')} *</span><select value={form.changeOrderId} onChange={e=>{setForm({...form,changeOrderId:e.target.value});setPreview(null)}}><option value="">{t('productionChangeRequired')}</option>{eligibleChanges.map(c=><option key={c.id} value={c.id}>{c.changeNo} · {c.patchCode} · {localize(c.summary)}</option>)}</select></label>}
    <label className="check-field full"><input type="checkbox" checked={form.onlineOnly} onChange={e=>{setForm({...form,onlineOnly:e.target.checked});setPreview(null)}}/><span>{t('onlineOnly')}</span></label>
    {canDeploy&&<button className="btn primary full" disabled={busy||!form.patchId||cidrs().length===0||productionScope&&!form.changeOrderId} onClick={previewScope}><ShieldCheck size={15}/>{t('matchAssets')}</button>}
   </div></section>
   <section className="panel batch-strategy-panel"><div className="panel-head"><h2><SquareStack size={18}/>{t('batchStrategy')}</h2></div><div className="panel-body form-grid">
    <label className="form-field full"><span>{t('planName')}</span><input value={form.planName} onChange={e=>setForm({...form,planName:e.target.value})} placeholder={t('planNamePlaceholder')}/></label>
    <label className="form-field"><span>{t('batchSize')}</span><input type="number" min="1" max="500" value={form.batchSize} onChange={e=>{setForm({...form,batchSize:e.target.value});setPreview(null)}}/></label>
    <label className="form-field"><span>{t('concurrency')}</span><input type="number" min="1" max="200" value={form.concurrency} onChange={e=>{setForm({...form,concurrency:e.target.value});setPreview(null)}}/></label>
    <label className="form-field"><span>{t('failureThreshold')}</span><div className="input-suffix"><input type="number" min="0.1" max="100" step="0.1" value={form.failureThreshold} onChange={e=>{setForm({...form,failureThreshold:e.target.value});setPreview(null)}}/><i>%</i></div></label>
    <label className="form-field"><span>{t('maintenanceWindow')}</span><input value={form.maintenanceWindow} onChange={e=>setForm({...form,maintenanceWindow:e.target.value})} placeholder="Sun 01:00-05:00"/></label>
    <div className="strategy-summary full"><div><span>{t('selectedPatch')}</span><b>{selectedPatch?.patchId||'—'}</b></div><div><span>{t('estimatedBatches')}</span><b>{preview?.totalBatches??'—'}</b></div><div><span>{t('selectedAssets')}</span><b>{preview?totalSelected:'—'}</b></div></div>
   </div></section>
  </div>
  {preview&&<>
   <div className="scope-metrics"><div><Network/><span>{t('cidrMatched')}</span><b>{preview.matchedCount}</b></div><div><CheckSquare2/><span>{t('patchApplicable')}</span><b>{preview.applicableCount}</b></div><div><UsersRound/><span>{t('selectedAssets')}</span><b>{totalSelected}</b></div><div><SquareStack/><span>{t('estimatedBatches')}</span><b>{preview.totalBatches}</b></div><div><ShieldCheck/><span>{t('offline')}</span><b>{preview.offlineCount}</b></div></div>
   <section className="panel batch-target-panel"><div className="panel-head"><h2>{t('targetAssets')}</h2><div className="panel-actions"><span className="toolbar-count">{t('selectedOf',visibleSelected,preview.assets?.length||0)}</span><button className="btn small" onClick={()=>setExcluded(new Set())}>{t('selectAll')}</button><button className="btn small" onClick={()=>setExcluded(new Set(preview.assets?.map(a=>a.id)||[]))}>{t('clearVisible')}</button></div></div>
    <div className="batch-table-wrap"><table className="batch-table"><thead><tr><th>{t('select')}</th><th>{t('batch')}</th><th>{t('asset')}</th><th>{t('assetType')}</th><th>{t('ipAddress')}</th><th>{t('networkSegment')}</th><th>{t('environment')}</th><th>{t('os')}</th><th>{t('businessService')}</th><th>{t('agentStatus')}</th></tr></thead><tbody>{preview.assets?.map((a,index)=>{const selected=!excluded.has(a.id);const batch=Math.floor((index-[...excluded].filter(id=>preview.assets.findIndex(x=>x.id===id)<index).length)/Number(form.batchSize))+1;return <tr key={a.id} className={selected?'':'excluded'}><td><input type="checkbox" checked={selected} onChange={()=>toggle(a.id)}/></td><td><span className="batch-number">{selected?batch:'—'}</span></td><td><div className="cell-main"><b>{localize(a.name)}</b><small>{a.assetCode}</small></div></td><td><StatusBadge tone="purple">{typeLabel(t,a.assetType)}</StatusBadge></td><td className="mono">{a.ipAddress}</td><td className="mono">{a.networkSegment}</td><td>{envLabel(t,a.environment)}</td><td><div className="cell-main"><b>{a.osName}</b><small>{a.osVersion}</small></div></td><td>{localize(a.businessService)}</td><td><StatusBadge tone={statusTone(a.agentStatus)}>{t(String(a.agentStatus).toLowerCase())}</StatusBadge></td></tr>})}</tbody></table></div>
   </section>
  </>}
 </>
}
