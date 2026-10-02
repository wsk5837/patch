package com.gazellio.platform.security;

import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class McpAuthenticationFilter extends OncePerRequestFilter {
    private final UserAccountRepository users;
    private final DatabaseUserDetailsService userDetails;
    @Value("${app.mcp.api-token:}") private String configuredToken;
    @Value("${app.mcp.service-username:ai-reader}") private String serviceUsername;
    @Value("${app.mcp.trusted-user-header:false}") private boolean trustedUserHeader;

    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        String path=request.getRequestURI();
        return !(path.equals("/mcp")||path.startsWith("/mcp/"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String authorization=request.getHeader("Authorization");
        if(configuredToken==null||configuredToken.isBlank()){
            error(response,503,"MCP 服务尚未配置");return;
        }
        if(authorization==null||!authorization.startsWith("Bearer ")
                ||!constantTimeEquals(configuredToken,authorization.substring(7))){
            error(response,401,"MCP Token 缺失或无效");return;
        }
        String delegated=trustedUserHeader?trim(request.getHeader("X-ANOWX-User")):null;
        String username=delegated==null?serviceUsername:delegated;
        UserAccount account=users.findByUsername(username).orElse(null);
        if(account==null||!account.isEnabled()||account.isLocked()){
            error(response,403,"MCP 调用身份不存在、已停用或已锁定");return;
        }
        UserDetails principal=userDetails.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication=new UsernamePasswordAuthenticationToken(
                principal,null,principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try{chain.doFilter(request,response);}finally{SecurityContextHolder.clearContext();}
    }

    private boolean constantTimeEquals(String expected,String actual){
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));
    }
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private void error(HttpServletResponse response,int status,String message)throws IOException{
        response.setStatus(status);response.setCharacterEncoding(StandardCharsets.UTF_8.name());response.setContentType("application/json");
        response.getWriter().write("{\"error\":\""+message+"\"}");
    }
}
