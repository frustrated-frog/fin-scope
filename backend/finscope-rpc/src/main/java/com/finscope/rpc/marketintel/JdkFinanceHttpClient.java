package com.finscope.rpc.marketintel;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

@Component
public class JdkFinanceHttpClient implements FinanceHttpClient {
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 FinScope/0.1";
    private final int connectTimeoutMs; private final int readTimeoutMs; private final int maxBytes;
    private final Object throttleLock = new Object();
    private final Map<String,Long> lastRequestAtNanos = new HashMap<String,Long>();
    public JdkFinanceHttpClient(){this(5000,10000,2*1024*1024);}
    public JdkFinanceHttpClient(int connectTimeoutMs,int readTimeoutMs,int maxBytes){this.connectTimeoutMs=connectTimeoutMs;this.readTimeoutMs=readTimeoutMs;this.maxBytes=maxBytes;}
    @Override public FinanceHttpResponse get(String provider,URI uri,Map<String,String> headers)throws Exception{
        throttle(provider);
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);connection.setRequestProperty("User-Agent",BROWSER_USER_AGENT);
        connection.setRequestProperty("Connection","keep-alive");
        for(Map.Entry<String,String> header:headers.entrySet())connection.setRequestProperty(header.getKey(),header.getValue());
        int status=connection.getResponseCode();InputStream input=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
        String body=read(input);if(status<200||status>=300)throw new ProviderContractException("HTTP_"+status,
                provider+" returned HTTP "+status,status==429||status==502||status==503||status==504);
        return new FinanceHttpResponse(status,body,Instant.now(),sha256(body));
    }
    private void throttle(String provider)throws InterruptedException{
        if(!"EASTMONEY".equalsIgnoreCase(provider))return;
        synchronized(throttleLock){long now=System.nanoTime();Long previous=lastRequestAtNanos.get(provider);long interval=1_000_000_000L;
            if(previous!=null){long remaining=interval-(now-previous);if(remaining>0){long millis=remaining/1_000_000L;int nanos=(int)(remaining%1_000_000L);Thread.sleep(millis,nanos);}}
            lastRequestAtNanos.put(provider,System.nanoTime());}
    }
    private String read(InputStream input)throws Exception{if(input==null)return "";try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buffer=new byte[8192];int total=0,read;while((read=in.read(buffer))!=-1){total+=read;if(total>maxBytes)throw new ProviderContractException("RESPONSE_TOO_LARGE","finance response exceeds limit",false);out.write(buffer,0,read);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    public static String sha256(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
