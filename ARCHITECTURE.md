# Gazellio Architecture

## Runtime topology

- `gazellio-web`: React/Vite UI served by Node/Express. Browser requests stay on the web origin; `/api` is proxied to the Java service over Render private networking.
- `gazellio-api`: Java 21 / Spring Boot REST API. Owns authentication and all business state transitions.
- `gazellio-db`: PostgreSQL. Persists vulnerabilities, findings, assets, patches, tasks, approvals, orchestration runs, execution steps, settings and audit events.

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
2. A resolved finding detected again becomes reopened.
3. A valid finding can be remediated, marked false-positive, or exempted until a defined expiry date. Expired exemptions can be reopened by a later scan.
4. CMDB ownership is copied to the finding/task when remediation begins.
5. Patch execution follows environment gates: test -> application validation -> targeted rescan -> release approval -> pre-production -> validation -> rescan -> production -> validation -> rescan.
6. Failed application validation or failed rescan returns the task to that environment's patch stage instead of silently advancing.
7. Final approval starts implementation. The release/change remains implementing until production rescan succeeds, then it closes.
8. Successful patch runs write installed/verified patch state back against affected CMDB assets.
9. A vulnerability finding closes only after production validation and targeted production rescan pass.
10. Long-running patch executions are persistent orchestration runs; each node has its own database state and can be observed, paused, resumed or rolled back.
11. The end-to-end business flow exists as an orchestration template. Normal product pages do not duplicate it as explanatory text.

## Scanner integration

`/api/agent/*` supports scanner-agent registration, heartbeat and result ingestion. The built-in scheduler is a safe demo provider so the Render deployment can be exercised without scanning arbitrary networks from a public cloud service.
