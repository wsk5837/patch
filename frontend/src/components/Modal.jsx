import React, { useEffect } from 'react'
import { X } from 'lucide-react'
export default function Modal({open,title,onClose,children,footer,size='md'}){
 useEffect(()=>{const f=e=>{if(e.key==='Escape')onClose?.()};if(open)window.addEventListener('keydown',f);return()=>window.removeEventListener('keydown',f)},[open,onClose])
 if(!open)return null
 return <div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)onClose?.()}}><div className={`modal modal-${size}`}><div className="modal-head"><h3>{title}</h3><button className="icon-button" onClick={onClose}><X size={18}/></button></div><div className="modal-body">{children}</div>{footer&&<div className="modal-foot">{footer}</div>}</div></div>
}
