package com.gazellio.platform.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React entry point for browser routes.
 *
 * <p>API and actuator paths are deliberately not included. Explicit route
 * mappings keep missing assets and unknown API endpoints as real 404s while
 * allowing BrowserRouter pages to be refreshed or opened directly.</p>
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/login",
            "/vulnerabilities/library",
            "/vulnerabilities/library/{cve}",
            "/vulnerabilities/findings",
            "/vulnerabilities/findings/{id}",
            "/scans",
            "/assets",
            "/patches",
            "/tasks",
            "/tasks/{id}",
            "/approvals",
            "/approvals/{id}",
            "/automation",
            "/automation/templates/{id}",
            "/automation/runs/{id}",
            "/reports",
            "/audit",
            "/settings"
    })
    public String forwardToReact() {
        return "forward:/index.html";
    }
}
