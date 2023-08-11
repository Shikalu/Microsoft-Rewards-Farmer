package de.scarpex.mrf.chromedriver;

import java.net.*;

public class ProxyUtils {
    /**
     * url代理，当前使用本机clash全局代理
     *
     * @param url url
     * @return {@link URLConnection}
     */

    public static URLConnection urlProxy(String url) {
        //设置代理服务器
        SocketAddress sa = new InetSocketAddress("127.0.0.1", 7890);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, sa);
        //以代理方式打开链接
        try {
            return new URL(url).openConnection(proxy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
