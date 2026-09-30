import React,{useState} from 'react'
import {Activity,Plus,RefreshCw,Workflow} from 'lucide-react'
import {useNavigate} from 'react-router-dom'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import {useAuth} from '../contexts/AuthContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {envLabel,runStatus,fmtDate,templateType} from '../utils/format'

export default function AutomationPage(){
 const {t,pick,lang}=useI18n(),{has}=useAuth(),nav=useNavigate();const [tab,setTab]=useState('runs');const {data:templates=[],reload:reloadTemplates}=useApiData('/api/automation/templates',{initial:[]});const {data:runs=[],reload:reloadRuns}=useApiData('/api/automation/runs',{initial:[],poll:8000})
 const tplCols=[{key:'code',label:'ID',render:r=><span className="mono linkish">{r.code}</span>},{key:'name',label:t('name'),render:r=><b>{pick(r,'nameZh','nameEn')}</b>},{key:'type',label:t('type'),render:r=>templateType(t,r.type)},{key:'version',label:t('version'),render:r=>`v${r.version}`},{key:'steps',label:t('currentStep'),render:r=>r.steps?.length??0},{key:'enabled',label:t('status'),render:r=><StatusBadge tone={r.enabled?'ok':'gray'}>{r.enabled?t('enabled'):t('disabled')}</StatusBadge>}]
 const runCols=[{key:'runNo',label:t('runNo'),render:r=><span className="mono linkish">{r.runNo}</span>},{key:'templateName',label:t('name'),render:r=><div className="cell-main"><b>{pick(r,'templateNameZh','templateNameEn')}</b><small>{r.templateCode}</small></div>},{key:'taskNo',label:t('taskNo')},{key:'environment',label:t('environment'),render:r=>envLabel(t,r.environment)},{key:'ring',label:t('ring')},{key:'status',label:t('status'),render:r=><StatusBadge tone={statusTone(r.status)}>{runStatus(t,r.status)}</StatusBadge>},{key:'progress',label:t('progress'),render:r=><div className="progress-cell"><div className="progress"><i style={{width:`${r.progress}%`}}/></div><small>{r.progress}%</small></div>},{key:'startedAt',label:t('startedAt'),render:r=>fmtDate(r.startedAt,lang)}]
 return <><PageHeader title={t('automation')}>{tab==='templates'&&has('AUTOMATION_TEMPLATE_EDIT')&&<button className="btn primary" onClick={()=>nav('/automation/templates/new')}><Plus size={15}/>{t('newTemplate')}</button>}<button className="btn" onClick={()=>{reloadTemplates();reloadRuns()}}><RefreshCw size={15}/>{t('refresh')}</button></PageHeader><div className="tabs"><button className={tab==='runs'?'active':''} onClick={()=>setTab('runs')}><Activity size={15}/>{t('executionRecords')}<span>{runs.length}</span></button><button className={tab==='templates'?'active':''} onClick={()=>setTab('templates')}><Workflow size={15}/>{t('templates')}<span>{templates.length}</span></button></div>{tab==='runs'?<DataTable columns={runCols} rows={runs} onRowClick={r=>nav(`/automation/runs/${r.id}`)}/>:<DataTable columns={tplCols} rows={templates} onRowClick={r=>nav(`/automation/templates/${r.id}`)}/>}</>
}
