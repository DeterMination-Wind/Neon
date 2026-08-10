package modupdater.features;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;

/**
 * Runtime resolver for mirror URLs.
 * The mirror service is about to migrate: the A record of play.mindustry.men will
 * point to the new server. The mirror only accepts requests with the IP as the
 * authority (requests carrying the domain Host header are blocked by an
 * interception page), so clients must resolve the domain to an IPv4 address and
 * fill the IP into the URL before requesting. Falls back to the original URL
 * (domain as-is) when resolution fails.
 */
public final class PlayMirrorResolver{
    private static final String mirrorHost = "play.mindustry.men";

    private PlayMirrorResolver(){
    }

    /** Replaces the play.mindustry.men host in the URL with the first IPv4 resolved at runtime, keeping port/path; returns the original URL if resolution fails. */
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

    /** System DNS has its own cache, so resolving on every call is cheap; returns the first IPv4, or null if none is available. */
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
