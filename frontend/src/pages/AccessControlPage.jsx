import React,{useMemo,useState} from 'react'
import {Copy,KeyRound,LockKeyhole,Plus,RotateCcw,Search,ShieldCheck,Trash2,UnlockKeyhole,UserRound} from 'lucide-react'
import {api} from '../api/client'
import {useApiData} from '../utils/useApiData'
import {useI18n} from '../contexts/I18nContext'
import {useToast} from '../components/ToastContext'
import {useAuth} from '../contexts/AuthContext'
import {fmtDate} from '../utils/format'
import PageHeader from '../components/PageHeader'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import StatusBadge from '../components/StatusBadge'

const emptyUser={username:'',displayName:'',email:'',department:'',employeeNo:'',phone:'',accountType:'LOCAL',roleId:'',roleIds:[],enabled:true,password:''}
const emptyRole={code:'',nameZh:'',nameEn:'',descriptionZh:'',descriptionEn:'',dataScope:'ALL',enabled:true,permissions:[]}
const permissionGroups=['DASHBOARD','VULNERABILITY','SCAN','ASSET','INCIDENT','CHANGE','TASK','PATCH','APPROVAL','AUTOMATION','REPORT','AUDIT','SETTINGS','USER']

export default function AccessControlPage(){
 const {t,lang}=useI18n(),toast=useToast(),{has}=useAuth()
 const [tab,setTab]=useState('users'),[userForm,setUserForm]=useState(null),[roleForm,setRoleForm]=useState(null),[busy,setBusy]=useState(false)
 const [query,setQuery]=useState(''),[roleFilter,setRoleFilter]=useState('ALL'),[stateFilter,setStateFilter]=useState('ALL')
 const {data:users=[],reload:reloadUsers}=useApiData('/api/access-control/users',{initial:[]})
 const {data:roles=[],reload:reloadRoles}=useApiData('/api/access-control/roles',{initial:[]})
 const {data:permissions=[]}=useApiData('/api/access-control/permissions',{initial:[]})
 const enabledRoles=roles.filter(r=>r.enabled)
 const grouped=useMemo(()=>permissionGroups.map(group=>({group,items:permissions.filter(p=>p.startsWith(group+'_'))})).filter(x=>x.items.length),[permissions])
 const filteredUsers=useMemo(()=>users.filter(row=>{
   const needle=query.trim().toLowerCase(),hay=[row.username,row.displayName,row.email,row.department,row.employeeNo,row.phone].filter(Boolean).join(' ').toLowerCase()
   const stateOk=stateFilter==='ALL'||(stateFilter==='ENABLED'&&row.enabled&&!row.locked)||(stateFilter==='DISABLED'&&!row.enabled)||(stateFilter==='LOCKED'&&row.locked)
   return (!needle||hay.includes(needle))&&(roleFilter==='ALL'||(row.roleIds||[row.roleId]).map(String).includes(roleFilter))&&stateOk
 }),[users,query,roleFilter,stateFilter])
 const filteredRoles=useMemo(()=>roles.filter(row=>{
   const needle=query.trim().toLowerCase(),hay=[row.code,row.nameZh,row.nameEn,row.descriptionZh,row.descriptionEn].filter(Boolean).join(' ').toLowerCase()
   return (!needle||hay.includes(needle))&&(stateFilter==='ALL'||(stateFilter==='ENABLED'&&row.enabled)||(stateFilter==='DISABLED'&&!row.enabled))
 }),[roles,query,stateFilter])
 const refresh=()=>Promise.all([reloadUsers(),reloadRoles()])
 const saveUser=async()=>{setBusy(true);try{const edit=!!userForm.id,roleIds=(userForm.roleIds||[]).map(Number);await api(`/api/access-control/users${edit?`/${userForm.id}`:''}`,{method:edit?'PUT':'POST',body:{username:userForm.username,displayName:userForm.displayName,email:userForm.email,department:userForm.department,employeeNo:userForm.employeeNo,phone:userForm.phone,accountType:userForm.accountType,roleId:roleIds[0],roleIds,enabled:userForm.enabled,password:userForm.password||null}});setUserForm(null);await refresh();toast.push(t('userSaved'))}catch(e){toast.push(e.message,'red')}finally{setBusy(false)}}
 const saveRole=async()=>{setBusy(true);try{const edit=!!roleForm.id;await api(`/api/access-control/roles${edit?`/${roleForm.id}`:''}`,{method:edit?'PUT':'POST',body:{code:roleForm.code,nameZh:roleForm.nameZh,nameEn:roleForm.nameEn,descriptionZh:roleForm.descriptionZh,descriptionEn:roleForm.descriptionEn,dataScope:roleForm.dataScope,enabled:roleForm.enabled,permissions:roleForm.permissions||[]}});setRoleForm(null);await refresh();toast.push(t('roleSaved'))}catch(e){toast.push(e.message,'red')}finally{setBusy(false)}}
 const remove=async(type,id)=>{if(!window.confirm(t(type==='users'?'confirmDeleteUser':'confirmDeleteRole')))return;try{await api(`/api/access-control/${type}/${id}`,{method:'DELETE'});await refresh();toast.push(t('operationSuccess'))}catch(e){toast.push(e.message,'red')}}
 const userAction=async(id,action,confirmKey,successKey)=>{if(confirmKey&&!window.confirm(t(confirmKey)))return;try{await api(`/api/access-control/users/${id}/${action}`,{method:'POST'});await reloadUsers();toast.push(t(successKey))}catch(e){toast.push(e.message,'red')}}
 const togglePermission=(permission,checked)=>setRoleForm(current=>({...current,permissions:checked?[...new Set([...(current.permissions||[]),permission])]:(current.permissions||[]).filter(x=>x!==permission)}))
 const toggleGroup=(items,checked)=>setRoleForm(current=>({...current,permissions:checked?[...new Set([...(current.permissions||[]),...items])]:(current.permissions||[]).filter(x=>!items.includes(x))}))
 const toggleUserRole=(roleId,checked)=>setUserForm(current=>{const ids=(current.roleIds||[]).map(Number);const roleIds=checked?[...new Set([...ids,Number(roleId)])]:ids.filter(id=>id!==Number(roleId));return {...current,roleIds,roleId:roleIds[0]||''}})
 const openUser=row=>setUserForm({...row,roleIds:(row.roleIds?.length?row.roleIds:[row.roleId]).filter(Boolean).map(Number),password:''})
 const openRole=row=>setRoleForm({...row,permissions:[...(row.permissions||[])]})
 const cloneRole=row=>setRoleForm({...row,id:null,code:`${row.code}_COPY`,systemRole:false,userCount:0,permissions:[...(row.permissions||[])]})
 const userCols=[
  {key:'username',label:t('username'),render:r=><div className="cell-main"><b>{r.username}</b><small>{r.employeeNo||r.email||'—'}</small></div>},
  {key:'profile',label:t('userProfile'),render:r=><div className="cell-main"><b>{r.displayName}</b><small>{[r.department,r.phone].filter(Boolean).join(' · ')||'—'}</small></div>},
  {key:'accountType',label:t('accountType'),render:r=><StatusBadge tone={r.accountType==='LOCAL'?'purple':'blue'}>{t(`accountType_${r.accountType||'LOCAL'}`)}</StatusBadge>},
  {key:'role',label:t('roles'),render:r=><div className="cell-main user-role-list"><b>{(r.roles||[]).map(role=>lang==='zh'?role.nameZh:role.nameEn).join(' · ')||(lang==='zh'?r.roleNameZh:r.roleNameEn)}</b><small>{t('effectivePermissions')}: {r.permissions?.length||0}</small></div>},
  {key:'lastLoginAt',label:t('lastLogin'),render:r=>fmtDate(r.lastLoginAt,lang)},
  {key:'state',label:t('accountStatus'),render:r=><StatusBadge tone={r.locked?'red':r.enabled?'ok':'gray'}>{r.locked?t('locked'):r.enabled?t('enabled'):t('disabled')}</StatusBadge>},
  {key:'actions',label:t('action'),render:r=><div className="row-actions">{has('USER_MANAGE')&&<button className="btn small" onClick={e=>{e.stopPropagation();openUser(r)}}>{t('edit')}</button>}{r.locked&&has('USER_ACCOUNT_STATUS')&&<button className="btn small" title={t('unlockUser')} onClick={e=>{e.stopPropagation();userAction(r.id,'unlock',null,'userUnlocked')}}><UnlockKeyhole size={13}/></button>}{has('USER_CREDENTIAL_RESET')&&<button className="btn small" title={t('resetPassword')} onClick={e=>{e.stopPropagation();userAction(r.id,'reset-password','confirmResetPassword','passwordReset')}}><RotateCcw size={13}/></button>}{has('USER_MANAGE')&&<button className="btn small danger" disabled={r.username==='admin'} title={t('delete')} onClick={e=>{e.stopPropagation();remove('users',r.id)}}><Trash2 size={13}/></button>}</div>}
 ]
 const roleCols=[
  {key:'code',label:t('roleCode'),render:r=><div className="cell-main"><b className="mono">{r.code}</b><small>{r.systemRole?t('systemRole'):t('customRole')}</small></div>},
  {key:'name',label:t('name'),render:r=><div className="cell-main"><b>{lang==='zh'?r.nameZh:r.nameEn}</b><small>{(lang==='zh'?r.descriptionZh:r.descriptionEn)||'—'}</small></div>},
  {key:'dataScope',label:t('dataScope'),render:r=>t(`dataScope_${r.dataScope||'ALL'}`)},
  {key:'userCount',label:t('assignedUsers'),render:r=>r.userCount},
  {key:'permissions',label:t('permissions'),render:r=>r.permissions?.length||0},
  {key:'enabled',label:t('status'),render:r=><StatusBadge tone={r.enabled?'ok':'gray'}>{r.enabled?t('enabled'):t('disabled')}</StatusBadge>},
  {key:'updatedAt',label:t('updatedAt'),render:r=>fmtDate(r.updatedAt,lang)},
  {key:'actions',label:t('action'),render:r=><div className="row-actions">{has('ROLE_MANAGE')&&<><button className="btn small" onClick={e=>{e.stopPropagation();openRole(r)}}>{t('edit')}</button><button className="btn small" title={t('cloneRole')} onClick={e=>{e.stopPropagation();cloneRole(r)}}><Copy size={13}/></button><button className="btn small danger" disabled={r.systemRole||r.userCount>0} title={t('delete')} onClick={e=>{e.stopPropagation();remove('roles',r.id)}}><Trash2 size={13}/></button></>}</div>}
 ]
 const lockedCount=users.filter(x=>x.locked).length
 return <>
  <PageHeader title={t('accessControl')}>{((tab==='users'&&has('USER_MANAGE'))||(tab==='roles'&&has('ROLE_MANAGE')))&&<button className="btn primary" onClick={()=>tab==='users'?setUserForm({...emptyUser,roleId:enabledRoles[0]?.id||'',roleIds:enabledRoles[0]?.id?[enabledRoles[0].id]:[]}):setRoleForm({...emptyRole})}><Plus size={15}/>{tab==='users'?t('newUser'):t('newRole')}</button>}</PageHeader>
  <div className="access-summary"><div><UserRound/><span>{t('users')}</span><b>{users.length}</b></div><div><ShieldCheck/><span>{t('roles')}</span><b>{roles.length}</b></div><div><KeyRound/><span>{t('permissions')}</span><b>{permissions.length}</b></div><div><LockKeyhole/><span>{t('lockedAccounts')}</span><b>{lockedCount}</b></div></div>
  <div className="tabs"><button className={tab==='users'?'active':''} onClick={()=>{setTab('users');setQuery('');setStateFilter('ALL')}}>{t('users')}<span>{users.length}</span></button><button className={tab==='roles'?'active':''} onClick={()=>{setTab('roles');setQuery('');setStateFilter('ALL')}}>{t('roles')}<span>{roles.length}</span></button></div>
  <div className="toolbar access-toolbar"><label className="search-with-icon"><Search size={15}/><input value={query} placeholder={t(tab==='users'?'searchUsers':'searchRoles')} onChange={e=>setQuery(e.target.value)}/></label>{tab==='users'&&<select value={roleFilter} onChange={e=>setRoleFilter(e.target.value)}><option value="ALL">{t('all')} · {t('roles')}</option>{roles.map(r=><option value={String(r.id)} key={r.id}>{lang==='zh'?r.nameZh:r.nameEn}</option>)}</select>}<select value={stateFilter} onChange={e=>setStateFilter(e.target.value)}><option value="ALL">{t('all')} · {t('status')}</option><option value="ENABLED">{t('enabled')}</option><option value="DISABLED">{t('disabled')}</option>{tab==='users'&&<option value="LOCKED">{t('locked')}</option>}</select></div>
  <DataTable columns={tab==='users'?userCols:roleCols} rows={tab==='users'?filteredUsers:filteredRoles} onRowClick={tab==='users'?(has('USER_MANAGE')?openUser:undefined):(has('ROLE_MANAGE')?openRole:undefined)}/>

  <Modal open={!!userForm} size="lg" title={userForm?.id?t('editUser'):t('newUser')} onClose={()=>setUserForm(null)} footer={<><button className="btn" onClick={()=>setUserForm(null)}>{t('cancel')}</button><button className="btn primary" disabled={busy||!userForm?.username||!userForm?.displayName||!userForm?.roleIds?.length} onClick={saveUser}>{t('save')}</button></>}>
   <div className="form-grid">
    <label className="form-field"><span>{t('username')}</span><input value={userForm?.username||''} onChange={e=>setUserForm({...userForm,username:e.target.value})}/></label>
    <label className="form-field"><span>{t('displayName')}</span><input value={userForm?.displayName||''} onChange={e=>setUserForm({...userForm,displayName:e.target.value})}/></label>
    <label className="form-field"><span>{t('employeeNo')}</span><input value={userForm?.employeeNo||''} onChange={e=>setUserForm({...userForm,employeeNo:e.target.value})}/></label>
    <label className="form-field"><span>{t('department')}</span><input value={userForm?.department||''} onChange={e=>setUserForm({...userForm,department:e.target.value})}/></label>
    <label className="form-field"><span>{t('email')}</span><input type="email" value={userForm?.email||''} onChange={e=>setUserForm({...userForm,email:e.target.value})}/></label>
    <label className="form-field"><span>{t('phone')}</span><input value={userForm?.phone||''} onChange={e=>setUserForm({...userForm,phone:e.target.value})}/></label>
    <label className="form-field"><span>{t('accountType')}</span><select value={userForm?.accountType||'LOCAL'} onChange={e=>setUserForm({...userForm,accountType:e.target.value})}>{['LOCAL','DIRECTORY','SERVICE'].map(x=><option value={x} key={x}>{t(`accountType_${x}`)}</option>)}</select></label>
    <div className="form-field full"><span>{t('assignedRoles')} *</span><div className="role-assignment-grid">{enabledRoles.map(role=>{const checked=(userForm?.roleIds||[]).map(Number).includes(role.id);return <label className={`role-assignment ${checked?'selected':''}`} key={role.id}><input type="checkbox" checked={checked} onChange={e=>toggleUserRole(role.id,e.target.checked)}/><span><b>{lang==='zh'?role.nameZh:role.nameEn}</b><small>{role.code} · {t(`dataScope_${role.dataScope||'ALL'}`)} · {role.permissions?.length||0} {t('permissions')}</small></span></label>})}</div></div>
    <label className="form-field full"><span>{userForm?.id?t('newPasswordOptional'):t('initialPassword')}</span><input type="password" value={userForm?.password||''} placeholder="Gazellio@123" onChange={e=>setUserForm({...userForm,password:e.target.value})}/></label>
    <label className="check-field full"><input type="checkbox" checked={!!userForm?.enabled} onChange={e=>setUserForm({...userForm,enabled:e.target.checked})}/>{t('enabled')}</label>
    {userForm?.id&&<div className="account-facts full"><span><small>{t('lastLogin')}</small><b>{fmtDate(userForm.lastLoginAt,lang)}</b></span><span><small>{t('passwordChangedAt')}</small><b>{fmtDate(userForm.passwordChangedAt,lang)}</b></span><span><small>{t('failedLoginAttempts')}</small><b>{userForm.failedLoginAttempts||0}</b></span><span><small>{t('accountStatus')}</small><b>{userForm.locked?t('locked'):userForm.enabled?t('enabled'):t('disabled')}</b></span></div>}
   </div>
  </Modal>

  <Modal open={!!roleForm} size="lg" title={roleForm?.id?t('editRole'):t('newRole')} onClose={()=>setRoleForm(null)} footer={<><button className="btn" onClick={()=>setRoleForm(null)}>{t('cancel')}</button><button className="btn primary" disabled={busy||!roleForm?.code||!roleForm?.nameZh||!roleForm?.nameEn} onClick={saveRole}>{t('save')}</button></>}>
   <div className="form-grid role-editor-form">
    <label className="form-field"><span>{t('roleCode')}</span><input disabled={!!roleForm?.systemRole} value={roleForm?.code||''} onChange={e=>setRoleForm({...roleForm,code:e.target.value})}/></label>
    <label className="form-field"><span>{t('dataScope')}</span><select value={roleForm?.dataScope||'ALL'} onChange={e=>setRoleForm({...roleForm,dataScope:e.target.value})}>{['ALL','BUSINESS_SERVICE','OWNED_ASSETS'].map(x=><option value={x} key={x}>{t(`dataScope_${x}`)}</option>)}</select></label>
    <label className="form-field"><span>{t('titleZh')}</span><input value={roleForm?.nameZh||''} onChange={e=>setRoleForm({...roleForm,nameZh:e.target.value})}/></label>
    <label className="form-field"><span>{t('titleEn')}</span><input value={roleForm?.nameEn||''} onChange={e=>setRoleForm({...roleForm,nameEn:e.target.value})}/></label>
    <label className="form-field"><span>{t('description')} · {t('chinese')}</span><textarea rows="2" value={roleForm?.descriptionZh||''} onChange={e=>setRoleForm({...roleForm,descriptionZh:e.target.value})}/></label>
    <label className="form-field"><span>{t('description')} · {t('english')}</span><textarea rows="2" value={roleForm?.descriptionEn||''} onChange={e=>setRoleForm({...roleForm,descriptionEn:e.target.value})}/></label>
    <label className="check-field full"><input type="checkbox" checked={!!roleForm?.enabled} onChange={e=>setRoleForm({...roleForm,enabled:e.target.checked})}/>{t('enabled')}</label>
    <div className="permission-editor full"><div className="permission-editor-head"><b>{t('permissionMatrix')}</b><span><button className="btn small" type="button" onClick={()=>setRoleForm({...roleForm,permissions:[...permissions]})}>{t('selectAll')}</button><button className="btn small" type="button" onClick={()=>setRoleForm({...roleForm,permissions:[]})}>{t('clear')}</button></span></div>{grouped.map(({group,items})=>{const selected=items.filter(x=>roleForm?.permissions?.includes(x)).length;return <section key={group}><label className="check-field permission-group"><input type="checkbox" checked={selected===items.length} onChange={e=>toggleGroup(items,e.target.checked)}/><b>{t(`permissionGroup_${group}`)}</b><small>{selected}/{items.length}</small></label><div>{items.map(permission=><label className="check-field" key={permission}><input type="checkbox" checked={roleForm?.permissions?.includes(permission)||false} onChange={e=>togglePermission(permission,e.target.checked)}/>{t(`permission_${permission}`)}</label>)}</div></section>})}</div>
   </div>
  </Modal>
 </>
}
