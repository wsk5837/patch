import React,{useMemo,useState} from 'react'
import {Database,RefreshCw,RotateCw} from 'lucide-react'
import {useNavigate} from 'react-router-dom'
import {api} from '../api/client'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import {useAuth} from '../contexts/AuthContext'
import {useToast} from '../components/ToastContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import StatusBadge,{statusTone} from '../components/StatusBadge'
import {assetTypeLabel,cmdbClassLabel,envLabel,fmtDate} from '../utils/format'

export default function AssetsPage(){
 const {t,lang,localize}=useI18n(),{has}=useAuth(),toast=useToast(),nav=useNavigate()
 const {data=[],loading,reload}=useApiData('/api/assets',{initial:[]})
 const {data:cmdb={},reload:reloadCmdb}=useApiData('/api/cmdb/status',{initial:{configured:false,syncing:false,classes:[]},poll:15000})
 const [q,setQ]=useState(''),[env,setEnv]=useState('ALL'),[ciClass,setCiClass]=useState('ALL'),[syncing,setSyncing]=useState(false)
 const classOptions=useMemo(()=>[...new Map(data.map(x=>[x.cmdbClassKey||x.assetType,{key:x.cmdbClassKey||x.assetType,name:x.cmdbClassName,assetType:x.assetType}])).values()].sort((a,b)=>(a.name||a.key).localeCompare(b.name||b.key)),[data])
 const rows=useMemo(()=>data.filter(a=>(!q||(`${a.assetCode} ${a.name} ${a.ipAddress} ${a.networkSegment} ${a.businessService} ${a.ownerName} ${a.installedProducts} ${a.cmdbClassName}`).toLowerCase().includes(q.toLowerCase()))&&(env==='ALL'||a.environment===env)&&(ciClass==='ALL'||(a.cmdbClassKey||a.assetType)===ciClass)),[data,q,env,ciClass])
 const synchronizedCount=useMemo(()=>data.filter(x=>x.sourceSystem==='CMDB').length,[data])
 const typeLabel=r=>r.cmdbClassKey?cmdbClassLabel(t,r.cmdbClassKey,r.cmdbClassName):assetTypeLabel(t,r.assetType)
 const sync=async()=>{setSyncing(true);try{const result=await api('/api/cmdb/sync',{method:'POST',timeout:120000});await Promise.all([reload(),reloadCmdb()]);toast.push(t('cmdbSyncResult',result.imported||0,result.updated||0))}catch(e){toast.push(e.message||t('operationFailed'),'red')}finally{setSyncing(false)}}
 const cols=[
  {key:'assetCode',label:t('assetCode'),render:r=><div className="cell-main"><b className="mono linkish">{r.assetCode}</b>{r.cmdbItemId&&<small>{r.cmdbItemId.slice(0,12)}</small>}</div>},
  {key:'name',label:t('asset'),render:r=><div className="cell-main"><b>{localize(r.name)}</b><small>{r.hostname||r.ipAddress||'—'}</small></div>},
  {key:'cmdbClassName',label:t('configurationItemType'),render:r=><div className="cell-main"><b>{typeLabel(r)}</b>{r.cmdbClassKey&&<small className="mono">{r.cmdbClassKey}</small>}</div>},
  {key:'networkSegment',label:t('networkSegment'),render:r=><div className="cell-main"><b className="mono">{r.ipAddress||'—'}</b><small className="mono">{r.networkSegment||'—'}</small></div>},
  {key:'assetType',label:t('assetType'),render:r=>assetTypeLabel(t,r.assetType)},
  {key:'environment',label:t('environment'),render:r=><StatusBadge tone={r.environment==='PROD'?'purple':'gray'}>{envLabel(t,r.environment)}</StatusBadge>},
  {key:'businessService',label:t('businessService'),render:r=>localize(r.businessService)||'—'},
  {key:'ownerName',label:t('owner'),render:r=>localize(r.ownerName)||'—'},
  {key:'cmdbState',label:t('assetStatus'),render:r=><StatusBadge tone={r.cmdbEnabled===false?'gray':'ok'}>{lang==='zh'&&r.cmdbState?r.cmdbState:(r.cmdbEnabled===false?t('disabled'):t('enabled'))}</StatusBadge>},
  {key:'agentStatus',label:t('agentStatus'),render:r=><StatusBadge tone={statusTone(r.agentStatus)}>{t(String(r.agentStatus).toLowerCase())}</StatusBadge>},
  {key:'openFindings',label:t('openVulnerabilities')},
  {key:'cmdbSyncedAt',label:t('dataUpdatedAt'),render:r=>fmtDate(r.cmdbSyncedAt,lang)}
 ]
 return <>
  <PageHeader title={t('assetManagement')}><button className="btn" onClick={()=>{reload();reloadCmdb()}}><RefreshCw size={15}/>{t('refresh')}</button>{has('ASSET_SYNC')&&<button className="btn primary" disabled={!cmdb.configured||syncing||cmdb.syncing} onClick={sync}><RotateCw className={syncing||cmdb.syncing?'spin':''} size={15}/>{syncing||cmdb.syncing?t('syncingAssets'):t('syncAssets')}</button>}</PageHeader>
  <div className={`cmdb-status-bar status-${String(cmdb.lastStatus||'NOT_RUN').toLowerCase()}`}><Database size={18}/><div><b>{t('assetDataSync')}</b><span>{cmdb.configured?t(`cmdbStatus_${cmdb.lastStatus||'NOT_RUN'}`):t('assetSourceNotConfigured')}</span></div><span className="cmdb-status-stat"><b>{synchronizedCount}</b><small>{t('managedAssets')}</small></span><span className="cmdb-status-stat"><b>{classOptions.length}</b><small>{t('configurationItemTypes')}</small></span><span className="cmdb-status-stat"><b>{fmtDate(cmdb.lastCompletedAt,lang)}</b><small>{t('lastSync')}</small></span></div>
  <div className="toolbar"><input className="search-input" value={q} onChange={e=>setQ(e.target.value)} placeholder={`${t('search')} ${t('asset')}`}/><select value={ciClass} onChange={e=>setCiClass(e.target.value)}><option value="ALL">{t('all')} · {t('configurationItemType')}</option>{classOptions.map(x=><option value={x.key} key={x.key}>{x.name?cmdbClassLabel(t,x.key,x.name):assetTypeLabel(t,x.assetType)}</option>)}</select><select value={env} onChange={e=>setEnv(e.target.value)}><option value="ALL">{t('all')} · {t('environment')}</option><option value="PROD">{t('production')}</option><option value="PREPROD">{t('preprod')}</option><option value="TEST">{t('test')}</option><option value="DEV">{t('development')}</option></select><span className="toolbar-count">{rows.length}</span></div>
  <DataTable columns={cols} rows={rows} onRowClick={r=>nav(`/assets/${r.id}`)} empty={loading?t('loading'):(cmdb.configured?t('noSynchronizedAssets'):t('noData'))} pageSize={30}/>
 </>
}
