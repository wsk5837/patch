import React from 'react'
import { ArrowLeft } from 'lucide-react'
import { useNavigate,useParams } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import { templateType } from '../utils/format'
import { OrchestrationGraph } from '../components/FlowGraph'
import StatusBadge from '../components/StatusBadge'
export default function AutomationTemplateDetailPage(){const {id}=useParams();const nav=useNavigate();const {t,pick}=useI18n();const {data:x,loading}=useApiData(`/api/automation/templates/${id}`);if(loading&&!x)return <div className="loading">{t('loading')}</div>;if(!x)return null;return <><div className="detail-top"><button className="back-button" onClick={()=>nav(-1)}><ArrowLeft size={16}/>{t('back')}</button></div><section className="detail-hero"><div><div className="eyebrow"><StatusBadge tone="purple">{x.code}</StatusBadge><StatusBadge tone={x.enabled?'ok':'gray'}>v{x.version}</StatusBadge></div><h1>{pick(x,'nameZh','nameEn')}</h1><h2>{templateType(t,x.type)}</h2></div></section><section className="panel"><div className="panel-head"><h2>{t('flowChart')}</h2></div><div className="panel-body flow-wrap"><OrchestrationGraph steps={x.steps} template/></div></section></>}
