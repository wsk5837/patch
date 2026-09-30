import { translations } from '../src/utils/translations.js'
import { localizePayload } from '../src/utils/dataTranslations.js'
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
const customerAssetFixture={
 assetName:'Oracle MySQL', ownerName:'应用负责人', businessService:'数据库服务',
 rows:[{assetName:'Apache Tomcat',zone:'生产数据中心',ownerName:'曾卫平'},{assetName:'Grafana',zone:'预生产资源区',ownerName:'应用负责人'}]
}
const localized=localizePayload(customerAssetFixture,'en')
if(/[\u3400-\u9fff]/.test(JSON.stringify(localized))){
 console.error('English dynamic-data localization left Chinese text',localized)
 process.exit(1)
}
console.log(`i18n OK: ${baseline.size} keys across ${languages.join(', ')}; customer asset data localized`)
