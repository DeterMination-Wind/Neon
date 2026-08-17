package modupdater.features;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;

/**
 * 镜像地址运行时解析工具。
 * 镜像服务即将迁移,play.mindustry.men 的 A 记录届时指向新服务器;
 * 镜像服务器只接受以 IP 作为 authority 的 HTTP 请求(不接受域名 Host 头,会被拦截页拒绝),
 * 所以客户端必须自己解析域名取 IPv4、把 IP 填充进 URL 后再请求。
 * 解析失败时回退用域名原样(由调用方直接使用返回值)。
 */
public final class PlayMirrorResolver{
    private static final String mirrorHost = "play.mindustry.men";

    private PlayMirrorResolver(){
    }

    /** 把 URL 中 play.mindustry.men 的 host 替换为运行时解析出的第一个 IPv4,保留端口/路径;解析失败返回原 URL。 */
    public static String resolveHost(String url){
        String ip = resolveIpv4();
        if(ip == null) return url;

        try{
            URI uri = new URI(url);
            if(uri.getHost() == null || !uri.getHost().equalsIgnoreCase(mirrorHost)) return url;
            return new URI(uri.getScheme(), uri.getUserInfo(), ip, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        }catch(Throwable t){
            return url;
        }
    }

    /** 系统 DNS 自带缓存,每次调用解析的开销可忽略;取第一个 IPv4,拿不到返回 null。 */
    private static String resolveIpv4(){
        try{
            for(InetAddress address : InetAddress.getAllByName(mirrorHost)){
                if(address instanceof Inet4Address) return address.getHostAddress();
            }
        }catch(Throwable ignored){
        }
        return null;
    }
}
