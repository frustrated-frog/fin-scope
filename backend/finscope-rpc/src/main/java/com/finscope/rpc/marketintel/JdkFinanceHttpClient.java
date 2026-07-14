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

@Component
public class JdkFinanceHttpClient implements FinanceHttpClient {
    private final int connectTimeoutMs; private final int readTimeoutMs; private final int maxBytes;
    public JdkFinanceHttpClient(){this(5000,10000,2*1024*1024);}
    public JdkFinanceHttpClient(int connectTimeoutMs,int readTimeoutMs,int maxBytes){this.connectTimeoutMs=connectTimeoutMs;this.readTimeoutMs=readTimeoutMs;this.maxBytes=maxBytes;}
    @Override public FinanceHttpResponse get(String provider,URI uri,Map<String,String> headers)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);connection.setRequestProperty("User-Agent","FinScope/0.1 market-intel");
        for(Map.Entry<String,String> header:headers.entrySet())connection.setRequestProperty(header.getKey(),header.getValue());
        int status=connection.getResponseCode();InputStream input=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
        String body=read(input);if(status<200||status>=300)throw new ProviderContractException("HTTP_"+status,
                provider+" returned HTTP "+status,status==429||status==502||status==503||status==504);
        return new FinanceHttpResponse(status,body,Instant.now(),sha256(body));
    }
    private String read(InputStream input)throws Exception{if(input==null)return "";try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buffer=new byte[8192];int total=0,read;while((read=in.read(buffer))!=-1){total+=read;if(total>maxBytes)throw new ProviderContractException("RESPONSE_TOO_LARGE","finance response exceeds limit",false);out.write(buffer,0,read);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    public static String sha256(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
