import React from 'react'
import { Check, Circle, Loader2, RotateCcw, X } from 'lucide-react'
import { useI18n } from '../contexts/I18nContext'
import { runStepStatus, approvalStepStatus } from '../utils/format'

function iconFor(status){
 if(['SUCCEEDED','APPROVED'].includes(status))return <Check size={14}/>
 if(['RUNNING','PENDING'].includes(status))return <Loader2 size={14} className="spin"/>
 if(['FAILED','REJECTED'].includes(status))return <X size={14}/>
 if(status==='ROLLED_BACK')return <RotateCcw size={14}/>
 return <Circle size={11}/>
}
export function OrchestrationGraph({steps=[],template=false}){
 const {lang,t}=useI18n()
 return <div className="flow-graph">{steps.map((s,i)=>{const st=template?'WAITING':s.status;return <React.Fragment key={s.id||s.code||i}><div className={`flow-node flow-${String(st).toLowerCase()}`}><div className="flow-node-icon">{template?<span>{i+1}</span>:iconFor(st)}</div><div><b>{lang==='zh'?s.nameZh:s.nameEn}</b>{!template&&<small>{runStepStatus(t,st)}</small>}</div></div>{i<steps.length-1&&<div className="flow-edge"><span>→</span></div>}</React.Fragment>})}</div>
}
export function ApprovalGraph({steps=[]}){
 const {lang,t,localize}=useI18n()
 return <div className="flow-graph approval-graph">{steps.map((s,i)=><React.Fragment key={s.id||i}><div className={`flow-node flow-${String(s.status).toLowerCase()}`}><div className="flow-node-icon">{iconFor(s.status)}</div><div><b>{lang==='zh'?s.roleNameZh:s.roleNameEn}</b><small>{localize(s.approverName)||'—'} · {approvalStepStatus(t,s.status)}</small></div></div>{i<steps.length-1&&<div className="flow-edge"><span>→</span></div>}</React.Fragment>)}</div>
}
