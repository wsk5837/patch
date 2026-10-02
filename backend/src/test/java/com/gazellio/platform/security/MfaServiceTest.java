package com.gazellio.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MfaServiceTest {
    @Test
    void generatesRfc6238CompatibleCodesAndEncryptsSecretsAtRest(){
        MfaService service=new MfaService("test-key-that-is-long-enough-for-encryption");
        assertEquals("287082",service.totp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",1));
        String encrypted=service.newEncryptedSecret();
        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains(service.displaySecret(encrypted)));
        assertEquals(32,service.displaySecret(encrypted).length());
    }
}
