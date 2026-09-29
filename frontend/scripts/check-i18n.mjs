import { translations } from '../src/utils/translations.js'
const languages=Object.keys(translations)
if(languages.length<2) throw new Error('At least two languages are required')
const baseline=new Set(Object.keys(translations[languages[0]]))
let failed=false
for(const lang of languages.slice(1)){
  const keys=new Set(Object.keys(translations[lang]))
  const missing=[...baseline].filter(k=>!keys.has(k))
  const extra=[...keys].filter(k=>!baseline.has(k))
  if(missing.length||extra.length){failed=true;console.error(lang,{missing,extra})}
}
if(failed) process.exit(1)
console.log(`i18n OK: ${baseline.size} keys across ${languages.join(', ')}`)
