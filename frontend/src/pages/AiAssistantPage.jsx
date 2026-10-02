import React,{useMemo,useState} from 'react'
import {Bot,Send,Sparkles} from 'lucide-react'
import {api} from '../api/client'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import {useToast} from '../components/ToastContext'
import PageHeader from '../components/PageHeader'

function ChartCard({chart}){
 const labels=Array.from(chart?.labels||[]),rawSeries=chart?.series||[]
 const series=Array.isArray(rawSeries)&&rawSeries.length&&typeof rawSeries[0]==='object'&&rawSeries[0]?.data?rawSeries:[{name:chart?.title||'',data:Array.from(rawSeries||[])}]
 const values=series.flatMap(s=>Array.from(s.data||[]).map(Number).filter(Number.isFinite)),max=Math.max(1,...values)
 if(!labels.length||!values.length)return <pre className="ai-json">{JSON.stringify(chart,null,2)}</pre>
 if(chart.type==='line'){
  const colors=['#6941F5','#1FA971','#E77922'];
  return <div className="ai-chart"><h3>{chart.title}</h3><svg viewBox="0 0 640 210" role="img">{[0,1,2,3].map(i=><line key={i} x1="28" x2="620" y1={30+i*48} y2={30+i*48} stroke="#ECEEF4"/>)}{series.map((s,si)=>{const data=Array.from(s.data||[]);const points=data.map((v,i)=>`${28+(i*Math.max(1,592/Math.max(1,data.length-1)))},${180-(Number(v)/max)*145}`).join(' ');return <polyline key={s.name||si} points={points} fill="none" stroke={colors[si%colors.length]} strokeWidth="3"/>})}</svg><div className="ai-chart-legend">{series.map((s,i)=><span key={s.name||i}>{s.name}</span>)}</div></div>
 }
 return <div className="ai-chart"><h3>{chart.title}</h3><div className="ai-bars">{labels.map((label,i)=>{const value=Number(series[0]?.data?.[i]||0);return <div key={String(label)}><span>{label}</span><i><b style={{width:`${Math.max(2,value/max*100)}%`}}/></i><strong>{value}</strong></div>})}</div></div>
}

export default function AiAssistantPage(){
 const {t}=useI18n(),toast=useToast();const {data:status={}}=useApiData('/api/ai/status',{initial:{}})
 const [question,setQuestion]=useState(''),[busy,setBusy]=useState(false),[conversationId,setConversationId]=useState(null),[messages,setMessages]=useState([])
 const charts=useMemo(()=>messages.flatMap(m=>{const v=m.visualization;if(Array.isArray(v))return v;if(Array.isArray(v?.charts))return v.charts;return v?[v]:[]}),[messages])
 const submit=async e=>{e?.preventDefault();const text=question.trim();if(!text||busy)return;const userMessage={role:'user',content:text};setMessages(v=>[...v,userMessage]);setQuestion('');setBusy(true);try{const history=[...messages,userMessage].map(m=>({role:m.role,content:m.content}));const result=await api('/api/ai/chat',{method:'POST',timeout:125000,body:{message:text,conversationId,history}});setConversationId(result.conversationId);setMessages(v=>[...v,{role:'assistant',content:String(result.answer||''),visualization:result.visualization,source:result.source}])}catch(e){toast.push(e.message||t('aiRequestFailed'),'red');setMessages(v=>[...v,{role:'error',content:e.message||t('aiRequestFailed')}])}finally{setBusy(false)}}
 return <>
  <PageHeader title={t('aiDataAssistant')} subtitle={t('aiDataAssistantSubtitle')}><span className={`ai-mode ${status.configured?'ok':'warn'}`}>{status.mode==='live'?t('liveAgent'):t('developmentMode')}</span></PageHeader>
  <div className="ai-workspace"><section className="panel ai-conversation"><div className="ai-capabilities"><Bot/><div><b>{t('aiReadOnlyBoundary')}</b><span>{t('aiReadOnlyBoundaryHint')}</span></div></div><div className="ai-messages">{!messages.length&&<div className="ai-empty"><Sparkles/><h2>{t('askAboutSystemData')}</h2><p>{t('aiQuestionExamples')}</p></div>}{messages.map((m,i)=><div key={i} className={`ai-message ${m.role}`}><b>{m.role==='user'?t('you'):m.role==='assistant'?t('companyAgent'):t('error')}</b><p>{m.content}</p></div>)}{busy&&<div className="ai-message assistant"><b>{t('companyAgent')}</b><p>{t('analyzingSystemData')}</p></div>}</div><form className="ai-composer" onSubmit={submit}><textarea rows={3} value={question} onChange={e=>setQuestion(e.target.value)} placeholder={t('aiQuestionPlaceholder')}/><button className="btn primary" disabled={busy||!question.trim()}><Send size={15}/>{t('send')}</button></form></section><section className="ai-report-column"><div className="panel-head standalone"><h2>{t('generatedVisualReports')}</h2><span>{charts.length}</span></div>{charts.length?charts.map((chart,i)=><ChartCard key={i} chart={chart}/>):<div className="panel ai-report-empty">{t('visualReportPlaceholder')}</div>}</section></div>
 </>
}
