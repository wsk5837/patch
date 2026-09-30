import React,{useEffect,useMemo,useState} from 'react'
import { useI18n } from '../contexts/I18nContext'
export default function DataTable({columns,rows=[],keyField='id',onRowClick,empty,pageSize=20,page:controlledPage,totalPages:controlledTotalPages,onPageChange}){
 const {t}=useI18n();const [localPage,setLocalPage]=useState(1)
 const serverPaged=Number.isFinite(controlledPage)&&Number.isFinite(controlledTotalPages)&&typeof onPageChange==='function'
 const pages=serverPaged?Math.max(1,controlledTotalPages):Math.max(1,Math.ceil(rows.length/pageSize))
 const page=serverPaged?Math.min(Math.max(1,controlledPage),pages):localPage
 useEffect(()=>{if(!serverPaged)setLocalPage(p=>Math.min(p,pages))},[pages,serverPaged])
 const visible=useMemo(()=>serverPaged?rows:rows.slice((page-1)*pageSize,page*pageSize),[rows,page,pageSize,serverPaged])
 const go=next=>serverPaged?onPageChange(next):setLocalPage(next)
 return <div className="table-card"><div className="table-scroll"><table><thead><tr>{columns.map(c=><th key={c.key} style={c.width?{width:c.width}:undefined}>{c.label}</th>)}</tr></thead><tbody>{visible.length?visible.map(r=><tr key={r[keyField]} className={onRowClick?'clickable':''} onClick={()=>onRowClick?.(r)}>{columns.map(c=><td key={c.key}>{c.render?c.render(r):r[c.key]??'—'}</td>)}</tr>):<tr><td className="empty-row" colSpan={columns.length}>{empty||t('noData')}</td></tr>}</tbody></table></div>{pages>1&&<div className="table-pagination"><button className="btn" disabled={page<=1} onClick={()=>go(Math.max(1,page-1))}>{t('previous')}</button><span>{t('pageOf',page,pages)}</span><button className="btn" disabled={page>=pages} onClick={()=>go(Math.min(pages,page+1))}>{t('next')}</button></div>}</div>
}
