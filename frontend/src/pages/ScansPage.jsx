import React,{useState} from 'react'
import { Plus, RefreshCw, Radar, Server } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { api } from '../api/client'
import { useI18n } from '../contexts/I18nContext'
import { useToast } from '../components/ToastContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import { fmtDate, scanTypeLabel, scanTargetLabel } from '../utils/format'

export default function ScansPage(){
 const {t,lang,localize}=useI18n();const toast=useToast();const nav=useNavigate();const [tab,setTab]=useState('jobs')
 const {data:jobs=[],loading,reload}=useApiData('/api/scans',{initial:[],poll:8000})
 const {data:agents=[],reload:reloadAgents}=useApiData('/api/scans/agents',{initial:[],poll:30000})
 const [open,setOpen]=useState(false)
 const [form,setForm]=useState({name:'',scanType:'AUTHENTICATED',targetType:'ALL',targetValue:'ALL',credentialType:'AGENT'})
 const create=async()=>{try{await api('/api/scans',{method:'POST',body:form});setOpen(false);setForm({...form,name:''});await reload();toast.push(t('scanCreated'))}catch(e){toast.push(t('operationFailed'),'red')}}
 const jobCols=[
  {key:'jobNo',label:'ID',render:r=><span className="mono linkish">{r.jobNo}</span>},{key:'name',label:t('name'),render:r=>localize(r.name)},
  {key:'scanType',label:t('scanType'),render:r=>scanTypeLabel(t,r.scanType)},{key:'targetValue',label:t('targetValue'),render:r=>scanTargetLabel(t,r,localize)},
  {key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{t(String(r.status).toLowerCase())}</StatusBadge>},
  {key:'progress',label:t('progress'),render:r=><div className="progress-cell"><div className="progress"><i style={{width:`${r.progress}%`}}/></div><small>{r.progress}%</small></div>},
  {key:'findingsCount',label:t('findings')},{key:'createdAt',label:t('createdAt'),render:r=>fmtDate(r.createdAt,lang)}
 ]
 const agentCols=[{key:'hostname',label:t('hostname'),render:r=><div className="cell-main"><b>{localize(r.hostname)}</b><small>{r.agentKey}</small></div>},{key:'ipAddress',label:t('ipAddress')},{key:'osName',label:t('os')},{key:'version',label:t('version')},{key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{t(String(r.status).toLowerCase())}</StatusBadge>},{key:'lastHeartbeatAt',label:t('lastHeartbeat'),render:r=>fmtDate(r.lastHeartbeatAt,lang)}]
 return <><PageHeader title={t('scanManagement')}><button className="btn" onClick={()=>{reload();reloadAgents()}}><RefreshCw size={15}/>{t('refresh')}</button><button className="btn primary" onClick={()=>setOpen(true)}><Plus size={15}/>{t('newScan')}</button></PageHeader><div className="tabs"><button className={tab==='jobs'?'active':''} onClick={()=>setTab('jobs')}><Radar size={15}/>{t('scanJobs')}<span>{jobs.length}</span></button><button className={tab==='agents'?'active':''} onClick={()=>setTab('agents')}><Server size={15}/>{t('agents')}<span>{agents.length}</span></button></div>{tab==='jobs'?<DataTable columns={jobCols} rows={jobs} onRowClick={r=>nav(`/scans/${r.id}`)} empty={loading?t('loading'):t('noData')}/>:<DataTable columns={agentCols} rows={agents}/>}<Modal open={open} title={t('createScan')} onClose={()=>setOpen(false)} footer={<><button className="btn" onClick={()=>setOpen(false)}>{t('cancel')}</button><button className="btn primary" disabled={!form.name.trim()} onClick={create}>{t('create')}</button></>}><div className="form-grid"><label className="form-field full"><span>{t('scanName')}</span><input value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/></label><label className="form-field"><span>{t('scanType')}</span><select value={form.scanType} onChange={e=>setForm({...form,scanType:e.target.value})}><option value="AUTHENTICATED">{t('authenticated')}</option><option value="NETWORK">{t('network')}</option></select></label><label className="form-field"><span>{t('credentialType')}</span><select value={form.credentialType} onChange={e=>setForm({...form,credentialType:e.target.value})}><option value="AGENT">{t('agent')}</option><option value="SSH">SSH</option><option value="WINRM">WinRM</option><option value="NONE">{t('none')}</option></select></label><label className="form-field"><span>{t('targetType')}</span><select value={form.targetType} onChange={e=>setForm({...form,targetType:e.target.value,targetValue:e.target.value==='ALL'?'ALL':''})}><option value="ALL">{t('allAssets')}</option><option value="ENVIRONMENT">{t('byEnvironment')}</option><option value="SERVICE">{t('byService')}</option><option value="ASSET_IDS">{t('byAsset')}</option></select></label><label className="form-field"><span>{t('targetValue')}</span><input value={form.targetValue} onChange={e=>setForm({...form,targetValue:e.target.value})} disabled={form.targetType==='ALL'}/></label></div></Modal></>
}
