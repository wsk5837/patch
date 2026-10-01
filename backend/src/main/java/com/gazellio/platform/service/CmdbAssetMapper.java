package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gazellio.platform.model.Asset;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.gazellio.platform.model.Enums.EnvironmentType;

@Component
public class CmdbAssetMapper {
    private static final Pattern IPV4=Pattern.compile("(?<!\\d)(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}(?!\\d)");
    private static final Map<String,String> CLASS_NAMES=Map.ofEntries(
            Map.entry("virtual_host","虚拟机"),Map.entry("physics_machine","物理机"),Map.entry("mysql","MySQL"),
            Map.entry("oracle","Oracle"),Map.entry("redis","Redis"),Map.entry("postgresql","PostgreSQL"),
            Map.entry("mongodb","MongoDB"),Map.entry("tomcat","Apache Tomcat"),Map.entry("nginx","Nginx"),
            Map.entry("firewall","防火墙"),Map.entry("swtich","交换机"),Map.entry("router","路由器"),
            Map.entry("load_balance","负载均衡"),Map.entry("nas","NAS"),Map.entry("KingBase","人大金仓"),
            Map.entry("application","应用模块"),Map.entry("service","服务"));

    public Asset apply(Asset asset,JsonNode row,String classKey,String className,Instant syncedAt){
        String itemId=text(row,"id");
        String ip=ip(row);
        String resolvedClassName=first(className,text(row,"ci#classname"),CLASS_NAMES.get(classKey),classKey);
        String product=productName(classKey,resolvedClassName);
        String version=first(text(row,"database_app_version"),text(row,"middleware_version"),text(row,"version"));
        String packageInventory=first(text(row,"virtual_host_packages"),text(row,"installed_software"),display(row.get("virtual_host_packages")));
        String name=first(text(row,"name"),text(row,"ci#name"),text(row,"host_name"),text(row,"application_name"),
                text(row,"business_name"),text(row,"middleware_name"),text(row,"database_hostname"),ip,product+" "+shortId(itemId));
        String hostname=first(text(row,"host_name"),text(row,"database_hostname"),text(row,"middleware_name"),name);
        String env=first(text(row,"env_#value"),text(row,"purpose_#value"),text(row,"purpose"),text(row,"zone_#value"));
        String environmentSignal=first(env,hostname,name);
        String os=cleanReference(first(text(row,"virtual_host_os_#value"),text(row,"host_os_#value"),text(row,"os_#value"),
                text(row,"virtual_host_os"),text(row,"host_os"),text(row,"operating_system")));
        String service=first(text(row,"belonging_system_#value"),text(row,"application_subsystem_#value"),
                text(row,"business_name"),text(row,"responsible_unit_#value"),text(row,"responsible_unit"),resolvedClassName);
        String owner=owner(row);
        boolean enabled=!row.has("enabled")||row.path("enabled").asBoolean(true);
        asset.setCmdbItemId(itemId);asset.setCmdbClassKey(classKey);asset.setCmdbClassName(resolvedClassName);
        asset.setCmdbState(first(text(row,"state_#value"),text(row,"state")));asset.setCmdbLocked(nullableBoolean(row,"locked"));
        asset.setCmdbEnabled(enabled);asset.setCmdbAutoDiscovery(nullableBoolean(row,"is_auto_discovery"));
        asset.setCmdbUpdatedAt(parseInstant(text(row,"updated_at")));asset.setCmdbSyncedAt(syncedAt);
        asset.setSourceSystem("CMDB");asset.setName(limit(name,180));asset.setHostname(limit(hostname,160));
        asset.setIpAddress(limit(ip,80));asset.setNetworkSegment(segment(ip));asset.setAssetType(assetType(classKey));
        asset.setZone(limit(first(text(row,"machine_room_#value"),text(row,"machine_room"),text(row,"district_#value"),
                text(row,"virtual_host_host_type_#value"),env),80));
        boolean explicitExposure=Boolean.TRUE.equals(nullableBoolean(row,"internet_exposed"))||Boolean.TRUE.equals(nullableBoolean(row,"is_internet_exposed"));
        asset.setInternetExposed(explicitExposure||isPublicIpv4(ip)||containsAny(String.join(" ",List.of(value(env),value(name),value(service))),"internet","互联网","公网"));
        asset.setOsName(limit(first(os,product),100));asset.setOsVersion(limit(version,80));asset.setEnvironment(environment(environmentSignal));
        asset.setBusinessService(limit(service,160));asset.setOwnerName(limit(owner,120));asset.setCriticality(criticality(row));
        asset.setInstalledProducts(limit(joinDistinct(version==null?product:product+" "+version,packageInventory),4000));asset.setLastSeenAt(syncedAt);
        asset.setActive(enabled);
        return asset;
    }

    public String proposedCode(JsonNode row,String classKey){
        String explicit=first(text(row,"ci_number"),text(row,"fa_cmdb_no"),text(row,"serial_number"));
        String base=explicit==null?classKey+"-"+shortId(text(row,"id")):classKey+"-"+explicit;
        return limit(base.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]+","-"),120);
    }
    public String className(String classKey){return CLASS_NAMES.getOrDefault(classKey,classKey);}

