import React from 'react'
import {RefreshCw,Download,ClockAlert,ShieldOff,Globe2,PackageX} from 'lucide-react'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import {useAuth} from '../contexts/AuthContext'
import PageHeader from '../components/PageHeader'
import {severityLabel,envLabel,deploymentStatus,fmtDate} from '../utils/format'
import StatusBadge from '../components/StatusBadge'

const csv=value=>`"${String(value??'').replaceAll('"','""')}"`

export default function ReportsPage(){
 const {t,lang,localize}=useI18n();const {has}=useAuth()
 const {data:d,loading,reload}=useApiData('/api/dashboard/report')
 const deploymentDistribution=d?.deploymentStatusDistribution||{}
 if(loading&&!d)return <div className="loading">{t('loading')}</div>
 const gauges=[['patchCompliance',d?.patchCompliance??0,'purple'],['slaComplianceRate',d?.slaComplianceRate??0,'green'],['automationSuccessRate',d?.automationSuccessRate??0,'blue']]
 const metrics=[['slaOverdue',d?.slaOverdue,ClockAlert,'critical'],['findingsWithoutPatch',d?.findingsWithoutPatch,PackageX,'high'],['criticalInternetExposed',d?.criticalInternetExposed,Globe2,'critical'],['reportExemptions',d?.exemptions,ShieldOff,'blue']]
 const maxSev=Math.max(1,...Object.values(d?.severityDistribution||{})),maxDeploy=Math.max(1,...Object.values(deploymentDistribution))
 const exportReport=()=>{
  const rows=[[t('generatedAt'),d?.generatedAt],[t('reportingWindow'),`${d?.reportingWindowDays} ${t('days')}`],[],[t('metric'),t('value')],...gauges.map(([key,value])=>[t(key),`${value}%`]),...metrics.map(([key,value])=>[t(key),value]),[],[t('severityDistribution'),''],...Object.entries(d?.severityDistribution||{}).map(([k,v])=>[severityLabel(t,k),v]),[],[t('deploymentStatusDistribution'),''],...Object.entries(deploymentDistribution).map(([k,v])=>[deploymentStatus(t,k),v]),[],[t('ownerBacklog'),t('openFindings'),t('slaOverdue')],...(d?.ownerBacklog||[]).map(x=>[x.owner,x.openFindings,x.overdue]),[],[t('riskAssets'),t('assetCode'),t('openFindings'),t('highestRisk')],...(d?.riskAssets||[]).map(x=>[localize(x.assetName),x.assetCode,x.openFindings,x.highestRisk])]
  const blob=new Blob(['\ufeff'+rows.map(r=>r.map(csv).join(',')).join('\n')],{type:'text/csv;charset=utf-8'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`gazellio-report-${new Date().toISOString().slice(0,10)}.csv`;a.click();URL.revokeObjectURL(url)
 }
 const chart=(title,items,max,label)=><section className="panel"><div className="panel-head"><h2>{title}</h2></div><div className="bar-chart">{Object.entries(items).map(([k,v])=><div className="bar-line" key={k}><label>{label(k)}</label><div className="bar-track"><i style={{width:`${v/max*100}%`}}/></div><b>{v}</b></div>)}</div></section>
 const trend=d?.remediationTrend||[],trendMax=Math.max(1,...trend.flatMap(x=>[x.opened,x.resolved,x.backlog])),points=key=>trend.map((x,i)=>`${trend.length===1?0:i/(trend.length-1)*100},${100-x[key]/trendMax*92}`).join(' ')
 return <>
  <PageHeader title={t('reports')}><button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button>{has('REPORT_EXPORT')&&<button className="btn primary" onClick={exportReport}><Download size={15}/>{t('exportReport')}</button>}</PageHeader>
  <section className="report-overview"><div className="report-overview-copy"><span>{t('generatedAt')} · {fmtDate(d?.generatedAt,lang)}</span><h2>{t('reportingWindow')} {d?.reportingWindowDays} {t('days')}</h2><div><b>{d?.totalAssets??0}</b><small>{t('managedAssets')}</small><b>{d?.openFindings??0}</b><small>{t('openFindings')}</small><b>{d?.resolvedFindings??0}</b><small>{t('reportResolved')}</small></div></div><div className="report-gauges">{gauges.map(([key,value,tone])=><div className={`report-gauge tone-${tone}`} style={{'--value':value}} key={key}><i><strong>{value}%</strong></i><span>{t(key)}</span></div>)}</div></section>
  <div className="report-risk-grid">{metrics.map(([k,v,Icon,tone])=><div className={`report-risk-card tone-${tone}`} key={k}><Icon size={19}/><div><span>{t(k)}</span><b>{v??0}</b></div></div>)}</div>
  <div className="report-primary-grid"><section className="panel report-trend"><div className="panel-head"><h2>{t('remediationTrend')}</h2><div className="chart-legend"><span className="opened">{t('opened')}</span><span className="resolved">{t('reportResolved')}</span><span className="backlog">{t('backlog')}</span></div></div><div className="report-line-chart"><svg viewBox="0 0 100 100" preserveAspectRatio="none"><polyline className="backlog" points={points('backlog')}/><polyline className="opened" points={points('opened')}/><polyline className="resolved" points={points('resolved')}/></svg><div><span>{trend[0]?.date||''}</span><b>{t('backlog')}: {trend.at(-1)?.backlog||0}</b><span>{trend.at(-1)?.date||''}</span></div></div></section><div className="report-chart-stack">{chart(t('severityDistribution'),d?.severityDistribution||{},maxSev,k=>severityLabel(t,k))}{chart(t('deploymentStatusDistribution'),deploymentDistribution,maxDeploy,k=>deploymentStatus(t,k))}</div></div>
  <div className="report-table-grid"><section className="panel"><div className="panel-head"><h2>{t('ownerBacklog')}</h2></div><table className="report-table"><thead><tr><th>{t('owner')}</th><th>{t('openFindings')}</th><th>{t('slaOverdue')}</th></tr></thead><tbody>{(d?.ownerBacklog||[]).map(x=><tr key={x.owner}><td>{localize(x.owner)}</td><td>{x.openFindings}</td><td className={x.overdue?'danger-text':''}>{x.overdue}</td></tr>)}</tbody></table></section><section className="panel"><div className="panel-head"><h2>{t('riskAssets')}</h2></div><table className="report-table"><thead><tr><th>{t('asset')}</th><th>{t('environment')}</th><th>{t('openFindings')}</th><th>{t('highestRisk')}</th></tr></thead><tbody>{(d?.riskAssets||[]).map(x=><tr key={x.assetId}><td><b>{localize(x.assetName)}</b><small>{x.assetCode}</small></td><td>{envLabel(t,x.environment)} {x.internetExposed&&<StatusBadge tone="critical">{t('internetExposure')}</StatusBadge>}</td><td>{x.openFindings}</td><td><b>{x.highestRisk}</b></td></tr>)}</tbody></table></section></div>
 </>
}
