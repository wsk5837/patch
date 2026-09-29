import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { translations } from '../utils/translations'
import { localizeData } from '../utils/dataTranslations'

const I18nContext=createContext(null)
export function I18nProvider({children}){
  const [lang,setLangState]=useState(()=>localStorage.getItem('gazellio_lang')||'zh')
  useEffect(()=>{document.documentElement.lang=lang==='zh'?'zh-CN':'en'},[lang])
  const setLang=(v)=>{setLangState(v);localStorage.setItem('gazellio_lang',v);document.documentElement.lang=v==='zh'?'zh-CN':'en'}
  const t=(key,...args)=>{let value=translations[lang]?.[key] ?? translations.zh[key] ?? key;args.forEach((v,i)=>{value=value.replace(`{${i}}`,v)});return value}
  const pick=(obj,zhKey='titleZh',enKey='titleEn')=>lang==='zh'?(obj?.[zhKey]||obj?.[enKey]||'—'):(obj?.[enKey]||obj?.[zhKey]||'—')
  const localize=(value)=>localizeData(value,lang)
  const value=useMemo(()=>({lang,setLang,t,pick,localize}),[lang])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
export const useI18n=()=>useContext(I18nContext)
