import React from 'react'
import { RefreshCw } from 'lucide-react'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import { fmtDate } from '../utils/format'

export default function AuditPage(){
  const {t,lang}=useI18n()
  const {data=[],loading,reload}=useApiData('/api/audit',{initial:[],poll:15000})
  const cols=[
    {key:'createdAt',label:t('time'),render:r=>fmtDate(r.createdAt,lang)},
    {key:'entityType',label:t('entity')},
    {key:'entityId',label:'ID',render:r=><span className="mono">{r.entityId}</span>},
    {key:'action',label:t('operation')},
    {key:'message',label:t('message'),render:r=><span>{lang==='zh'?(r.messageZh||r.messageEn):(r.messageEn||r.messageZh)}</span>},
    {key:'actor',label:t('actor')}
  ]
  return <><PageHeader title={t('audit')}><button className="btn" onClick={reload}><RefreshCw size={15}/>{t('refresh')}</button></PageHeader><DataTable columns={cols} rows={data} empty={loading?t('loading'):t('noData')}/></>
}
