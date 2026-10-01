export function fmtDate(v, lang='zh'){
  if(!v) return '—'
  const d=new Date(v); if(Number.isNaN(d.getTime())) return String(v)
  return new Intl.DateTimeFormat(lang==='zh'?'zh-CN':'en-US',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).format(d)
}
export function envLabel(t,v){ return ({PROD:t('production'),TEST:t('test'),PREPROD:t('preprod'),DEV:t('development'),MIXED:t('mixed')})[v]||v||'—' }
export function assetTypeLabel(t,v){const key=({DATABASE:'database',MIDDLEWARE:'middleware',APPLICATION_PLATFORM:'applicationPlatform',APPLICATION_RUNTIME:'applicationRuntime',SECURITY_COMPONENT:'securityComponent',OBSERVABILITY:'observability',COLLABORATION:'collaboration',FILE_SERVICE:'fileService',SEARCH_PLATFORM:'searchPlatform',UNCLASSIFIED:'unclassified',VIRTUAL_MACHINE:'virtualMachine',PHYSICAL_SERVER:'physicalServer',NETWORK_DEVICE:'networkDevice'})[v];return key?t(key):(v||'—')}
export function cmdbClassLabel(t,key,fallback){
  const labels={virtual_host:'virtualMachine',physics_machine:'physicalServer',firewall:'firewall',swtich:'switch',switch:'switch',router:'router',load_balance:'loadBalancer',nas:'nasDevice',application:'applicationPlatform',service:'applicationRuntime'}
  const translated=labels[String(key||'')]
  if(translated){const value=t(translated);if(value!==translated)return value}
  return ({mysql:'MySQL',oracle:'Oracle',redis:'Redis',postgresql:'PostgreSQL',mongodb:'MongoDB',tomcat:'Apache Tomcat',nginx:'Nginx',KingBase:'KingBase'})[key]||fallback||key||'—'
}
export function severityLabel(t,v){return ({CRITICAL:t('critical'),HIGH:t('high'),MEDIUM:t('medium'),LOW:t('low'),UNKNOWN:t('unknown')})[v]||v||'—'}
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

export function scanTargetLabel(t,r,localize=(value)=>value){
  if(!r) return '—'
  if(r.targetType==='ALL'||r.targetValue==='ALL') return t('allAssets')
  if(r.targetType==='ENVIRONMENT') return envLabel(t,r.targetValue)
  if(r.targetType==='CMDB_CLASS') return r.targetValue||'—'
  return localize(r.targetValue)||'—'
}
