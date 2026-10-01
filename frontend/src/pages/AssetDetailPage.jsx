import React,{useEffect,useMemo,useState} from 'react'
import {ArrowLeft,Network,Radar,ShieldAlert} from 'lucide-react'
import {useNavigate,useParams} from 'react-router-dom'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import DataTable from '../components/DataTable'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {assetTypeLabel,cmdbClassLabel,envLabel,findingStatus,fmtDate,severityLabel} from '../utils/format'
import {api} from '../api/client'

export default function AssetDetailPage(){
 const {id}=useParams(),nav=useNavigate(),{t,lang,localize,pick}=useI18n()
 const {data:a,loading}=useApiData(`/api/assets/${id}`)
 const {data:findings=[]}=useApiData(`/api/vulnerabilities/findings?assetId=${id}`,{initial:[]})
 const [cmdb,setCmdb]=useState(null),[cmdbLoading,setCmdbLoading]=useState(false)
 useEffect(()=>{let active=true;if(a?.sourceSystem!=='CMDB')return;setCmdbLoading(true);api(`/api/cmdb/assets/${id}`).then(value=>{if(active)setCmdb(value)}).catch(()=>{if(active)setCmdb(null)}).finally(()=>{if(active)setCmdbLoading(false)});return()=>{active=false}},[a?.sourceSystem,id])
 const nodeNames=useMemo(()=>Object.fromEntries((cmdb?.nodes||[]).map(x=>[x.id,x.label||x.className||x.id])),[cmdb])
 if(loading&&!a)return <div className="loading">{t('loading')}</div>;if(!a)return null
 const products=(a.installedProducts||'').split(',').map(x=>x.trim()).filter(Boolean)
 const configurationItemType=a.cmdbClassKey?cmdbClassLabel(t,a.cmdbClassKey,a.cmdbClassName):assetTypeLabel(t,a.assetType)
 const cols=[
  {key:'cveId',label:t('cve'),render:r=><div className="cell-main"><b className="mono linkish">{r.cveId}</b><small>{pick(r)}</small></div>},
  {key:'severity',label:t('severity'),render:r=><StatusBadge tone={severityTone(r.severity)}>{severityLabel(t,r.severity)}</StatusBadge>},
  {key:'riskScore',label:t('riskScore')},
  {key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{findingStatus(t,r.status)}</StatusBadge>},
  {key:'remediationTaskId',label:t('remediationTask'),render:r=>r.remediationTaskId?<button className="text-button mono" onClick={e=>{e.stopPropagation();nav(`/tasks/${r.remediationTaskId}`)}}>{r.remediationTaskNo||`RMD · ${r.remediationTaskId}`}</button>:'—'},
  {key:'lastSeenAt',label:t('lastSeen'),render:r=>fmtDate(r.lastSeenAt,lang)}
 ]
 return <>
  <div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions"><button className="btn primary" onClick={()=>nav(`/scans?asset=${a.id}`)}><Radar size={15}/>{t('scanThisAsset')}</button><button className="btn" onClick={()=>nav(`/automation/batch?asset=${a.id}`)}><Network size={15}/>{t('batchPatch')}</button></div></div>
  <section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={a.environment==='PROD'?'purple':'gray'}>{envLabel(t,a.environment)}</StatusBadge><StatusBadge tone="blue">{configurationItemType}</StatusBadge><StatusBadge tone={a.agentStatus==='ONLINE'?'ok':'critical'}>{t(String(a.agentStatus).toLowerCase())}</StatusBadge>{a.internetExposed&&<StatusBadge tone="critical">{t('internetExposure')}</StatusBadge>}</div><h1>{localize(a.name)}</h1><h2>{a.assetCode} · {a.hostname||a.ipAddress}</h2></div><div className="score-box"><b>{a.openFindings}</b><span>{t('openVulnerabilities')}</span></div></section>
  <div className="detail-grid">
   <section className="panel"><div className="panel-head"><h2>{t('assetProfile')}</h2></div><div className="kv-grid"><div><span>{t('assetCode')}</span><b className="mono">{a.assetCode}</b></div><div><span>{t('configurationItemType')}</span><b>{configurationItemType}</b></div><div><span>{t('ipAddress')}</span><b className="mono">{a.ipAddress||'—'}</b></div><div><span>{t('networkSegment')}</span><b className="mono">{a.networkSegment||'—'}</b></div><div><span>{t('os')}</span><b>{[a.osName,a.osVersion].filter(Boolean).join(' ')||'—'}</b></div><div><span>{t('zone')}</span><b>{localize(a.zone)||'—'}</b></div><div><span>{t('assetStatus')}</span><b>{lang==='zh'&&a.cmdbState?a.cmdbState:(a.cmdbEnabled===false?t('disabled'):t('enabled'))}</b></div><div><span>{t('dataUpdatedAt')}</span><b>{fmtDate(a.cmdbSyncedAt||a.lastSeenAt,lang)}</b></div></div></section>
   <section className="panel"><div className="panel-head"><h2>{t('ownershipAndPatch')}</h2></div><div className="kv-grid"><div><span>{t('businessService')}</span><b>{localize(a.businessService)}</b></div><div><span>{t('owner')}</span><b>{localize(a.ownerName)}</b></div><div><span>{t('baseline')}</span><b>{a.patchBaseline||'—'}</b></div><div><span>{t('maintenanceWindow')}</span><b>{localize(a.maintenanceWindow)||'—'}</b></div></div><div className="panel-head top-border"><h2>{t('installedSoftware')}</h2></div><div className="panel-body tag-list">{products.length?products.map(p=><StatusBadge key={p} tone="gray">{p}</StatusBadge>):<span className="muted">{t('noData')}</span>}</div></section>
   {a.sourceSystem==='CMDB'&&<section className="panel span-2"><div className="panel-head"><h2><Network size={16}/>{t('assetTopology')}</h2><span>{cmdb?.nodes?.length||0}</span></div>{cmdbLoading?<div className="loading compact">{t('loading')}</div>:<div className="cmdb-topology"><div className="cmdb-node-grid">{(cmdb?.nodes||[]).map(node=><button key={node.id} disabled={!node.localAssetId} onClick={()=>node.localAssetId&&nav(`/assets/${node.localAssetId}`)}><b>{node.label||node.id}</b><small>{cmdbClassLabel(t,node.classKey,node.className)}</small></button>)}</div><div className="cmdb-edge-list">{(cmdb?.edges||[]).map(edge=><div key={edge.id||`${edge.source}-${edge.target}`}><span>{nodeNames[edge.source]||edge.source}</span><i>→</i><span>{nodeNames[edge.target]||edge.target}</span><small>{edge.label||edge.category||t('relation')}</small></div>)}</div>{!cmdbLoading&&!(cmdb?.nodes||[]).length&&<div className="empty-row">{t('noTopology')}</div>}</div>}</section>}
   {a.sourceSystem==='CMDB'&&<section className="panel span-2"><div className="panel-head"><h2>{t('configurationItemProperties')}</h2><span>{cmdb?.properties?.length||0}</span></div><div className="cmdb-property-grid">{(cmdb?.properties||[]).map(property=><div key={property.key}><span>{lang==='zh'?property.label:property.key}</span><b>{property.value}</b>{lang==='zh'&&<small className="mono">{property.key}</small>}</div>)}</div></section>}
   <section className="panel span-2"><div className="panel-head"><h2><ShieldAlert size={16}/>{t('assetFindings')}</h2><span>{findings.length}</span></div><DataTable columns={cols} rows={findings} onRowClick={r=>nav(`/vulnerabilities/findings/${r.id}`)} pageSize={10}/></section>
  </div>
 </>
}
