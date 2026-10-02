import React from 'react'
import { ArrowLeft, ExternalLink } from 'lucide-react'
import { useNavigate,useParams } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import DataTable from '../components/DataTable'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {fmtDate,scanTypeLabel,scanTargetLabel,severityLabel,findingStatus,envLabel} from '../utils/format'

export default function ScanDetailPage(){
 const {id}=useParams();const nav=useNavigate();const {t,lang,localize}=useI18n()
 const {data:x,loading}=useApiData(`/api/scans/${id}`,{poll:3000})
 const {data:findings=[]}=useApiData(`/api/scans/${id}/findings`,{initial:[],poll:5000})
 if(loading&&!x)return <div className="loading">{t('loading')}</div>;if(!x)return null
 const scanFinished=['COMPLETED','FAILED','CANCELLED'].includes(x.status)||x.progress>=100
 const columns=[
  {key:'severity',label:t('severity'),render:r=><StatusBadge tone={severityTone(r.severity)}>{severityLabel(t,r.severity)}</StatusBadge>},
  {key:'cveId',label:t('cve'),render:r=><span className="mono linkish">{r.cveId}</span>},
  {key:'assetName',label:t('asset'),render:r=><div className="cell-main"><b>{localize(r.assetName)}</b><small>{r.assetCode} · {envLabel(t,r.environment)}</small></div>},
  {key:'riskScore',label:t('riskScore')},{key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{findingStatus(t,r.status)}</StatusBadge>},
  {key:'lastSeenAt',label:t('lastSeen'),render:r=>fmtDate(r.lastSeenAt,lang)}
 ]
 return <><div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions" role="group" aria-label={t('operationArea')}>{x.remediationTaskId&&<button className={scanFinished?'btn primary next-action':'btn'} onClick={()=>nav(`/tasks/${x.remediationTaskId}`)}><ExternalLink size={15}/>{scanFinished?t('returnToTask'):t('remediationTask')}</button>}{x.automationRunId&&<button className={!scanFinished?'btn primary next-action':'btn'} onClick={()=>nav(`/automation/runs/${x.automationRunId}`)}><ExternalLink size={15}/>{t('retestTrace')}</button>}</div></div><section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={statusTone(x.status)}>{t(String(x.status).toLowerCase())}</StatusBadge><StatusBadge tone="purple">{scanTypeLabel(t,x.scanType)}</StatusBadge></div><h1>{x.jobNo}</h1><h2>{localize(x.name)}</h2></div><div className="run-progress-summary"><div><strong>{x.progress}%</strong><span>{x.findingsCount} {t('findings')}</span></div><div className="run-progress-track"><i style={{width:`${x.progress}%`}}/></div></div></section><div className="detail-grid"><section className="panel span-2"><div className="kv-grid three"><div><span>{t('scanType')}</span><b>{scanTypeLabel(t,x.scanType)}</b></div><div><span>{t('targetValue')}</span><b>{scanTargetLabel(t,x,localize)}</b></div><div><span>{t('credentialType')}</span><b>{x.credentialType||'—'}</b></div><div><span>{t('targetCve')}</span><b className="mono">{x.targetCve||'—'}</b></div><div><span>{t('requestedBy')}</span><b>{localize(x.requestedByName)||'—'}</b></div><div><span>{t('createdAt')}</span><b>{fmtDate(x.createdAt,lang)}</b></div><div><span>{t('startedAt')}</span><b>{fmtDate(x.startedAt,lang)}</b></div><div><span>{t('completedAt')}</span><b>{fmtDate(x.completedAt,lang)}</b></div><div><span>{t('errorMessage')}</span><b>{localize(x.errorMessage)||'—'}</b></div></div></section><section className="panel span-2"><div className="panel-head"><h2>{t('scanFindings')}</h2><span className="toolbar-count">{findings.length}</span></div><DataTable columns={columns} rows={findings} onRowClick={r=>nav(`/vulnerabilities/findings/${r.id}`)} empty={t('noData')}/></section></div></>
}
