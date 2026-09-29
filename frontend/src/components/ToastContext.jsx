import React,{createContext,useContext,useMemo,useState} from 'react'
const C=createContext(null)
export function ToastProvider({children}){const [items,setItems]=useState([]);const push=(message,tone='ok')=>{const id=Date.now()+Math.random();setItems(x=>[...x,{id,message,tone}]);setTimeout(()=>setItems(x=>x.filter(y=>y.id!==id)),3000)};const value=useMemo(()=>({push}),[]);return <C.Provider value={value}>{children}<div className="toast-stack">{items.map(x=><div className={`toast toast-${x.tone}`} key={x.id}>{x.message}</div>)}</div></C.Provider>}
export const useToast=()=>useContext(C)
