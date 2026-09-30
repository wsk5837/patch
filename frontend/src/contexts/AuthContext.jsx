import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { authApi, getToken, setToken } from '../api/client'

const AuthContext = createContext(null)
export function AuthProvider({children}){
  const [token,setTokenState] = useState(getToken())
  const [user,setUser] = useState(()=>{try{return JSON.parse(localStorage.getItem('gazellio_user')||'null')}catch{return null}})
  useEffect(()=>{
    const fn=()=>{setToken(null);setTokenState(null);setUser(null);localStorage.removeItem('gazellio_user')}
    window.addEventListener('gazellio:unauthorized',fn); return()=>window.removeEventListener('gazellio:unauthorized',fn)
  },[])
  const login=async(username,password)=>{const data=await authApi.login(username,password);setToken(data.token);setTokenState(data.token);setUser(data.user);localStorage.setItem('gazellio_user',JSON.stringify(data.user));return data}
  const logout=async()=>{try{await authApi.logout()}catch{}finally{setToken(null);setTokenState(null);setUser(null);localStorage.removeItem('gazellio_user')}}
  const value=useMemo(()=>({token,user,login,logout,authenticated:!!token}),[token,user])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
export const useAuth=()=>useContext(AuthContext)
