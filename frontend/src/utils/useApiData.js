import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'

export function useApiData(path,{poll=0,initial=null}={}){
 const [data,setData]=useState(initial),[loading,setLoading]=useState(true),[error,setError]=useState(null)
 const active=useRef(true),inFlight=useRef(null),generation=useRef(0)
 const load=useCallback(async(showLoading=false)=>{
  if(inFlight.current)return inFlight.current
  if(showLoading)setLoading(true)
  const currentGeneration=generation.current
  const pending=api(path).then(d=>{if(active.current&&generation.current===currentGeneration){setData(d);setError(null)}return d}).catch(e=>{if(active.current&&generation.current===currentGeneration)setError(e);return undefined}).finally(()=>{if(active.current&&generation.current===currentGeneration)setLoading(false);if(inFlight.current===pending)inFlight.current=null})
  inFlight.current=pending
  return pending
 },[path])
 useEffect(()=>{
  active.current=true
  generation.current+=1
  let timer,cancelled=false
  const tick=async(first=false)=>{
   if(!document.hidden){try{await load(first)}catch{}}
   if(!cancelled&&poll)timer=window.setTimeout(()=>tick(false),poll)
  }
  tick(true)
  const onVisible=()=>{if(!document.hidden)load(false).catch(()=>{})}
  document.addEventListener('visibilitychange',onVisible)
  return()=>{cancelled=true;active.current=false;generation.current+=1;inFlight.current=null;window.clearTimeout(timer);document.removeEventListener('visibilitychange',onVisible)}
 },[load,poll])
 const reload=useCallback(()=>load(false),[load])
 return {data,setData,loading,error,reload}
}
