export function fmtDate(v, lang='zh'){
  if(!v) return '—'
  const d=new Date(v); if(Number.isNaN(d.getTime())) return String(v)
  return new Intl.DateTimeFormat(lang==='zh'?'zh-CN':'en-US',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).format(d)
}
export function envLabel(t,v){ return ({PROD:t('production'),TEST:t('test'),PREPROD:t('preprod'),DEV:t('development')})[v]||v||'—' }
export function severityLabel(t,v){return ({CRITICAL:t('critical'),HIGH:t('high'),MEDIUM:t('medium'),LOW:t('low')})[v]||v||'—'}
export function findingStatus(t,v){return t(`finding_${v}`)}
export function taskStatus(t,v){return t(`task_${v}`)}
export function taskStage(t,v){return t(`stage_${v}`)}
export function approvalStatus(t,v){return t(`approval_${v}`)}
export function approvalStepStatus(t,v){return t(`step_${v}`)}
export function runStatus(t,v){return t(`run_${v}`)}
export function runStepStatus(t,v){return t(`step_${v}`)}
export function incidentStatus(t,v){const x=t(`incident_${v}`);return x===`incident_${v}`?(v||'—'):x}
export function changeStatus(t,v){const x=t(`change_${v}`);return x===`change_${v}`?(v||'—'):x}

export function scanTypeLabel(t,v){return t(`scanType_${v}`)||v}
export function serverStatus(t,v){const x=t(`server_${v}`);return x===`server_${v}`?(v||'—'):x}
export function deploymentStatus(t,v){const x=t(`deployment_${v}`);return x===`deployment_${v}`?(v||'—'):x}
export function templateType(t,v){const x=t(`template_${v}`);return x===`template_${v}`?(v||'—'):x}

export function scanTargetLabel(t,r){
  if(!r) return '—'
  if(r.targetType==='ALL'||r.targetValue==='ALL') return t('allAssets')
  if(r.targetType==='ENVIRONMENT') return envLabel(t,r.targetValue)
  return r.targetValue||'—'
}
