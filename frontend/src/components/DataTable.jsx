import React,{useEffect,useMemo,useState} from 'react'
import { useI18n } from '../contexts/I18nContext'
export default function DataTable({columns,rows=[],keyField='id',onRowClick,empty,pageSize=20}){
 const {t}=useI18n();const [page,setPage]=useState(1)
 const pages=Math.max(1,Math.ceil(rows.length/pageSize))
 useEffect(()=>{setPage(p=>Math.min(p,pages))},[pages])
 const visible=useMemo(()=>rows.slice((page-1)*pageSize,page*pageSize),[rows,page,pageSize])
 return <div className="table-card"><div className="table-scroll"><table><thead><tr>{columns.map(c=><th key={c.key} style={c.width?{width:c.width}:undefined}>{c.label}</th>)}</tr></thead><tbody>{visible.length?visible.map(r=><tr key={r[keyField]} className={onRowClick?'clickable':''} onClick={()=>onRowClick?.(r)}>{columns.map(c=><td key={c.key}>{c.render?c.render(r):r[c.key]??'—'}</td>)}</tr>):<tr><td className="empty-row" colSpan={columns.length}>{empty||t('noData')}</td></tr>}</tbody></table></div>{rows.length>pageSize&&<div className="table-pagination"><button className="btn" disabled={page<=1} onClick={()=>setPage(p=>Math.max(1,p-1))}>{t('previous')}</button><span>{t('pageOf',page,pages)}</span><button className="btn" disabled={page>=pages} onClick={()=>setPage(p=>Math.min(pages,p+1))}>{t('next')}</button></div>}</div>
}