    private String productName(String key,String fallback){return switch(key){case "tomcat"->"Apache Tomcat";case "mysql"->"Oracle MySQL";case "postgresql"->"PostgreSQL";case "mongodb"->"MongoDB";case "KingBase"->"人大金仓";default->fallback;};}
    private String assetType(String key){return switch(key){case "virtual_host"->"VIRTUAL_MACHINE";case "physics_machine"->"PHYSICAL_SERVER";case "mysql","oracle","redis","postgresql","mongodb","KingBase"->"DATABASE";case "tomcat","nginx"->"MIDDLEWARE";case "application"->"APPLICATION_PLATFORM";case "service"->"APPLICATION_RUNTIME";case "firewall","swtich","router","load_balance"->"NETWORK_DEVICE";case "nas"->"FILE_SERVICE";default->"UNCLASSIFIED";};}
    private EnvironmentType environment(String value){String v=lower(value);if(containsAny(v,"prod","生产"))return EnvironmentType.PROD;if(containsAny(v,"preprod","uat","预生产"))return EnvironmentType.PREPROD;if(containsAny(v,"test","测试"))return EnvironmentType.TEST;if(containsAny(v,"dev","开发"))return EnvironmentType.DEV;return EnvironmentType.PROD;}
    private Integer criticality(JsonNode row){String raw=first(text(row,"device_level_#value"),text(row,"device_level"));try{int level=Integer.parseInt(raw);return Math.max(1,Math.min(5,6-level));}catch(Exception ignored){return 3;}}
    private String owner(JsonNode row){
        JsonNode managers=row.get("manager_items");List<String> names=new ArrayList<>();
        if(managers!=null&&managers.isArray())for(JsonNode item:managers){String value=first(text(item,"name"),text(item,"label"),text(item,"username"),item.isTextual()?item.asText():null);if(value!=null)names.add(value);}
        return names.isEmpty()?first(text(row,"director_#value"),text(row,"director"),text(row,"responsible_person_#value"),text(row,"responsible_unit_#value"),text(row,"responsible_unit")):String.join("、",names);
    }
    private String ip(JsonNode row){
        for(String key:List.of("virtual_host_front_card_ip","host_ip","manage_address","database_ip_address","middleware_ip_addr","net_devise_IP","ip_address")){String hit=extractIp(text(row,key));if(hit!=null)return hit;}
        Iterator<String> fields=row.fieldNames();while(fields.hasNext()){String key=fields.next();if(lower(key).contains("ip")||lower(key).contains("address")){String hit=extractIp(text(row,key));if(hit!=null)return hit;}}
        return null;
    }
    private String extractIp(String value){if(value==null)return null;Matcher matcher=IPV4.matcher(value);return matcher.find()?matcher.group():null;}
    private String segment(String ip){if(ip==null)return null;String[] p=ip.split("\\.");return p.length==4?p[0]+"."+p[1]+"."+p[2]+".0/24":null;}
    private boolean isPublicIpv4(String ip){
        if(ip==null)return false;String[] p=ip.split("\\.");if(p.length!=4)return false;
        try{int a=Integer.parseInt(p[0]),b=Integer.parseInt(p[1]);return !(a==10||a==127||a==0||(a==172&&b>=16&&b<=31)||(a==192&&b==168)||(a==169&&b==254)||a>=224);}catch(Exception ignored){return false;}
    }
    private Boolean nullableBoolean(JsonNode row,String key){return row.has(key)&&!row.get(key).isNull()?row.get(key).asBoolean():null;}
    private String cleanReference(String value){return value!=null&&value.matches("[0-9a-fA-F-]{30,}")?null:value;}
    private String display(JsonNode value){if(value==null||value.isNull())return null;if(value.isValueNode()){String text=value.asText().trim();return text.isBlank()?null:text;}return value.size()==0?null:value.toString();}
    private String joinDistinct(String first,String second){if(second==null||second.isBlank())return first;if(first==null||first.isBlank()||second.toLowerCase(Locale.ROOT).contains(first.toLowerCase(Locale.ROOT)))return second;return first+", "+second;}
    private Instant parseInstant(String value){if(value==null)return null;try{return Instant.parse(value);}catch(Exception ignored){}try{return OffsetDateTime.parse(value).toInstant();}catch(Exception ignored){}try{return LocalDateTime.parse(value.replace(' ','T'),DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);}catch(Exception ignored){return null;}}
    private static String text(JsonNode node,String key){if(node==null||key==null)return null;JsonNode value=node.get(key);if(value==null||value.isNull()||value.isContainerNode())return null;String result=value.asText().trim();return result.isBlank()||"null".equalsIgnoreCase(result)?null:result;}
    private static String first(String...values){for(String value:values)if(value!=null&&!value.isBlank())return value.trim();return null;}
    private static String shortId(String value){if(value==null||value.isBlank())return "UNKNOWN";return value.replace("-","").substring(0,Math.min(10,value.replace("-","").length()));}
    private static String limit(String value,int max){return value==null?null:value.substring(0,Math.min(max,value.length()));}
    private static String lower(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
    private static String value(String value){return value==null?"":value;}
    private static boolean containsAny(String value,String...needles){String hay=lower(value);for(String needle:needles)if(hay.contains(lower(needle)))return true;return false;}
}
