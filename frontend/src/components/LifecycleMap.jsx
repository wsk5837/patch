import React from 'react'
import {Check,Clock3,ExternalLink} from 'lucide-react'
import {useI18n} from '../contexts/I18nContext'

const nodes=[
 {key:'finding',label:'lifecycleFinding',x:110,y:55,rank:0},{key:'incident',label:'lifecycleIncident',x:315,y:55,rank:1},{key:'ASSIGNED',label:'lifecycleAssigned',x:520,y:55,rank:2},
 {key:'TEST_PATCH',label:'lifecycleTestPatch',x:520,y:175,rank:3},{key:'APP_VERIFY',label:'lifecycleAppVerify',x:725,y:175,rank:4},{key:'TEST_RESCAN',label:'lifecycleTestRescan',x:930,y:175,rank:5},
 {key:'RELEASE_APPROVAL',label:'lifecycleChangeApproval',x:930,y:295,rank:6},{key:'PREPROD_PATCH',label:'lifecyclePreprod',x:725,y:295,rank:7},{key:'PROD_PATCH',label:'lifecycleProduction',x:520,y:295,rank:8},
 {key:'PROD_RESCAN',label:'lifecycleProdRescan',x:520,y:415,rank:8},{key:'CLOSED',label:'lifecycleClosed',x:315,y:415,rank:9}
]
const rank={ASSIGNED:2,TEST_PATCH:3,APP_VERIFY:4,TEST_RESCAN:5,RELEASE_APPROVAL:6,PREPROD_PATCH:7,PREPROD_VERIFY:7,PREPROD_RESCAN:7,PROD_PATCH:8,PROD_VERIFY:8,PROD_RESCAN:8,CLOSED:9}

export default function LifecycleMap({task,onNavigate}){
 const {t}=useI18n(),current=rank[task.stage]??2
 const pathFor=key=>key==='finding'?task.findingId&&`/vulnerabilities/findings/${task.findingId}`:key==='incident'?task.securityIncidentId&&`/work-orders/incidents/${task.securityIncidentId}`:key==='RELEASE_APPROVAL'?(task.approvalId?`/approvals/${task.approvalId}`:task.changeOrderId&&`/work-orders/changes/${task.changeOrderId}`):['TEST_PATCH','APP_VERIFY','TEST_RESCAN','PREPROD_PATCH','PROD_PATCH','PROD_RESCAN'].includes(key)&&task.latestRunId?`/automation/runs/${task.latestRunId}`:null
 return <div className="lifecycle-bpmn" aria-label={t('lifecycleOverview')}>
  <div className="bpmn-lane" style={{top:0}}><b>{t('laneIdentify')}</b></div><div className="bpmn-lane" style={{top:120}}><b>{t('laneTest')}</b></div><div className="bpmn-lane" style={{top:240}}><b>{t('laneRelease')}</b></div><div className="bpmn-lane" style={{top:360}}><b>{t('laneClose')}</b></div>
  <svg viewBox="0 0 1180 500" preserveAspectRatio="none" className="bpmn-lines" aria-hidden="true"><defs><marker id="flowArrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z"/></marker><marker id="loopArrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z"/></marker></defs><path d="M238 86H315M443 86H520M584 112V175M648 206H725M853 206H930M994 232V295M930 326H853M725 326H648M584 352V415M520 446H443"/><path className="bpmn-loop" d="M994 215H1080V150H725V175"/><path className="bpmn-loop" d="M725 309H1110V270H725V295"/><path className="bpmn-loop" d="M584 429H1110V365H520V415"/></svg>
  <div className="bpmn-gateway" style={{left:1070,top:188}}><span>?</span><small>{t('testPassed')}</small></div><div className="bpmn-gateway" style={{left:1070,top:302}}><span>?</span><small>{t('preprodPassed')}</small></div><div className="bpmn-gateway" style={{left:1070,top:408}}><span>?</span><small>{t('productionPassed')}</small></div>
  <span className="bpmn-branch-label test">{t('failedReturn')}</span><span className="bpmn-branch-label preprod">{t('failedReturn')}</span><span className="bpmn-branch-label prod">{t('failedReturn')}</span>
  {nodes.map(node=>{const state=node.rank<current?'done':node.rank===current?'current':'future',path=pathFor(node.key);return <button key={node.key} style={{left:node.x,top:node.y}} className={`bpmn-node ${state}`} onClick={()=>path&&onNavigate(path)} disabled={!path}><span>{state==='done'?<Check/>:state==='current'?<Clock3/>:<i/>}</span><b>{t(node.label)}</b><small>{state==='done'?t('completed'):state==='current'?t('currentStage'):t('notStarted')}</small>{path&&<ExternalLink className="bpmn-link"/>}</button>})}
 </div>
}
