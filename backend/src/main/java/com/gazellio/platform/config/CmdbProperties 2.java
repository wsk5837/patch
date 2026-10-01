package com.gazellio.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix="app.cmdb")
@Getter @Setter
public class CmdbProperties {
    private boolean enabled;
    private String baseUrl="https://zypoc.gazellio.com/backend-server";
    private String clientId;
    private String clientSecret;
    private int pageSize=100;
    private boolean syncOnStartup=true;
    private boolean replaceLocalAssets=true;
    private List<String> classKeys=new ArrayList<>(List.of(
            "virtual_host","physics_machine","mysql","oracle","redis","postgresql","mongodb",
            "tomcat","nginx","firewall","swtich","router","load_balance","nas","KingBase"));

    public boolean configured(){return enabled&&notBlank(baseUrl)&&notBlank(clientId)&&notBlank(clientSecret);}
    public String normalizedBaseUrl(){return baseUrl==null?"":baseUrl.trim().replaceAll("/+$","");}
    private boolean notBlank(String value){return value!=null&&!value.isBlank();}
}
