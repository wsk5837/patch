import React from 'react'
import {Check,ChevronRight,Circle,Clock3} from 'lucide-react'
import {useI18n} from '../contexts/I18nContext'

const stages=[
 ['finding','lifecycleFinding'],['incident','lifecycleIncident'],['ASSIGNED','lifecycleAssigned'],
 ['TEST_PATCH','lifecycleTestPatch'],['APP_VERIFY','lifecycleAppVerify'],['TEST_RESCAN','lifecycleTestRescan'],
 ['RELEASE_APPROVAL','lifecycleChangeApproval'],['PREPROD_PATCH','lifecyclePreprod'],['PROD_PATCH','lifecycleProduction'],['CLOSED','lifecycleClosed']
]
const rank={ASSIGNED:2,TEST_PATCH:3,APP_VERIFY:4,TEST_RESCAN:5,RELEASE_APPROVAL:6,PREPROD_PATCH:7,PREPROD_VERIFY:7,PREPROD_RESCAN:7,PROD_PATCH:8,PROD_VERIFY:8,PROD_RESCAN:8,CLOSED:9}
export default function LifecycleMap({task,onNavigate}){
 const {t}=useI18n(),current=rank[task.stage]??2
 const pathFor=key=>key==='finding'?task.findingId&&`/vulnerabilities/findings/${task.findingId}`:key==='incident'?task.securityIncidentId&&`/work-orders/incidents/${task.securityIncidentId}`:key==='RELEASE_APPROVAL'?(task.approvalId?`/approvals/${task.approvalId}`:task.changeOrderId&&`/work-orders/changes/${task.changeOrderId}`):['TEST_PATCH','APP_VERIFY','TEST_RESCAN','PREPROD_PATCH','PROD_PATCH'].includes(key)&&task.latestRunId?`/automation/runs/${task.latestRunId}`:null
 return <div className="lifecycle-map">{stages.map(([key,label],index)=>{const state=index<current?'done':index===current?'current':'future',path=pathFor(key);return <React.Fragment key={key}><button className={`lifecycle-node ${state}`} onClick={()=>path&&onNavigate(path)} disabled={!path&&index!==current}><span>{state==='done'?<Check/>:state==='current'?<Clock3/>:<Circle/>}</span><b>{t(label)}</b><small>{state==='done'?t('completed'):state==='current'?t('currentStage'):t('notStarted')}</small></button>{index<stages.length-1&&<ChevronRight className={`lifecycle-edge ${index<current?'done':''}`}/>}</React.Fragment>})}</div>
}
