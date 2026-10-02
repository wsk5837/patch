import React from 'react'
import ReactDOM from 'react-dom/client'
import {BrowserRouter,Navigate,Route,Routes} from 'react-router-dom'
import {AuthProvider,useAuth} from './contexts/AuthContext'
import {I18nProvider} from './contexts/I18nContext'
import {ToastProvider} from './components/ToastContext'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import VulnerabilityLibraryPage from './pages/VulnerabilityLibraryPage'
import VulnerabilityDetailPage from './pages/VulnerabilityDetailPage'
import FindingsPage from './pages/FindingsPage'
import FindingDetailPage from './pages/FindingDetailPage'
import ScansPage from './pages/ScansPage'
import ScanDetailPage from './pages/ScanDetailPage'
import AssetsPage from './pages/AssetsPage'
import AssetDetailPage from './pages/AssetDetailPage'
import PatchesPage from './pages/PatchesPage'
import PatchDetailPage from './pages/PatchDetailPage'
import PatchCalendarPage from './pages/PatchCalendarPage'
import SecurityIncidentsPage from './pages/SecurityIncidentsPage'
import SecurityIncidentDetailPage from './pages/SecurityIncidentDetailPage'
import ChangeOrdersPage from './pages/ChangeOrdersPage'
import ChangeOrderDetailPage from './pages/ChangeOrderDetailPage'
import TasksPage from './pages/TasksPage'
import TaskDetailPage from './pages/TaskDetailPage'
import ApprovalsPage from './pages/ApprovalsPage'
import ApprovalDetailPage from './pages/ApprovalDetailPage'
import AutomationPage from './pages/AutomationPage'
import AutomationTemplateDetailPage from './pages/AutomationTemplateDetailPage'
import AutomationRunDetailPage from './pages/AutomationRunDetailPage'
import BatchPatchPage from './pages/BatchPatchPage'
import ReportsPage from './pages/ReportsPage'
import SettingsPage from './pages/SettingsPage'
import SlaMatrixPage from './pages/SlaMatrixPage'
import AuditPage from './pages/AuditPage'
import AccessControlPage from './pages/AccessControlPage'
import CompliancePage from './pages/CompliancePage'
import AiAssistantPage from './pages/AiAssistantPage'
import './styles.css'

function Protected({children}){const {authenticated}=useAuth();return authenticated?children:<Navigate to="/login" replace/>}
function Gate({permission,children}){const {has}=useAuth();return has(permission)?children:<div className="empty-state">403 · Access denied</div>}
const gate=(permission,element)=><Gate permission={permission}>{element}</Gate>

function AppRoutes(){
 const {authenticated}=useAuth()
 return <Routes>
  <Route path="/login" element={authenticated?<Navigate to="/" replace/>:<LoginPage/>}/>
  <Route element={<Protected><Layout/></Protected>}>
   <Route path="/" element={gate('DASHBOARD_VIEW',<DashboardPage/>)}/>
   <Route path="/vulnerabilities/library" element={gate('VULNERABILITY_VIEW',<VulnerabilityLibraryPage/>)}/>
   <Route path="/vulnerabilities/library/:cve" element={gate('VULNERABILITY_VIEW',<VulnerabilityDetailPage/>)}/>
   <Route path="/vulnerabilities/findings" element={gate('VULNERABILITY_VIEW',<FindingsPage/>)}/>
   <Route path="/vulnerabilities/findings/:id" element={gate('VULNERABILITY_VIEW',<FindingDetailPage/>)}/>
   <Route path="/scans" element={gate('SCAN_VIEW',<ScansPage/>)}/><Route path="/scans/:id" element={gate('SCAN_VIEW',<ScanDetailPage/>)}/>
   <Route path="/assets" element={gate('ASSET_VIEW',<AssetsPage/>)}/><Route path="/assets/:id" element={gate('ASSET_VIEW',<AssetDetailPage/>)}/>
   <Route path="/work-orders/incidents" element={gate('INCIDENT_VIEW',<SecurityIncidentsPage/>)}/><Route path="/work-orders/incidents/:id" element={gate('INCIDENT_VIEW',<SecurityIncidentDetailPage/>)}/>
   <Route path="/work-orders/changes" element={gate('CHANGE_VIEW',<ChangeOrdersPage/>)}/><Route path="/work-orders/changes/:id" element={gate('CHANGE_VIEW',<ChangeOrderDetailPage/>)}/>
   <Route path="/patches" element={gate('PATCH_VIEW',<PatchesPage/>)}/><Route path="/patches/calendar" element={gate('PATCH_VIEW',<PatchCalendarPage/>)}/><Route path="/patches/:id" element={gate('PATCH_VIEW',<PatchDetailPage/>)}/>
   <Route path="/tasks" element={gate('TASK_VIEW',<TasksPage/>)}/><Route path="/tasks/:id" element={gate('TASK_VIEW',<TaskDetailPage/>)}/>
   <Route path="/approvals" element={gate('APPROVAL_VIEW',<ApprovalsPage/>)}/><Route path="/approvals/:id" element={gate('APPROVAL_VIEW',<ApprovalDetailPage/>)}/>
   <Route path="/automation" element={gate('AUTOMATION_VIEW',<AutomationPage/>)}/><Route path="/automation/batch" element={gate('AUTOMATION_EXECUTE',<BatchPatchPage/>)}/><Route path="/automation/templates/:id" element={gate('AUTOMATION_VIEW',<AutomationTemplateDetailPage/>)}/><Route path="/automation/runs/:id" element={gate('AUTOMATION_VIEW',<AutomationRunDetailPage/>)}/>
   <Route path="/sla-matrix" element={gate('SETTINGS_VIEW',<SlaMatrixPage/>)}/>
   <Route path="/reports" element={gate('REPORT_VIEW',<ReportsPage/>)}/><Route path="/compliance" element={gate('COMPLIANCE_VIEW',<CompliancePage/>)}/><Route path="/audit" element={gate('AUDIT_VIEW',<AuditPage/>)}/><Route path="/settings" element={gate('SETTINGS_VIEW',<SettingsPage/>)}/><Route path="/settings/access" element={gate('USER_MANAGE',<AccessControlPage/>)}/>
   <Route path="/ai-assistant" element={gate('REPORT_VIEW',<AiAssistantPage/>)}/>
  </Route>
  <Route path="*" element={<Navigate to="/" replace/>}/>
 </Routes>
}

ReactDOM.createRoot(document.getElementById('root')).render(<React.StrictMode><I18nProvider><AuthProvider><ToastProvider><BrowserRouter><AppRoutes/></BrowserRouter></ToastProvider></AuthProvider></I18nProvider></React.StrictMode>)
