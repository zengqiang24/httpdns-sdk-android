package com.tencent.msdk.dns;

import android.content.Context;
import android.util.Log;
import com.tencent.msdk.dns.base.executor.DnsExecutors;
import com.tencent.msdk.dns.base.jni.JniWrapper;
import com.tencent.msdk.dns.base.log.DnsLog;
import com.tencent.msdk.dns.base.log.ILogNode;
import com.tencent.msdk.dns.base.utils.CommonUtils;
import com.tencent.msdk.dns.core.Const;
import com.tencent.msdk.dns.core.IpSet;

public class MSDKDnsResolver {
    public static final String DES_HTTP_CHANNEL = Const.DES_HTTP_CHANNEL;
    public static final String AES_HTTP_CHANNEL = Const.AES_HTTP_CHANNEL;
    public static final String HTTPS_CHANNEL = Const.HTTPS_CHANNEL;

    private static volatile MSDKDnsResolver sInstance = null;

    private static HttpDnsResponseObserver sHttpDnsResponseObserver = null;

    /**
     * 获取MSDKDnsResolver单例
     *
     * @return MSDKDnsResolver实例
     */
    public static MSDKDnsResolver getInstance() {
        if (null == sInstance) {
            synchronized (MSDKDnsResolver.class) {
                if (null == sInstance) {
                    sInstance = new MSDKDnsResolver();
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化SDK
     *
     * @param context <a href="https://developer.android.google.cn/reference/android/content/Context">Context</a>实例, SDK内部持有ApplicationContext用于监听网络切换等操作
     * @param appId   即SDK appId, 从<a href="https://console.cloud.tencent.com/HttpDNS">腾讯云官网</a>申请获得
     * @param debug   是否输出调试日志, true为输出, false不输出, SDK默认仅将日志通过logcat输出, tag统一使用HTTPDNS
     * @param dnsIp   由外部传入的dnsIp，可选："119.29.29.98"（仅支持 http 请求），"119.29.29.99"（仅支持 https 请求）以腾讯云文档（https://cloud.tencent.com/document/product/379/54976）提供的 IP 为准
     * @param timeout 域名解析请求超时时间, 单位为ms
     */
    public void init(Context context, String appId, String dnsIp, boolean debug, int timeout) {
        try {
            // NOTE: 兼容旧版本实现, 参数不合法时不crash
            init(context, appId, null, null, dnsIp, debug, timeout);
        } catch (Exception e) {
        }
    }

    /**
     * 初始化SDK
     *
     * @param context <a href="https://developer.android.google.cn/reference/android/content/Context">Context</a>实例, SDK内部持有ApplicationContext用于监听网络切换等操作
     * @param appId   即SDK appId, 从<a href="https://console.cloud.tencent.com/httpdns">腾讯云官网</a>申请获得
     * @param dnsId   即HTTPDNS服务的授权Id, 从<a href="https://console.cloud.tencent.com/httpdns">腾讯云官网</a>申请获得
     * @param dnsKey  即HTTPDNS服务的授权Id对应的加密密钥, 从<a href="https://console.cloud.tencent.com/httpdns">腾讯云官网</a>申请获得
     * @param dnsIp   由外部传入的dnsIp，可选："119.29.29.98"（仅支持 http 请求），"119.29.29.99"（仅支持 https 请求）以腾讯云文档（https://cloud.tencent.com/document/product/379/54976）提供的 IP 为准
     * @param debug   是否输出调试日志, true为输出, false不输出, SDK默认仅将日志通过logcat输出, tag统一使用HTTPDNS
     * @param timeout 域名解析请求超时时间, 单位为ms
     */
    public void init(Context context, String appId, String dnsId, String dnsKey, String dnsIp, boolean debug,
                     int timeout) {
        init(context, appId, dnsId, dnsKey, dnsIp, debug, timeout, Const.DES_HTTP_CHANNEL);
    }

    // channel可选AES_HTTP_CHANNEL，DES_HTTP_CHANNEL
    public void init(Context context, String appID, String dnsId, String dnsKey, String dnsIp, boolean debug,
                     int timeout, String channel) {
        DnsConfig.Builder dnsConfigBuilder =
                new DnsConfig
                        .Builder()
                        .logLevel(debug ? Log.VERBOSE : Log.WARN)
                        .appId(appID)
                        .timeoutMills(timeout);
        if (null != dnsIp) {
            dnsConfigBuilder.dnsIp(dnsIp);
        }
        if (null != dnsId) {
            dnsConfigBuilder.dnsId(dnsId);
        }
        if (null != dnsKey) {
            dnsConfigBuilder.dnsKey(dnsKey);
        }

        if (AES_HTTP_CHANNEL.equals(channel)) {
            // aes http
            dnsConfigBuilder.aesHttp();
        } else {
            // 默认des http
            dnsConfigBuilder.desHttp();
        }
        DnsConfig dnsConfig = dnsConfigBuilder.build();
        DnsService.init(context, dnsConfig);

        DnsLog.d("MSDKDnsResolver.init() called, ver:%s, channel:%s", BuildConfig.VERSION_NAME, channel);
    }

    /**
    * 初始化 HTTPDNS（自选加密方式）
    *
    * @param context 应用上下文，最好传入 ApplicationContext
    * @param appID 业务 appkey，即 SDK AppID，腾讯云官网（https://console.cloud.tencent.com/httpdns）申请获得，用于上报
    * @param dnsId dns解析id，即授权id，腾讯云官网（https://console.cloud.tencent.com/httpdns）申请获得，用于域名解析鉴权
    * @param dnsKey dns解析key，即授权id对应的 key（加密密钥），在申请 SDK 后的邮箱里，腾讯云官网（https://console.cloud.tencent.com/httpdns）申请获得，用于域名解析鉴权
    * @param dnsIp 由外部传入的dnsIp，可选："119.29.29.98"（仅支持 http 请求），"119.29.29.99"（仅支持 https 请求）以腾讯云文档（https://cloud.tencent.com/document/product/379/54976）提供的 IP 为准
    * @param debug 是否开启 debug 日志，true 为打开，false 为关闭，建议测试阶段打开，正式上线时关闭
    * @param timeout dns请求超时时间，单位ms，建议设置1000
    * @param channel 设置 channel，可选：DesHttp（默认）, AesHttp, Https
    * @param token 腾讯云官网（https://console.cloud.tencent.com/httpdns）申请获得，用于 HTTPS 校验
    */
    public void init(Context context, String appID, String dnsId, String dnsKey, String dnsIp, boolean debug,
                     int timeout, String channel, String token) {
        DnsConfig.Builder dnsConfigBuilder =
                new DnsConfig
                        .Builder()
                        .logLevel(debug ? Log.VERBOSE : Log.WARN)
                        .appId(appID)
                        .timeoutMills(timeout);
        if (null != dnsIp) {
            dnsConfigBuilder.dnsIp(dnsIp);
        }
        if (null != dnsId) {
            dnsConfigBuilder.dnsId(dnsId);
        }
        if (null != dnsKey) {
            dnsConfigBuilder.dnsKey(dnsKey);
        }

        // HTTPS的情况下必须要传如token
        if (HTTPS_CHANNEL.equals(channel) && null != token) {
            dnsConfigBuilder.token(token);
            dnsConfigBuilder.https();
        } else if (AES_HTTP_CHANNEL.equals(channel)) {
            // AES加密的渠道
            dnsConfigBuilder.aesHttp();
        } else {
            // 默认为DES加密
            dnsConfigBuilder.desHttp();
        }

        DnsConfig dnsConfig = dnsConfigBuilder.build();
        DnsService.init(context, dnsConfig);

        DnsLog.d("MSDKDnsResolver.init() called, ver:%s, channel:%s", BuildConfig.VERSION_NAME, channel);
    }

    /**
     * 设置UserId, 进行数据上报时区分用户, 出现问题时, 依赖该Id进行单用户问题排查
     *
     * @param openId 用户的唯一标识符, 腾讯业务建议直接使用OpenId, 腾讯云客户建议传入长度50位以内, 由字母数字下划线组合而成的字符串
     * @return 是否设置成功, true为设置成功, false为设置失败
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean WGSetDnsOpenId(String openId) {
        DnsLog.v("MSDKDnsResolver.WGSetDnsOpenId() called.");

        try {
            DnsService.setUserId(openId);
            return true;
        } catch (Exception e) {
            DnsLog.w(e, "WGSetDnsOpenId failed");
            return false;
        }
    }

    public void setHttpDnsResponseObserver(HttpDnsResponseObserver httpDnsResponseObserver) {
        sHttpDnsResponseObserver = httpDnsResponseObserver;
    }

    /**
     * 进行域名解析
     * 接口区分本地网络栈进行解析
     * 本地为IPv4 Only网络时, 最多返回一个IPv4结果IP
     * 本地为IPv6 Only网络时, 最多返回一个IPv6结果IP
     * 本地为Dual Stack网络时, 最多返回一个IPv4结果IP和一个IPv6结果IP
     *
     * @param domain 域名
     * @return 解析结果, 以';'分隔, ';'前为IPv4, ';'后为IPv6, 对应位置没有解析结果则为'0'
     */
    public String getAddrByName(String domain) {
        return getAddrByName(domain, true);
    }

    private String getAddrByName(String domain, boolean useHttp) {
        DnsLog.v("MSDKDnsResolver.getAddrByName() called.");

        IpSet ipSet = IpSet.EMPTY;
        // NOTE: 兼容旧版本实现, 未调用init时不crash
        try {
            ipSet = DnsService.getAddrsByName(domain, useHttp);
        } catch (Exception ignored) {
        }
        String v4Ip = "0";
        if (!CommonUtils.isEmpty(ipSet.v4Ips)) {
            v4Ip = ipSet.v4Ips[0];
        }
        String v6Ip = "0";
        if (!CommonUtils.isEmpty(ipSet.v6Ips)) {
            v6Ip = ipSet.v6Ips[0];
        }
        return v4Ip + ";" + v6Ip;
    }

    /**
     * 异步进行域名解析
     * 接口区分本地网络栈进行解析
     * 本地为IPv4 Only网络时, 最多返回一个IPv4结果IP
     * 本地为IPv6 Only网络时, 最多返回一个IPv6结果IP
     * 本地为Dual Stack网络时, 最多返回一个IPv4结果IP和一个IPv6结果IP
     * 注意需要先通过setHttpDnsResponseObserver设置回调的监听
     *
     * @param domain
     * @param tag
     */
    public void getAddrByNameAsync(final String domain, final String tag) {
        DnsExecutors.WORK.execute(new Runnable() {
            @Override
            public void run() {
                String result = getAddrByName(domain);
                if (sHttpDnsResponseObserver != null) {
                    sHttpDnsResponseObserver.onHttpDnsResponse(tag, domain, result);
                } else {
                    // try to send to unity
                    JniWrapper.sendToUnity(tag);
                }
            }
        });
    }

    /**
     * 进行域名解析(批量情况)
     * 接口区分本地网络栈进行解析
     * 单独接口查询情况返回：IpSet{v4Ips=[xx.xx.xx.xx], v6Ips=[xxx], ips=null}
     * 多域名批量查询返回：IpSet{v4Ips=[youtube.com:31.13.73.1, qq.com:123.151.137.18, qq.com:183.3.226.35, qq.com:61.129.7.47], v6Ips=[youtube.com.:2001::42d:9141], ips=null}
     *
     * @param domain 域名
     * @return 解析结果, 以';'分隔, ';'前为IPv4, ';'后为IPv6, 对应位置没有解析结果则为'0'
     */
    public IpSet getAddrsByName(String domain) {
        return getAddrsByName(domain, true);
    }

    private IpSet getAddrsByName(String domain, boolean useHttp) {
        DnsLog.v("MSDKDnsResolver.getAddrsByName() called.");

        IpSet ipSet = IpSet.EMPTY;
        // NOTE: 兼容旧版本实现, 未调用init时不crash
        try {
            ipSet = DnsService.getAddrsByName(domain, useHttp);
        } catch (Exception ignored) {
        }

        return ipSet;
    }

    /**
     * 异步进行域名解析（批量情况）
     * 接口区分本地网络栈进行解析
     * 单独接口查询情况返回：IpSet{v4Ips=[xx.xx.xx.xx], v6Ips=[xxx], ips=null}
     * 多域名批量查询返回：IpSet{v4Ips=[youtube.com:31.13.73.1, qq.com:123.151.137.18, qq.com:183.3.226.35, qq.com:61.129.7.47], v6Ips=[youtube.com.:2001::42d:9141], ips=null}
     * 注意需要先通过setHttpDnsResponseObserver设置回调的监听
     *
     * @param domain 域名, 多域名以;分割
     * @param tag
     */
    public void getAddrsByNameAsync(final String domain, final String tag) {
        DnsExecutors.WORK.execute(new Runnable() {
            @Override
            public void run() {
                IpSet result = getAddrsByName(domain);
                if (sHttpDnsResponseObserver != null) {
                    sHttpDnsResponseObserver.onHttpDnsResponse(tag, domain, result);
                } else {
                    // try to send to unity
                    JniWrapper.sendToUnity(tag);
                }
            }
        });
    }

    public String getDnsDetail(String domain) {
        DnsLog.v("MSDKDnsResolver.getDnsDetail() called.");
        try {
            return DnsService.getDnsDetail(domain);
        } catch (Exception e) {
            DnsLog.v("getDnsDetail exception:" + e);
        }
        return "";
    }

    @SuppressWarnings("unused")
    public void addLogNode(ILogNode logNode) {
        DnsLog.addLogNode(logNode);
    }
}
