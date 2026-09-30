const exactEnglish={
 '王卫嘉':'Weijia Wang','曾卫平':'Weiping Zeng','应用负责人':'Application Owner','发布审批人':'Release Approver',
 '支付服务':'Payment Service','互联网门户':'Internet Portal','核心业务':'Core Business','研发平台':'Engineering Platform',
 '客户服务':'Customer Service','数据服务':'Data Service','远程接入':'Remote Access','入口网关':'Ingress Gateway',
 '支付应用节点 01':'Payment Application Node 01','支付应用节点 02':'Payment Application Node 02','支付测试节点':'Payment Test Node',
 '支付预生产节点':'Payment Pre-production Node','互联网门户 Web 01':'Internet Portal Web 01','互联网门户测试 Web':'Internet Portal Test Web',
 '核心 Windows 应用 01':'Core Windows Application 01','核心 Windows 预生产':'Core Windows Pre-production',
 '核心 Windows 测试':'Core Windows Test','客户服务 Tomcat':'Customer Service Tomcat','客户服务测试节点':'Customer Service Test Node',
 '客户服务预生产节点':'Customer Service Pre-production Node','业务数据库 01':'Business Database 01','远程接入网关':'Remote Access Gateway',
 '远程接入测试网关':'Remote Access Test Gateway','远程接入预生产网关':'Remote Access Pre-production Gateway',
 '应用交付控制器':'Application Delivery Controller','入口网关测试设备':'Ingress Gateway Test Appliance',
 '入口网关预生产设备':'Ingress Gateway Pre-production Appliance','TeamCity 构建平台':'TeamCity Build Platform',
 '研发平台测试节点':'Engineering Platform Test Node','研发平台预生产节点':'Engineering Platform Pre-production Node',
 '生产服务器认证扫描':'Production Server Authenticated Scan','测试环境补丁复测':'Test Environment Patch Retest',
 '互联网暴露面快速扫描':'Internet Exposure Quick Scan','紧急变更审批人':'Emergency Change Approver',
 '重大变更审批人':'Major Change Approver','运维负责人':'Operations Lead','安全负责人':'Security Lead','标准变更授权':'Standard Change Authorization',
 '正在执行':'Running','等待执行':'Waiting','执行成功':'Succeeded','正在读取验证基线':'Reading validation baseline','等待复测':'Waiting for retest',
 '执行已暂停':'Execution paused','执行已恢复':'Execution resumed','已恢复至回退点':'Restored to rollback point',
 '读取补丁安装状态与验证基线':'Reading installed patch state and validation baseline',
 '补丁安装、健康检查与证据回写完成':'Patch installation, health checks and evidence write-back completed',
 '安装状态、版本标识、漏洞探针与应用健康验证通过':'Installed state, version marker, vulnerability probe and application health validation passed',
 '正在执行自动化补丁节点':'Running automation patch step','已同步部署结果':'Deployment result synchronized',
 '测试环境验证与复测通过，申请进入生产发布。':'Test validation and retest passed; requesting production release.',
 '失败自动暂停；恢复快照或回退补丁版本。':'Pause automatically on failure; restore the snapshot or roll back the patch version.',
 '基于漏洞严重度、KEV 状态、资产重要度、影响范围与重启要求评估。':'Assessed using severity, KEV status, asset criticality, blast radius and restart requirements.',
 '测试复测通过后，按 Ring 0/1/2 分批执行生产补丁并进行应用验证。':'After the test retest passes, roll out production patching through Rings 0/1/2 and validate the application.',
 '失败时暂停后续批次，恢复快照或回退补丁版本，并重新验证服务健康状态。':'On failure, pause later rings, restore the snapshot or patch version, and revalidate service health.'
}

const replacements=[
 ['虚拟机','Virtual Machine'],['物理机','Physical Server'],['主数据中心','Primary Data Center'],['测试云区','Test Cloud Zone'],['批次','Batch'],['前置检查','Pre-check'],['已完成','Completed'],
 [' 生产补丁发布',' Production Patch Release'],['生产补丁发布','Production Patch Release'],
 [' 环境漏洞复测已创建',' environment vulnerability retest created'],[' 环境补丁效果复测已启动',' environment patch effect retest started'],
 ['任务动作：','Task action: '],['已触发变更','triggered change'],['安全事件','Security incident'],['补丁处置任务','patch remediation task']
]

const embeddedEnglish=Object.entries(exactEnglish)
 .filter(([source])=>/[\u3400-\u9fff]/.test(source))
 .sort(([a],[b])=>b.length-a.length)

export function localizeData(value,lang){
 if(value==null||lang==='zh')return value
 const original=String(value)
 let text=original.replace(/\s+/g,' ').trim()
 if(exactEnglish[text])return exactEnglish[text]
 for(const [zh,en] of embeddedEnglish)text=text.split(zh).join(en)
 for(const [zh,en] of replacements)text=text.split(zh).join(en)
 return text||original
}

export function localizePayload(value,lang){
 if(lang==='zh'||value==null)return value
 if(typeof value==='string')return localizeData(value,lang)
 if(Array.isArray(value))return value.map(item=>localizePayload(item,lang))
 if(typeof value==='object')return Object.fromEntries(Object.entries(value).map(([key,item])=>[key,localizePayload(item,lang)]))
 return value
}
