package com.gazellio.platform.security;

import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class McpAuthenticationFilterTest {
    private final UserAccountRepository users=mock(UserAccountRepository.class);
    private final DatabaseUserDetailsService details=mock(DatabaseUserDetailsService.class);
    private final McpAuthenticationFilter filter=new McpAuthenticationFilter(users,details);

    McpAuthenticationFilterTest(){
        ReflectionTestUtils.setField(filter,"configuredToken","test-mcp-token");
        ReflectionTestUtils.setField(filter,"serviceUsername","ai-reader");
        ReflectionTestUtils.setField(filter,"trustedUserHeader",false);
    }

    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void rejectsMissingBearerToken() throws Exception {
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/mcp/");
        MockHttpServletResponse response=new MockHttpServletResponse();
        FilterChain chain=mock(FilterChain.class);
        filter.doFilter(request,response,chain);
        assertEquals(401,response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void authenticatesConfiguredReadOnlyServiceAccount() throws Exception {
        UserAccount account=UserAccount.builder().username("ai-reader").enabled(true).locked(false).build();
        when(users.findByUsername("ai-reader")).thenReturn(Optional.of(account));
        when(details.loadUserByUsername("ai-reader")).thenReturn(User.withUsername("ai-reader").password("-").authorities("REPORT_VIEW").build());
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/mcp/");
        request.addHeader("Authorization","Bearer test-mcp-token");
        MockHttpServletResponse response=new MockHttpServletResponse();
        FilterChain chain=mock(FilterChain.class);
        filter.doFilter(request,response,chain);
        assertEquals(200,response.getStatus());
        verify(chain).doFilter(request,response);
    }
}
