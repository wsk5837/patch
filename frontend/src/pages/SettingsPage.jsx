import React,{useEffect,useMemo,useState} from 'react'
import {CalendarClock,Gauge,Save,Search,Workflow} from 'lucide-react'
import {useApiData} from '../utils/useApiData'
import {api} from '../api/client'
import {useI18n} from '../contexts/I18nContext'
import {useAuth} from '../contexts/AuthContext'
import {useToast} from '../components/ToastContext'
import PageHeader from '../components/PageHeader'
import StatusBadge from '../components/StatusBadge'

const defaults={scanPolicyProd:'0 0 2 * * SAT',scanPolicyTest:'0 0 */6 * * *',maintenanceWindow:'Sat 00:00-04:00',autoRollbackThreshold:'5',batchSize:'20',batchConcurrency:'10',slaP1Days:'3',slaP2Days:'7',slaP3Days:'30',slaP4Days:'90',auditRetentionMonths:'12'}
const scenarios=['STANDARD_PATCH','EMERGENCY_PATCH','RETEST','BATCH_PATCH']

export default function SettingsPage(){
 const {t,pick}=useI18n(),{has}=useAuth(),toast=useToast();const editable=has('SETTINGS_MANAGE')
 const {data,reload}=useApiData('/api/settings');const {data:templates=[]}=useApiData('/api/automation/templates',{initial:[]});const {data:bindingRows=[],reload:reloadBindings}=useApiData('/api/automation/bindings',{initial:[]})
 const [form,setForm]=useState(defaults),[bindings,setBindings]=useState({}),[busy,setBusy]=useState(false)
 useEffect(()=>{if(data)setForm({...defaults,...data})},[data]);useEffect(()=>{if(bindingRows.length)setBindings(Object.fromEntries(bindingRows.map(x=>[x.scenario,x.templateId])))},[bindingRows])
 const enabled=useMemo(()=>templates.filter(x=>x.enabled),[templates])
 const save=async()=>{setBusy(true);try{await api('/api/settings',{method:'PUT',body:{values:form}});await api('/api/automation/bindings',{method:'PUT',body:{bindings}});await Promise.all([reload(),reloadBindings()]);toast.push(t('settingsSaved'))}catch(e){toast.push(e.message||t('operationFailed'),'red')}finally{setBusy(false)}}
 const field=(key,type='text',props={})=><label className="setting-field"><span>{t(key)}</span><input disabled={!editable} type={type} value={form[key]||''} onChange={e=>setForm({...form,[key]:e.target.value})} {...props}/></label>
 return <><PageHeader title={t('settings')}>{editable&&<button className="btn primary" disabled={busy} onClick={save}><Save size={15}/>{t('save')}</button>}</PageHeader><div className="settings-console">
  <section className="panel settings-card"><div className="panel-head"><h2><Workflow size={16}/>{t('automationBindings')}</h2></div><div className="settings-card-body automation-binding-grid">{scenarios.map(s=>{const expected=s==='RETEST'?'RETEST':'PATCH',bound=bindingRows.find(x=>x.scenario===s);return <label className="binding-field" key={s}><span>{t(`scenario_${s}`)}</span><select disabled={!editable} value={bindings[s]||''} onChange={e=>setBindings({...bindings,[s]:Number(e.target.value)})}>{enabled.filter(x=>x.type===expected).map(x=><option value={x.id} key={x.id}>{pick(x,'nameZh','nameEn')} · v{x.version}</option>)}</select><small>{bound?.templateCode||'—'}{bound&&<StatusBadge tone="ok">{t('active')}</StatusBadge>}</small></label>})}</div></section>
  <section className="panel settings-card"><div className="panel-head"><h2><CalendarClock size={16}/>{t('slaAndRetention')}</h2></div><div className="settings-card-body setting-fields five">{field('slaP1Days','number',{min:1})}{field('slaP2Days','number',{min:1})}{field('slaP3Days','number',{min:1})}{field('slaP4Days','number',{min:1})}{field('auditRetentionMonths','number',{min:12})}</div></section>
  <section className="panel settings-card"><div className="panel-head"><h2><Gauge size={16}/>{t('deploymentPolicy')}</h2></div><div className="settings-card-body setting-fields">{field('maintenanceWindow')}{field('batchSize','number',{min:1,max:500})}{field('batchConcurrency','number',{min:1,max:200})}{field('autoRollbackThreshold','number',{min:1,max:100})}</div></section>
  <section className="panel settings-card"><div className="panel-head"><h2><Search size={16}/>{t('scanPolicy')}</h2></div><div className="settings-card-body setting-fields two">{field('scanPolicyProd')}{field('scanPolicyTest')}</div></section>
 </div></>
}
