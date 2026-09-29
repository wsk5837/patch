import React from 'react'
import { ArrowLeft, ExternalLink, Download, ShieldCheck, Terminal } from 'lucide-react'
import { useNavigate,useParams } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import DataTable from '../components/DataTable'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {envLabel,findingStatus,severityLabel,fmtDate} from '../utils/format'

export default function PatchDetailPage(){
 const {id}=useParams();const nav=useNavigate();const {t,pick,lang}=useI18n()
 const {data:p,loading}=useApiData(`/api/patches/${id}`)
 const {data:findings=[]}=useApiData(`/api/patches/${id}/findings`,{initial:[]})
 if(loading&&!p)return <div className="loading">{t('loading')}</div>;if(!p)return null
 const columns=[
  {key:'severity',label:t('severity'),render:r=><StatusBadge tone={severityTone(r.severity)}>{severityLabel(t,r.severity)}</StatusBadge>},
  {key:'cveId',label:t('cve'),render:r=><span className="mono linkish">{r.cveId}</span>},
  {key:'assetName',label:t('asset'),render:r=><div className="cell-main"><b>{r.assetName}</b><small>{r.assetCode} · {envLabel(t,r.environment)}</small></div>},
  {key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{findingStatus(t,r.status)}</StatusBadge>},
  {key:'ownerName',label:t('owner')},{key:'lastSeenAt',label:t('lastSeen'),render:r=>fmtDate(r.lastSeenAt,lang)}
 ]
 return <>
  <div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button><div className="detail-actions">{p.vendorAdvisoryUrl&&<a className="btn" href={p.vendorAdvisoryUrl} target="_blank" rel="noreferrer"><ExternalLink size={15}/>{t('vendorAdvisory')}</a>}{p.downloadUrl&&<a className="btn primary" href={p.downloadUrl} target="_blank" rel="noreferrer"><Download size={15}/>{t('downloadPackage')}</a>}</div></div>
  <section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone={statusTone(p.status)}>{p.status}</StatusBadge><StatusBadge tone={p.signatureStatus==='VERIFIED'?'ok':'warn'}>{t('signature')} · {p.signatureStatus}</StatusBadge></div><h1>{p.patchId}</h1><h2>{pick(p)} · {p.vendor} {p.product} {p.version||''}</h2></div><div className="score-box"><b>{p.affectedAssets}</b><span>{t('affectedAssets')}</span></div></section>
  <div className="detail-grid">
   <section className="panel span-2"><div className="kv-grid three"><div><span>{t('vendor')}</span><b>{p.vendor}</b></div><div><span>{t('product')}</span><b>{p.product}</b></div><div><span>{t('version')}</span><b>{p.version||'—'}</b></div><div><span>{t('source')}</span><b>{p.source}</b></div><div><span>{t('publishedDate')}</span><b>{p.publishedDate||'—'}</b></div><div><span>{t('size')}</span><b>{p.sizeMb?`${p.sizeMb} MB`:'—'}</b></div><div><span>{t('rebootRequired')}</span><b>{p.rebootRequired?t('yes'):t('no')}</b></div><div><span>{t('supersedes')}</span><b>{p.supersedes||'—'}</b></div><div><span>{t('checksum')}</span><b className="mono">{p.checksum||'—'}</b></div></div></section>
   <section className="panel"><div className="panel-head"><h2>{t('cveMappings')}</h2></div><div className="chip-list">{p.cves?.map(c=><button className="chip chip-button" key={c} onClick={()=>nav(`/vulnerabilities/library/${c}`)}>{c}<ExternalLink size={11}/></button>)}</div></section>
   <section className="panel"><div className="panel-head"><h2>{t('applicability')}</h2></div><div className="panel-body"><p className="text-block">{p.applicabilityRule||'—'}</p></div></section>
   <section className="panel span-2"><div className="panel-head"><h2>{t('releaseNotes')}</h2></div><div className="panel-body"><p className="text-block">{lang==='zh'?p.releaseNotesZh:p.releaseNotesEn}</p></div></section>
   <section className="panel span-2"><div className="panel-head"><h2><ShieldCheck size={17}/>{t('packageEvidence')}</h2></div><div className="kv-grid three"><div><span>{t('signatureIssuer')}</span><b>{p.signatureIssuer||'—'}</b></div><div><span>{t('signatureFingerprint')}</span><b className="mono wrap-anywhere">{p.signatureFingerprint||'—'}</b></div><div><span>{t('integrityVerifiedAt')}</span><b>{fmtDate(p.integrityVerifiedAt,lang)}</b></div><div><span>{t('checksum')}</span><b className="mono wrap-anywhere">{p.checksum||'—'}</b></div><div><span>{t('sourceType')}</span><b>{p.source}</b></div><div><span>{t('packageStatus')}</span><b>{p.status}</b></div></div><div className="evidence-result"><b>{t('validationEvidence')}</b><pre>{p.testEvidence||'—'}</pre></div></section>
   <section className="panel"><div className="panel-head"><h2>{t('prerequisites')}</h2></div><div className="panel-body"><p className="text-block">{p.prerequisites||'—'}</p></div></section>
   <section className="panel"><div className="panel-head"><h2>{t('knownIssues')}</h2></div><div className="panel-body"><p className="text-block">{p.knownIssues||'—'}</p></div></section>
   <section className="panel span-2"><div className="panel-head"><h2><Terminal size={17}/>{t('executionCommands')}</h2></div><div className="command-grid"><div><span>{t('installCommand')}</span><code>{p.installCommand||'—'}</code></div><div><span>{t('rollbackCommand')}</span><code>{p.uninstallCommand||'—'}</code></div></div></section>
   <section className="panel span-2"><div className="panel-head"><h2>{t('affectedFindings')}</h2><span className="toolbar-count">{findings.length}</span></div><DataTable columns={columns} rows={findings} onRowClick={r=>nav(`/vulnerabilities/findings/${r.id}`)}/></section>
  </div>
 </>
}
