import React,{useState} from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { LayoutDashboard, ShieldAlert, Library, ScanSearch, ClipboardList, Package, CheckSquare, Workflow, BarChart3, Settings, ScrollText, Search, Bell, LogOut, ChevronDown, TicketCheck, FileClock, CalendarDays } from 'lucide-react'
import { useI18n } from '../contexts/I18nContext'
import { useAuth } from '../contexts/AuthContext'

const groups=[
 {key:'vulnerabilityManagement',items:[['/vulnerabilities/library','vulnerabilityLibrary',Library],['/vulnerabilities/findings','findings',ShieldAlert],['/scans','scanManagement',ScanSearch]]},
 {key:'workOrderCenter',items:[['/work-orders/incidents','securityIncidents',TicketCheck],['/work-orders/changes','changeOrders',FileClock]]},
 {key:'remediation',items:[['/tasks','tasks',ClipboardList],['/patches','patchCenter',Package],['/patches/calendar','patchCalendar',CalendarDays],['/approvals','approvals',CheckSquare],['/automation','automation',Workflow]]},
 {key:null,items:[['/reports','reports',BarChart3],['/audit','audit',ScrollText],['/settings','settings',Settings]]}
]
export default function Layout(){
 const {t,lang,setLang}=useI18n();const {user,logout}=useAuth();const nav=useNavigate();const [q,setQ]=useState('')
 const submit=e=>{e.preventDefault();if(q.trim())nav('/vulnerabilities/findings?q='+encodeURIComponent(q.trim()))}
 return <div className="app-shell"><aside className="sidebar"><div className="brand"><img src="/gazellio-logo.png" alt="Gazellio"/></div><nav><NavLink to="/" end className={({isActive})=>`nav-link ${isActive?'active':''}`}><LayoutDashboard/><span>{t('dashboard')}</span></NavLink>{groups.map((g,gi)=><div className="nav-group" key={gi}>{g.key&&<div className="nav-group-title">{t(g.key)}</div>}{g.items.map(([to,key,Icon])=><NavLink key={to} to={to} end={to==='/patches'} className={({isActive})=>`nav-link ${isActive?'active':''}`}><Icon/><span>{t(key)}</span></NavLink>)}</div>)}</nav><div className="sidebar-health"><span className="health-dot"/><span>{t('normal')}</span></div></aside><main className="main"><header className="topbar"><form className="global-search" onSubmit={submit}><Search size={17}/><input value={q} onChange={e=>setQ(e.target.value)} placeholder={t('globalSearch')}/></form><div className="top-actions"><button className="lang-toggle" onClick={()=>setLang(lang==='zh'?'en':'zh')}>{lang==='zh'?t('switchToEnglish'):t('switchToChinese')}</button><button className="icon-button"><Bell size={18}/><span className="bell-dot"/></button><div className="user-box"><div className="avatar">{(user?.displayName||'GA').slice(0,2).toUpperCase()}</div><div className="user-copy"><b>{user?.displayName||'Gazellio'}</b><small>{user?.role?t(`role_${user.role}`):''}</small></div><ChevronDown size={14}/><button className="logout-button" title={t('logout')} onClick={logout}><LogOut size={16}/></button></div></div></header><section className="content"><Outlet/></section></main></div>
}
