import React from 'react'
import {RefreshCw,Download,Library,ShieldCheck,Ban,ClipboardList,Workflow,Gauge,ClockAlert,ShieldOff,Rocket} from 'lucide-react'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import {useAuth} from '../contexts/AuthContext'
import PageHeader from '../components/PageHeader'
import {severityLabel,envLabel,deploymentStatus} from '../utils/format'

const csv=value=>`"${String(value??'').replaceAll('"','""')}"`

export default function ReportsPage(){
 const {t}=useI18n();const {has}=useAuth()
 const {data:d,loading,reload}=useApiData('/api/dashboard/report')
 const deploymentDistribution=d?.deploymentStatusDistribution||{}
 if(loading&&!d)return <div className="loading">{t('loading')}</div>
 const metrics=[['reportTotalLibrary',d?.totalLibrary,Library],['openFindings',d?.openFindings,ShieldCheck],['reportResolved',d?.resolvedFindings,ShieldCheck],['reportExemptions',d?.exemptions,ShieldOff],['slaOverdue',d?.slaOverdue,ClockAlert],['reportOpenTasks',d?.openTasks,ClipboardList],['runningDeployments',d?.runningDeployments,Rocket],['failedDeployments',d?.failedDeployments,Ban],['automationSuccessRate',`${d?.automationSuccessRate??0}%`,Workflow],['patchCompliance',`${d?.patchCompliance??0}%`,Gauge]]
 const maxSev=Math.max(1,...Object.values(d?.severityDistribution||{})),maxEnv=Math.max(1,...Object.values(d?.environmentDistribution||{})),maxDeploy=Math.max(1,...Object.values(deploymentDistribution))
 const automation={SUCCEEDED:d?.automationSucceeded||0,OTHER:Math.max(0,(d?.automationRuns||0)-(d?.automationSucceeded||0))},maxAutomation=Math.max(1,...Object.values(automation))
 const exportReport=()=>{
  const rows=[[t('metric'),t('value')],...metrics.map(([key,value])=>[t(key),value]),[],[t('severityDistribution'),''],...Object.entries(d?.severityDistribution||{}).map(([k,v])=>[severityLabel(t,k),v]),[],[t('environmentDistribution'),''],...Object.entries(d?.environmentDistribution||{}).map(([k,v])=>[envLabel(t,k),v]),[],[t('deploymentStatusDistribution'),''],...Object.entries(deploymentDistribution).map(([k,v])=>[deploymentStatus(t,k),v])]
  const blob=new Blob(['\ufeff'+rows.map(r=>r.map(csv).join(',')).join('\n')],{type:'text/csv;charset=utf-8'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`gazellio-report-${new Date().toISOString().slice(0,10)}.csv`;a.click();URL.revokeObjectURL(url)
 }
 const chart=(title,items,max,label)=><section className="panel"><div className="panel-head"><h2>{title}</h2></div><div className="bar-chart">{Object.entries(items).map(([k,v])=><div className="bar-line" key={k}><label>{label(k)}</label><div className="bar-track"><i style={{width:`${v/max*100}%`}}/></div><b>{v}</b></div>)}</div></section>
 return <>
  <PageHeader title={t('reports')}><button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button>{has('REPORT_EXPORT')&&<button className="btn primary" onClick={exportReport}><Download size={15}/>{t('exportReport')}</button>}</PageHeader>
  <div className="metric-grid report-metrics">{metrics.map(([k,v,Icon])=><div className="metric-card static" key={k}><div className="metric-icon tone-purple"><Icon size={19}/></div><div><span>{t(k)}</span><b>{v}</b></div></div>)}</div>
  <div className="report-grid report-grid-four">
   {chart(t('severityDistribution'),d?.severityDistribution||{},maxSev,k=>severityLabel(t,k))}
   {chart(t('environmentDistribution'),d?.environmentDistribution||{},maxEnv,k=>envLabel(t,k))}
   {chart(t('deploymentStatusDistribution'),deploymentDistribution,maxDeploy,k=>deploymentStatus(t,k))}
   {chart(t('automationRunDistribution'),automation,maxAutomation,k=>t(k==='SUCCEEDED'?'successfulRuns':'otherRuns'))}
  </div>
 </>
}
