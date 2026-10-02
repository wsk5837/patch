import React,{useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {useI18n} from '../contexts/I18nContext'
import StatusBadge,{severityTone,statusTone} from './StatusBadge'
import {severityLabel,findingStatus} from '../utils/format'

export default function WorkOrderInsightTabs({record,overview}){
 const {t,pick,localize}=useI18n(),nav=useNavigate(),[tab,setTab]=useState('overview')
 const items=record?.linkedVulnerabilities||[],decision=record?.slaDecision
 const reasonLabel=value=>t(`slaReason_${value}`)
 return <section className="panel span-2 workorder-insights">
  <div className="tabs detail-tabs"><button className={tab==='overview'?'active':''} onClick={()=>setTab('overview')}>{t('workOrderInfo')}</button><button className={tab==='vulnerabilities'?'active':''} onClick={()=>setTab('vulnerabilities')}>{t('linkedVulnerabilities')}<span>{items.length}</span></button><button className={tab==='sla'?'active':''} onClick={()=>setTab('sla')}>{t('slaDecision')}</button></div>
  {tab==='overview'&&overview}
  {tab==='vulnerabilities'&&<div className="linked-vulnerability-list">{items.map(v=><button key={v.findingId} onClick={()=>nav(`/vulnerabilities/findings/${v.findingId}`)}><div><b className="mono">{v.cveId}</b><strong>{pick(v)}</strong><small>{localize(v.assetName)} · {v.assetCode}</small></div><div><StatusBadge tone={severityTone(v.severity)}>{severityLabel(t,v.severity)}</StatusBadge><StatusBadge tone={statusTone(v.findingStatus)}>{findingStatus(t,v.findingStatus)}</StatusBadge></div><p>{localize(v.descriptionZh)||localize(v.descriptionEn)||'—'}</p></button>)}</div>}
  {tab==='sla'&&decision&&<div className="sla-matrix"><div className="sla-axis"><span>{t('threatDimension')}</span><b>{decision.threatLevel}</b><small>{t(`slaThreat_${decision.threatLevel}`)}</small></div><i>×</i><div className="sla-axis"><span>{t('assetDimension')}</span><b>{decision.assetLevel}</b><small>{t(`slaAsset_${decision.assetLevel}`)}</small></div><i>×</i><div className="sla-axis"><span>{t('exposureDimension')}</span><b>{decision.exposureLevel}</b><small>{t(`slaExposure_${decision.exposureLevel}`)}</small></div><em>→</em><div className="sla-outcome"><span>{t('remediationPriority')}</span><b>{decision.priority}</b><strong>{t('completeWithinDays',decision.slaDays)}</strong></div><div className="sla-rationale"><span>{t('priorityBasis')}</span>{decision.reasons?.map(r=><small key={r}>{reasonLabel(r)}</small>)}</div></div>}
 </section>
}
