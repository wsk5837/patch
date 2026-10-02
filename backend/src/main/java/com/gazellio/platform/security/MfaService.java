package com.gazellio.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/** RFC 6238 TOTP support with the shared secret encrypted at rest. */
@Service
public class MfaService {
    private static final char[] BASE32="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM=new SecureRandom();
    private final SecretKeySpec encryptionKey;

    public MfaService(@Value("${app.mfa-encryption-key:${app.jwt-secret}}") String secret){
        try{
            byte[] key=MessageDigest.getInstance("SHA-256").digest((secret+":gazellio-mfa").getBytes(StandardCharsets.UTF_8));
            this.encryptionKey=new SecretKeySpec(key,"AES");
        }catch(Exception ex){throw new IllegalStateException("Unable to initialize MFA encryption",ex);}
    }

    public String newEncryptedSecret(){return encrypt(newSecret());}
    public String displaySecret(String encrypted){return decrypt(encrypted);}
    public String setupUri(String username,String encrypted){
        String account=url("Gazellio:"+username),issuer=url("Gazellio");
        return "otpauth://totp/"+account+"?secret="+displaySecret(encrypted)+"&issuer="+issuer+"&algorithm=SHA1&digits=6&period=30";
    }
    public boolean verify(String encrypted,String code){
        if(encrypted==null||code==null||!code.trim().matches("\\d{6}"))return false;
        long counter=Instant.now().getEpochSecond()/30;
        String normalized=code.trim();
        for(long offset=-1;offset<=1;offset++)if(totp(displaySecret(encrypted),counter+offset).equals(normalized))return true;
        return false;
    }

    String totp(String base32,long counter){
        try{
            Mac mac=Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(base32),"HmacSHA1"));
            byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset=hash[hash.length-1]&0x0f;
            int binary=((hash[offset]&0x7f)<<24)|((hash[offset+1]&0xff)<<16)|((hash[offset+2]&0xff)<<8)|(hash[offset+3]&0xff);
            return String.format(Locale.ROOT,"%06d",binary%1_000_000);
        }catch(Exception ex){throw new IllegalStateException("Unable to calculate MFA code",ex);}
    }

    private String newSecret(){byte[] bytes=new byte[20];RANDOM.nextBytes(bytes);return encodeBase32(bytes);}
    private String encrypt(String value){
        try{
            byte[] iv=new byte[12];RANDOM.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,encryptionKey,new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload=ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array();
            return "v1:"+Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        }catch(Exception ex){throw new IllegalStateException("Unable to encrypt MFA secret",ex);}
    }
    private String decrypt(String value){
        try{
            if(value==null||!value.startsWith("v1:"))throw new IllegalArgumentException("Unsupported MFA secret");
            byte[] payload=Base64.getUrlDecoder().decode(value.substring(3));byte[] iv=new byte[12],encrypted=new byte[payload.length-12];
            System.arraycopy(payload,0,iv,0,12);System.arraycopy(payload,12,encrypted,0,encrypted.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey,new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        }catch(Exception ex){throw new IllegalStateException("Unable to decrypt MFA secret",ex);}
    }
    private String encodeBase32(byte[] input){
        StringBuilder out=new StringBuilder();int buffer=0,bits=0;
        for(byte value:input){buffer=(buffer<<8)|(value&0xff);bits+=8;while(bits>=5){out.append(BASE32[(buffer>>(bits-5))&31]);bits-=5;}}
        if(bits>0)out.append(BASE32[(buffer<<(5-bits))&31]);return out.toString();
    }
    private byte[] decodeBase32(String input){
        String value=input.replace("=","").replace(" ","").toUpperCase(Locale.ROOT);byte[] out=new byte[value.length()*5/8];int buffer=0,bits=0,index=0;
        for(char ch:value.toCharArray()){int digit="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(ch);if(digit<0)throw new IllegalArgumentException("Invalid Base32 secret");buffer=(buffer<<5)|digit;bits+=5;if(bits>=8){out[index++]=(byte)((buffer>>(bits-8))&0xff);bits-=8;}}
        return out;
    }
    private String url(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
}
