# Gazellio Architecture

## Runtime topology

- `gazellio-web`: React/Vite UI. Render publishes `frontend/dist` as an independent CDN-backed static site; the company-server Docker Compose profile serves the same files through Node/Express and proxies `/api` on one origin.
- `gazellio`: Java 21 / Spring Boot REST API on Render. Owns authentication and all business state transitions. The Render static build receives its public hostname through `VITE_API_BASE_URL`.
- `gazellio-db`: PostgreSQL. Persists vulnerabilities, findings, assets, patches, tasks, approvals, orchestration runs, execution steps, settings and audit events.

Render sets `rootDir: frontend` and `rootDir: backend` for the two services. A commit that only touches one directory therefore does not rebuild the other service. The static-site SPA rewrite sends browser routes to `index.html`; API calls use the separately configured HTTPS origin.

## Product domains

- Vulnerability intelligence and CVE library
- Scan jobs and scanner agents
- Findings and risk decisions
- CMDB assets and ownership
- Patch catalog and patch distribution servers
- Remediation tasks
- Release/change approval
- Patch automation and orchestration traces
- Reports, settings and audit

## Business invariants

1. A finding is unique by `asset + CVE`; repeated scans update the same finding.
2. A scan creates or updates a finding only. A security incident is created after an operator explicitly confirms a new or reopened finding.
3. A resolved finding detected again becomes reopened.
4. A valid finding can be remediated, marked false-positive, or exempted until a defined expiry date. Expired exemptions can be reopened by a later scan.
5. CMDB ownership is copied to the finding/task when remediation begins.
6. Patch execution follows environment gates: test -> application validation -> targeted rescan -> release approval -> pre-production -> validation -> rescan -> production -> validation -> rescan.
7. Failed application validation or failed rescan returns the task to that environment's patch stage instead of silently advancing.
8. Final approval starts implementation. The release/change remains implementing until production rescan succeeds, then it closes.
9. CMDB is read-only. Successful patch runs store installed/verified patch state in Gazellio against the mirrored CMDB asset identifier; no CMDB API write is performed.
10. A vulnerability finding closes only after production validation and targeted production rescan pass.
11. Long-running patch executions are persistent orchestration runs; each node has its own database state and can be observed, paused, resumed or rolled back.
12. The end-to-end business flow exists as an orchestration template. Normal product pages do not duplicate it as explanatory text.

## Scanner integration

`/api/agent/*` supports scanner-agent registration, heartbeat and result ingestion. The built-in scheduler is a safe demo provider so the Render deployment can be exercised without scanning arbitrary networks from a public cloud service.
