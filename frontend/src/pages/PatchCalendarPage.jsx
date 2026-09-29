import React,{useMemo,useState} from 'react'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useApiData } from '../utils/useApiData'
import { useI18n } from '../contexts/I18nContext'
import PageHeader from '../components/PageHeader'

function monthKey(date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`}
function moveMonth(key,amount){const [y,m]=key.split('-').map(Number);return monthKey(new Date(y,m-1+amount,1))}

export default function PatchCalendarPage(){
 const {t,lang}=useI18n();const nav=useNavigate();const [month,setMonth]=useState(monthKey(new Date()))
 const {data:events=[],loading}=useApiData(`/api/patches/calendar?month=${month}`,{initial:[]})
 const days=useMemo(()=>{const [year,mon]=month.split('-').map(Number);const first=new Date(year,mon-1,1);const count=new Date(year,mon,0).getDate();const offset=(first.getDay()+6)%7;return [...Array(offset).fill(null),...Array.from({length:count},(_,i)=>i+1)]},[month])
 const byDay=useMemo(()=>events.reduce((map,event)=>{const day=new Date(event.date).getDate();(map[day]??=[]).push(event);return map},{}),[events])
 const locale=lang==='zh'?'zh-CN':'en-US';const [year,mon]=month.split('-').map(Number);const title=new Intl.DateTimeFormat(locale,{year:'numeric',month:'long'}).format(new Date(year,mon-1,1))
 const open=event=>nav(event.changeOrderId?`/work-orders/changes/${event.changeOrderId}`:`/work-orders/incidents/${event.incidentId}`)
 const weekdays=lang==='zh'?['一','二','三','四','五','六','日']:['Mon','Tue','Wed','Thu','Fri','Sat','Sun']
 return <><PageHeader title={t('patchCalendar')}><button className="btn" onClick={()=>setMonth(moveMonth(month,-1))}><ChevronLeft size={15}/></button><button className="btn calendar-month-label" onClick={()=>setMonth(monthKey(new Date()))}>{title}</button><button className="btn" onClick={()=>setMonth(moveMonth(month,1))}><ChevronRight size={15}/></button></PageHeader><div className="calendar-legend"><span><i className="cal-dot p1"/>P1 · 3{t('days')}</span><span><i className="cal-dot p2"/>P2 · 7{t('days')}</span><span><i className="cal-dot p3"/>P3 · 30{t('days')}</span><span><i className="cal-dot p4"/>P4 · {t('cycle')}</span><span className="legend-spacer"/><span><i className="cal-outline overdue"/>{t('overdue')}</span><span><i className="cal-outline window"/>{t('maintenanceWindow')}</span></div><div className="patch-calendar"><div className="calendar-weekdays">{weekdays.map(d=><div key={d}>{d}</div>)}</div><div className="calendar-grid">{days.map((day,index)=><div key={index} className={`calendar-day ${!day?'empty':''}`}><span className="day-number">{day||''}</span>{day&&<div className="calendar-events">{(byDay[day]||[]).slice(0,4).map(event=><button key={event.eventId} className={`calendar-event ${String(event.priority).toLowerCase()} ${String(event.status).toLowerCase()}`} onClick={()=>open(event)}><span>{new Date(event.date).toLocaleTimeString(locale,{hour:'2-digit',minute:'2-digit'})}</span><b>{event.patchCode||event.cveId}</b><small>{event.assetName}</small></button>)}{(byDay[day]||[]).length>4&&<div className="calendar-more">+{(byDay[day]||[]).length-4}</div>}</div>}</div>)}</div>{loading&&<div className="calendar-loading"><CalendarDays size={18}/>{t('loading')}</div>}</div></>
}
