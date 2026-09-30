import React,{useMemo,useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {ShieldAlert,AlertTriangle,ClipboardCheck,ScanSearch,Workflow,Gauge,Server,ClockAlert,Timer,Radar} from 'lucide-react'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import PageHeader from '../components/PageHeader'
import StatusBadge,{severityTone,statusTone} from '../components/StatusBadge'
import {envLabel,severityLabel,runStatus} from '../utils/format'

function TrendChart({rows,t}){
 const width=760,height=190,pad=26,max=Math.max(1,...rows.map(x=>x.backlog))
 const points=rows.map((x,i)=>`${pad+(width-pad*2)*(i/Math.max(1,rows.length-1))},${height-pad-(height-pad*2)*(x.backlog/max)}`).join(' ')
 return <div className="trend-chart"><svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label={t('riskTrend')}>
  {[0,.25,.5,.75,1].map(v=><line key={v} x1={pad} x2={width-pad} y1={pad+(height-pad*2)*v} y2={pad+(height-pad*2)*v}/>)}
  <polyline points={points}/>{rows.map((x,i)=><circle key={x.date} cx={pad+(width-pad*2)*(i/Math.max(1,rows.length-1))} cy={height-pad-(height-pad*2)*(x.backlog/max)} r="3"><title>{x.date}: {x.backlog}</title></circle>)}
 </svg><div className="trend-axis"><span>{rows[0]?.date}</span><b>{t('openFindings')} · {rows.at(-1)?.backlog??0}</b><span>{rows.at(-1)?.date}</span></div></div>
}

export default function DashboardPage(){
 const {t,pick}=useI18n();const nav=useNavigate();const [days,setDays]=useState(14);const {data:d,loading}=useApiData('/api/dashboard',{poll:20000})
 const trend=useMemo(()=>d?.riskTrend?.slice(-days)||[],[d,days])
 if(loading&&!d)return <div className="loading">{t('loading')}</div>
 const metrics=[
  ['openCritical',d?.openCritical,ShieldAlert,'critical','/vulnerabilities/findings?severity=CRITICAL'],['openHigh',d?.openHigh,AlertTriangle,'high','/vulnerabilities/findings?severity=HIGH'],
  ['totalAssets',d?.totalAssets,Server,'blue','/assets'],['slaOverdue',d?.slaOverdue,ClockAlert,'critical','/work-orders/incidents'],
  ['pendingApprovals',d?.pendingApprovals,ClipboardCheck,'purple','/approvals'],['runningScans',d?.runningScans,ScanSearch,'blue','/scans'],
  ['runningAutomations',d?.runningAutomations,Workflow,'purple','/automation'],['patchCompliance',`${d?.patchCompliance??0}%`,Gauge,'ok','/reports'],
  ['mttrHours',`${d?.mttrHours??0}h`,Timer,'high','/reports'],['scanCoverage',`${d?.scanCoverage??0}%`,Radar,'ok','/scans']
 ]
 return <><PageHeader title={t('dashboard')}/><div className="metric-grid">{metrics.map(([k,v,Icon,tone,to])=><button className="metric-card" key={k} onClick={()=>nav(to)}><div className={`metric-icon tone-${tone}`}><Icon size={19}/></div><div><span>{t(k)}</span><b>{v??0}</b></div></button>)}</div>
 <div className="dashboard-grid">
  <section className="panel span-3"><div className="panel-head"><h2>{t('riskTrend')}</h2><select value={days} onChange={e=>setDays(Number(e.target.value))}><option value="7">7 {t('days')}</option><option value="14">14 {t('days')}</option><option value="30">30 {t('days')}</option></select></div><TrendChart rows={trend} t={t}/></section>
  <section className="panel span-2"><div className="panel-head"><h2>{t('topFindings')}</h2></div><div className="compact-list">{d?.topFindings?.map(f=>{const age=Math.max(0,Math.floor((Date.now()-new Date(f.firstSeenAt).getTime())/86400000));const remaining=f.slaDueAt?Math.ceil((new Date(f.slaDueAt).getTime()-Date.now())/86400000):null;return <button className="compact-row" key={f.id} onClick={()=>nav(`/vulnerabilities/findings/${f.id}`)}><div className="compact-main"><b>{f.cveId} · {pick(f)}</b><span>{f.assetName} · {f.businessService} · {t('agingDays',age)}{remaining!==null?` · ${remaining<0?t('slaOverdueDays',Math.abs(remaining)):t('slaRemainingDays',remaining)}`:''}{f.internetExposed?` · ${t('internetExposure')}`:''}</span></div><StatusBadge tone={severityTone(f.severity)}>{severityLabel(t,f.severity)}</StatusBadge><strong>{f.riskScore}</strong></button>})}</div></section>
  <section className="panel"><div className="panel-head"><h2>{t('recentScans')}</h2></div><div className="compact-list">{d?.recentScans?.map(s=><button className="compact-row two" key={s.id} onClick={()=>nav(`/scans/${s.id}`)}><div className="compact-main"><b>{s.name}</b><span>{s.jobNo}</span></div><StatusBadge tone={statusTone(s.status)}>{t(String(s.status).toLowerCase())}</StatusBadge></button>)}</div></section>
  <section className="panel span-3"><div className="panel-head"><h2>{t('recentRuns')}</h2></div><div className="run-list">{d?.recentRuns?.map(r=><button className="run-row" key={r.id} onClick={()=>nav(`/automation/runs/${r.id}`)}><div><b>{r.runNo}</b><span>{pick(r,'templateNameZh','templateNameEn')}</span></div><span>{envLabel(t,r.environment)}</span><span>{r.ring}</span><div className="progress-cell"><div className="progress"><i style={{width:`${r.progress}%`}}/></div><small>{r.progress}%</small></div><StatusBadge tone={statusTone(r.status)}>{runStatus(t,r.status)}</StatusBadge></button>)}</div></section>
 </div></>
}
