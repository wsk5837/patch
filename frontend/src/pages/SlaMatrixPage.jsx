import React,{useEffect,useState} from 'react'
import {RefreshCw,Save,ShieldCheck} from 'lucide-react'
import PageHeader from '../components/PageHeader'
import StatusBadge from '../components/StatusBadge'
import {useApiData} from '../utils/useApiData'
import {api} from '../api/client'
import {useI18n} from '../contexts/I18nContext'
import {useAuth} from '../contexts/AuthContext'
import {useToast} from '../components/ToastContext'

const priority=(threat,asset,exposure)=>threat===3||(threat===2&&(asset===3||exposure===2))?'P1':threat===2||asset===3||exposure===2?'P2':threat+asset+exposure>=4?'P3':'P4'
const tone=p=>p==='P1'?'critical':p==='P2'?'high':p==='P3'?'medium':'low'

export default function SlaMatrixPage(){
 const {t}=useI18n(),{has}=useAuth(),toast=useToast(),{data:settings={},reload}=useApiData('/api/settings',{initial:{}})
 const [days,setDays]=useState({slaP1Days:'3',slaP2Days:'7',slaP3Days:'30',slaP4Days:'90'}),[saving,setSaving]=useState(false)
 useEffect(()=>setDays({slaP1Days:settings.slaP1Days||'3',slaP2Days:settings.slaP2Days||'7',slaP3Days:settings.slaP3Days||'30',slaP4Days:settings.slaP4Days||'90'}),[settings])
 const save=async()=>{setSaving(true);try{await api('/api/settings',{method:'PUT',body:{values:days}});await reload();toast.push(t('slaMatrixSaved'))}catch(e){toast.push(e.message||t('operationFailed'),'red')}finally{setSaving(false)}}
 const matrix=exposure=><section className="panel sla-policy-panel"><div className="panel-head"><h2>{t(exposure===2?'internetExposure':'internalExposure')}</h2><span className="matrix-axis-note">{t('columnsThreatRowsAsset')}</span></div><div className="sla-policy-matrix"><div className="matrix-corner"><span>{t('assetDimension')}</span><b>{t('threatDimension')}</b></div>{[1,2,3].map(x=><div className="matrix-col" key={`h${x}`}>T{x}<small>{t(`slaThreat_T${x}`)}</small></div>)}{[3,2,1].flatMap(asset=>[<div className="matrix-row" key={`r${asset}`}>A{asset}<small>{t(`slaAsset_A${asset}`)}</small></div>,...[1,2,3].map(threat=>{const p=priority(threat,asset,exposure);return <div className={`matrix-cell priority-${p.toLowerCase()}`} key={`${asset}-${threat}`}><StatusBadge tone={tone(p)}>{p}</StatusBadge><b>{t('completeWithinDays',days[`sla${p}Days`])}</b></div>})])}</div></section>
 return <><PageHeader title={t('slaMatrixMenu')} subtitle={t('slaMatrixSubtitle')}><button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button>{has('SETTINGS_MANAGE')&&<button className="btn primary" disabled={saving} onClick={save}><Save size={15}/>{t('save')}</button>}</PageHeader><section className="sla-policy-toolbar"><div className="sla-policy-intro"><ShieldCheck/><div><b>{t('slaPriorityMatrix')}</b><span>{t('slaMatrixRule')}</span></div></div><div className="sla-days-editor">{['P1','P2','P3','P4'].map(p=><label key={p}><span>{p}</span><input type="number" min="1" max="3650" disabled={!has('SETTINGS_MANAGE')} value={days[`sla${p}Days`]} onChange={e=>setDays({...days,[`sla${p}Days`]:e.target.value})}/><small>{t('days')}</small></label>)}</div></section><div className="sla-matrix-layout">{matrix(1)}{matrix(2)}</div><div className="sla-matrix-legend"><b>{t('matrixLegend')}</b><span>T1–T3 · {t('threatDimension')}</span><span>A1–A3 · {t('assetDimension')}</span><span>E1–E2 · {t('exposureDimension')}</span></div></>
}
