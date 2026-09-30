import React from 'react'
import {ArrowLeft,Network,ShieldAlert} from 'lucide-react'
import {useNavigate,useParams} from 'react-router-dom'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import DataTable from '../components/DataTable'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {assetTypeLabel,envLabel,findingStatus,fmtDate,severityLabel} from '../utils/format'

export default function AssetDetailPage(){
 const {id}=useParams(),nav=useNavigate(),{t,lang,localize,pick}=useI18n()
 const {data:a,loading}=useApiData(`/api/assets/${id}`)
 const {data:findings=[]}=useApiData(`/api/vulnerabilities/findings?assetId=${id}`,{initial:[]})
 if(loading&&!a)return <div className="loading">{t('loading')}</div>;if(!a)return null
 const products=(a.installedProducts||'').split(',').map(x=>x.trim()).filter(Boolean)
 const cols=[
  {key:'cveId',label:t('cve'),render:r=><div className="cell-main"><b className="mono linkish">{r.cveId}</b><small>{pick(r)}</small></div>},
  {key:'severity',label:t('severity'),render:r=><StatusBadge tone={severityTone(r.severity)}>{severityLabel(t,r.severity)}</StatusBadge>},
  {key:'riskScore',label:t('riskScore')},
  {key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{findingStatus(t,r.status)}</StatusBadge>},
  {key:'remediationTaskId',label:t('remediationTask'),render:r=>r.remediationTaskId?<button className="text-button mono" onClick={e=>{e.stopPropagation();nav(`/tasks/${r.remediationTaskId}`)}}>{r.remediationTaskNo||`RMD · ${r.remediationTaskId}`}</button>:'—'},
  {key:'lastSeenAt',label:t('lastSeen'),render:r=>fmtDate(r.lastSeenAt,lang)}
 ]
 return <>
  <div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions"><button className="btn primary" onClick={()=>nav(`/automation/batch?asset=${a.id}`)}><Network size={15}/>{t('batchPatch')}</button></div></div>
  <section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={a.environment==='PROD'?'purple':'gray'}>{envLabel(t,a.environment)}</StatusBadge><StatusBadge tone={a.agentStatus==='ONLINE'?'ok':'critical'}>{t(String(a.agentStatus).toLowerCase())}</StatusBadge>{a.internetExposed&&<StatusBadge tone="critical">{t('internetExposure')}</StatusBadge>}</div><h1>{localize(a.name)}</h1><h2>{a.assetCode} · {a.hostname||a.ipAddress}</h2></div><div className="score-box"><b>{a.openFindings}</b><span>{t('openVulnerabilities')}</span></div></section>
  <div className="detail-grid">
   <section className="panel"><div className="panel-head"><h2>{t('assetProfile')}</h2></div><div className="kv-grid"><div><span>{t('assetCode')}</span><b className="mono">{a.assetCode}</b></div><div><span>{t('assetType')}</span><b>{assetTypeLabel(t,a.assetType)}</b></div><div><span>{t('ipAddress')}</span><b className="mono">{a.ipAddress}</b></div><div><span>{t('networkSegment')}</span><b className="mono">{a.networkSegment}</b></div><div><span>{t('os')}</span><b>{a.osName} {a.osVersion}</b></div><div><span>{t('zone')}</span><b>{localize(a.zone)||'—'}</b></div><div><span>{t('criticality')}</span><b>{a.criticality}/5</b></div><div><span>{t('lastSeen')}</span><b>{fmtDate(a.lastSeenAt,lang)}</b></div></div></section>
   <section className="panel"><div className="panel-head"><h2>{t('ownershipAndPatch')}</h2></div><div className="kv-grid"><div><span>{t('businessService')}</span><b>{localize(a.businessService)}</b></div><div><span>{t('owner')}</span><b>{localize(a.ownerName)}</b></div><div><span>{t('baseline')}</span><b>{a.patchBaseline||'—'}</b></div><div><span>{t('maintenanceWindow')}</span><b>{localize(a.maintenanceWindow)||'—'}</b></div></div><div className="panel-head top-border"><h2>{t('installedSoftware')}</h2></div><div className="panel-body tag-list">{products.length?products.map(p=><StatusBadge key={p} tone="gray">{p}</StatusBadge>):<span className="muted">{t('noData')}</span>}</div></section>
   <section className="panel span-2"><div className="panel-head"><h2><ShieldAlert size={16}/>{t('assetFindings')}</h2><span>{findings.length}</span></div><DataTable columns={cols} rows={findings} onRowClick={r=>nav(`/vulnerabilities/findings/${r.id}`)} pageSize={10}/></section>
  </div>
 </>
}
