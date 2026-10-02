import React,{useState} from 'react'
import { RefreshCw, Package, Server, Rocket, Plus, Download } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { api } from '../api/client'
import { useI18n } from '../contexts/I18nContext'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../components/ToastContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {fmtDate,envLabel,serverStatus,deploymentStatus} from '../utils/format'

const emptyPatch={patchId:'',vendor:'',product:'',version:'',titleZh:'',titleEn:'',downloadUrl:'',checksum:'',sizeMb:'',rebootRequired:false,cves:'',applicabilityRule:'',supersedes:'',releaseNotesZh:'',releaseNotesEn:''}

export default function PatchesPage(){
  const {t,pick,lang}=useI18n()
  const {has}=useAuth()
  const nav=useNavigate()
  const toast=useToast()
  const [tab,setTab]=useState('library')
  const [open,setOpen]=useState(false)
  const [busy,setBusy]=useState(false)
  const [form,setForm]=useState(emptyPatch)
  const {data:patches=[],reload}=useApiData('/api/patches',{initial:[]})
  const {data:servers=[],reload:reloadServers}=useApiData('/api/patches/servers',{initial:[]})
  const {data:deployments=[],reload:reloadDeployments}=useApiData('/api/patches/deployments',{initial:[],poll:10000})

  const sync=async()=>{
    try{
      await api('/api/patches/sync',{method:'POST'})
      await Promise.all([reload(),reloadServers()])
      toast.push(t('syncDone'))
    }catch(e){toast.push(t('operationFailed'),'red')}
  }

  const register=async()=>{
    setBusy(true)
    try{
      await api('/api/patches',{method:'POST',body:{...form,sizeMb:form.sizeMb?Number(form.sizeMb):null,cves:form.cves.split(/[,\s]+/).filter(Boolean)}})
      setOpen(false)
      setForm(emptyPatch)
      await reload()
      toast.push(t('patchRegistered'))
    }catch(e){toast.push(t('operationFailed'),'red')}
    finally{setBusy(false)}
  }

  const libCols=[
    {key:'patchId',label:t('patchId'),render:r=><span className="mono linkish">{r.patchId}</span>},
    {key:'title',label:t('name'),render:r=><div className="cell-main"><b>{pick(r)}</b><small>{r.vendor} · {r.product} · {r.version||'—'}</small></div>},
    {key:'cves',label:t('cve'),render:r=><div className="chip-list mini">{r.cves?.slice(0,3).map(x=><span className="chip" key={x}>{x}</span>)}</div>},
    {key:'affectedAssets',label:t('affectedAssets')},
    {key:'sizeMb',label:t('size'),render:r=>r.sizeMb?`${r.sizeMb} MB`:'—'},
    {key:'rebootRequired',label:t('rebootRequired'),render:r=><StatusBadge tone={r.rebootRequired?'warn':'gray'}>{r.rebootRequired?t('yes'):t('no')}</StatusBadge>},
    {key:'download',label:t('action'),render:r=>r.downloadUrl?<button className="btn compact" title={t('officialPatchSource')} onClick={e=>{e.stopPropagation();window.open(r.downloadUrl,'_blank','noopener,noreferrer')}}><Download size={14}/>{t('downloadPackage')}</button>:<span className="muted">{t('packageSourceUnavailable')}</span>}
  ]
  const serverCols=[
    {key:'name',label:t('name'),render:r=><span className="mono">{r.name}</span>},
    {key:'address',label:t('serverAddress')},{key:'region',label:t('region')},{key:'osSupport',label:t('osSupport')},
    {key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{serverStatus(t,r.status)}</StatusBadge>},
    {key:'lastSyncAt',label:t('lastSync'),render:r=>fmtDate(r.lastSyncAt,lang)},
    {key:'usedGb',label:t('used'),render:r=>`${r.usedGb}/${r.capacityGb} GB`}
  ]
  const depCols=[
    {key:'deploymentNo',label:t('deploymentNo'),render:r=><span className="mono">{r.deploymentNo}</span>},
    {key:'patchCode',label:t('patchId')},{key:'environment',label:t('environment'),render:r=>envLabel(t,r.environment)},
    {key:'ring',label:t('ring')},{key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{deploymentStatus(t,r.status)}</StatusBadge>},
    {key:'progress',label:t('progress'),render:r=><div className="progress-cell"><div className="progress"><i style={{width:`${r.progress}%`}}/></div><small>{r.progress}%</small></div>},
    {key:'successCount',label:t('success')},{key:'failureCount',label:t('failure')}
  ]

  return <>
    <PageHeader title={t('patchCenter')}>
      <button className="btn" onClick={()=>{reload();reloadServers();reloadDeployments()}}><RefreshCw size={15}/>{t('refresh')}</button>
      {(has('PATCH_REGISTER')||has('PATCH_MANAGE'))&&tab==='library'&&<button className="btn" onClick={()=>setOpen(true)}><Plus size={15}/>{t('registerPatch')}</button>}
      {(has('PATCH_CATALOG_SYNC')||has('PATCH_MANAGE'))&&<button className="btn primary" onClick={sync}>{t('syncPatchCatalog')}</button>}
    </PageHeader>
    <div className="tabs">
      <button className={tab==='library'?'active':''} onClick={()=>setTab('library')}><Package size={15}/>{t('patchLibrary')}<span>{patches.length}</span></button>
      <button className={tab==='servers'?'active':''} onClick={()=>setTab('servers')}><Server size={15}/>{t('patchServers')}<span>{servers.length}</span></button>
      <button className={tab==='deployments'?'active':''} onClick={()=>setTab('deployments')}><Rocket size={15}/>{t('deployments')}<span>{deployments.length}</span></button>
    </div>
    {tab==='library'?<DataTable columns={libCols} rows={patches} onRowClick={r=>nav(`/patches/${r.id}`)}/>:tab==='servers'?<DataTable columns={serverCols} rows={servers}/>:<DataTable columns={depCols} rows={deployments} onRowClick={r=>r.orchestrationRunId&&nav(`/automation/runs/${r.orchestrationRunId}`)}/>}
    <Modal open={open} title={t('registerPatch')} onClose={()=>setOpen(false)} size="lg" footer={<><button className="btn" onClick={()=>setOpen(false)}>{t('cancel')}</button><button className="btn primary" disabled={busy||!form.patchId.trim()||!form.vendor.trim()||!form.product.trim()||!form.titleZh.trim()||!form.titleEn.trim()} onClick={register}>{t('save')}</button></>}>
      <div className="form-grid">
        <label className="form-field"><span>{t('patchId')}</span><input value={form.patchId} onChange={e=>setForm({...form,patchId:e.target.value})}/></label>
        <label className="form-field"><span>{t('vendor')}</span><input value={form.vendor} onChange={e=>setForm({...form,vendor:e.target.value})}/></label>
        <label className="form-field"><span>{t('product')}</span><input value={form.product} onChange={e=>setForm({...form,product:e.target.value})}/></label>
        <label className="form-field"><span>{t('version')}</span><input value={form.version} onChange={e=>setForm({...form,version:e.target.value})}/></label>
        <label className="form-field"><span>{t('size')} (MB)</span><input type="number" min="0" step="0.1" value={form.sizeMb} onChange={e=>setForm({...form,sizeMb:e.target.value})}/></label>
        <label className="form-field full"><span>{t('titleZh')}</span><input value={form.titleZh} onChange={e=>setForm({...form,titleZh:e.target.value})}/></label>
        <label className="form-field full"><span>{t('titleEn')}</span><input value={form.titleEn} onChange={e=>setForm({...form,titleEn:e.target.value})}/></label>
        <label className="form-field full"><span>{t('downloadUrl')}</span><input value={form.downloadUrl} onChange={e=>setForm({...form,downloadUrl:e.target.value})}/></label>
        <label className="form-field"><span>{t('checksum')}</span><input value={form.checksum} onChange={e=>setForm({...form,checksum:e.target.value})}/></label>
        <label className="form-field"><span>{t('rebootRequired')}</span><select value={form.rebootRequired?'true':'false'} onChange={e=>setForm({...form,rebootRequired:e.target.value==='true'})}><option value="false">{t('no')}</option><option value="true">{t('yes')}</option></select></label>
        <label className="form-field full"><span>{t('cveMappings')}</span><input value={form.cves} onChange={e=>setForm({...form,cves:e.target.value})} placeholder="CVE-2025-..., CVE-2026-..."/></label>
        <label className="form-field full"><span>{t('applicability')}</span><textarea rows={3} value={form.applicabilityRule} onChange={e=>setForm({...form,applicabilityRule:e.target.value})}/></label>
        <label className="form-field full"><span>{t('supersedes')}</span><input value={form.supersedes} onChange={e=>setForm({...form,supersedes:e.target.value})}/></label>
        <label className="form-field full"><span>{t('releaseNotes')} · {t('titleZh')}</span><textarea rows={3} value={form.releaseNotesZh} onChange={e=>setForm({...form,releaseNotesZh:e.target.value})}/></label>
        <label className="form-field full"><span>{t('releaseNotes')} · {t('titleEn')}</span><textarea rows={3} value={form.releaseNotesEn} onChange={e=>setForm({...form,releaseNotesEn:e.target.value})}/></label>
      </div>
    </Modal>
  </>
}
