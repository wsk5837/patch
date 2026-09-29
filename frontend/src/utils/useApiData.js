import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'

export function useApiData(path,{poll=0,initial=null}={}){
 const [data,setData]=useState(initial),[loading,setLoading]=useState(true),[error,setError]=useState(null)
 const load=useCallback(async()=>{try{const d=await api(path);setData(d);setError(null)}catch(e){setError(e)}finally{setLoading(false)}},[path])
 useEffect(()=>{setLoading(true);load();if(!poll)return;const id=setInterval(load,poll);return()=>clearInterval(id)},[load,poll])
 return {data,setData,loading,error,reload:load}
}
