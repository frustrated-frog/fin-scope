package com.finscope.rpc.marketintel.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EastmoneyCapitalFlowProvider implements CapitalFlowProvider {
    private static final String HOST="https://push2his.eastmoney.com/api/qt/stock/";
    private static final DateTimeFormatter MINUTE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final FinanceHttpClient http; private final Clock clock; private final ObjectMapper json=new ObjectMapper();
    @Autowired public EastmoneyCapitalFlowProvider(FinanceHttpClient http){this(http,Clock.systemUTC());}
    public EastmoneyCapitalFlowProvider(FinanceHttpClient http,Clock clock){this.http=http;this.clock=clock;}
    @Override public String providerCode(){return "EASTMONEY";}
    @Override public boolean supports(Instrument i){return i!=null&&"STOCK".equals(i.getType())&&("SH".equals(i.getMarket())||"SZ".equals(i.getMarket())||"BJ".equals(i.getMarket()));}
    @Override public CapitalFlowData fetch(Instrument instrument,LocalDate asOfDate){
        if(!supports(instrument))throw new ProviderContractException("UNSUPPORTED_INSTRUMENT","Eastmoney capital flow requires an A-share stock",false);
        try{
            String secid=("SH".equals(instrument.getMarket())?"1.":"0.")+instrument.getCode();
            FinanceHttpResponse minute=getFundFlow("fflow/kline/get",secid);FinanceHttpResponse daily=getFundFlow("fflow/daykline/get",secid);
            FinanceHttpResponse quote=getQuote(secid);FinanceHttpResponse trend=getTrend(secid);FinanceHttpResponse dailyMarket=getDailyMarket(secid);
            JsonNode quoteData=data(quote);BigDecimal turnover=scaled(quoteData.get("f168"),2);BigDecimal volumeRatio=scaled(quoteData.get("f50"),2);
            Map<LocalDateTime,MarketPoint> market=parseTrends(trend);Map<LocalDateTime,MarketPoint> dailyContext=parseDailyMarket(dailyMarket);List<String>warnings=new ArrayList<String>();
            List<CapitalFlowPoint> minutes=parseFlow(minute,instrument,"MINUTE_1",false,market,warnings);
            List<CapitalFlowPoint> days=parseFlow(daily,instrument,"DAY_1",true,dailyContext,warnings);
            if(!days.isEmpty()){
                CapitalFlowPoint latest=days.get(days.size()-1);
                if(latest.getDataDate()!=null&&latest.getDataDate().equals(asOfDate)){
                    if(latest.getTurnoverRate()==null)latest.setTurnoverRate(turnover);
                    latest.setVolumeRatio(volumeRatio);
                    mergeProvenance(latest,quote);
                }
            }
            return new CapitalFlowData(minutes,days,turnover,volumeRatio,warnings,providerCode());
        }catch(ProviderContractException e){throw e;}catch(Exception e){throw new ProviderContractException("PROVIDER_ERROR","cannot parse Eastmoney capital flow",false,e);}
    }
    private FinanceHttpResponse getFundFlow(String path,String secid)throws Exception{return request(path,"secid="+secid+"&lmt=120&klt=1&fields1=f1&fields2=f51,f52,f53,f54,f55,f56");}
    private FinanceHttpResponse getQuote(String secid)throws Exception{return request("get","secid="+secid+"&fields=f43,f47,f48,f50,f168");}
    private FinanceHttpResponse getTrend(String secid)throws Exception{return request("trends2/get","secid="+secid+"&ndays=1&fields1=f1&fields2=f51,f52,f53,f54,f55,f56,f57,f58");}
    private FinanceHttpResponse getDailyMarket(String secid)throws Exception{return request("kline/get","secid="+secid+"&lmt=120&klt=101&fqt=1&fields1=f1&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61");}
    private FinanceHttpResponse request(String path,String query)throws Exception{return http.get(providerCode(),URI.create(HOST+path+"?"+query),Collections.singletonMap("Referer","https://quote.eastmoney.com"));}
    private JsonNode data(FinanceHttpResponse response)throws Exception{JsonNode value=json.readTree(response.getBody()).path("data");if(value.isMissingNode()||value.isNull())throw drift("missing data");return value;}
    private List<CapitalFlowPoint> parseFlow(FinanceHttpResponse response,Instrument instrument,String granularity,boolean daily,
                                             Map<LocalDateTime,MarketPoint> market,List<String>warnings)throws Exception{
        JsonNode lines=data(response).path("klines");if(!lines.isArray())throw drift("missing klines");List<CapitalFlowPoint> result=new ArrayList<CapitalFlowPoint>();
        for(JsonNode line:lines){String[] f=line.asText().split(",",-1);if(f.length<6)throw drift("kline field count");
            try{LocalDateTime observed=daily?LocalDate.parse(f[0]).atTime(15,0):LocalDateTime.parse(f[0],MINUTE);CapitalFlowPoint p=new CapitalFlowPoint();
                p.setInstrumentId(instrument.getId());p.setProviderCode(providerCode());p.setGranularity(granularity);p.setDataDate(observed.toLocalDate());p.setObservedAt(observed);
                p.setMainNetInflow(decimal(f[1]));p.setSmallNetInflow(decimal(f[2]));p.setMediumNetInflow(decimal(f[3]));p.setLargeNetInflow(decimal(f[4]));p.setSuperLargeNetInflow(decimal(f[5]));
                p.setCalculationVersion("eastmoney-v2");
                p.setRetrievedAt(LocalDateTime.ofInstant(response.getRetrievedAt(),ZoneId.systemDefault()));p.setPayloadHash(response.getPayloadHash());p.setQualityStatus("COMPLETE");
                MarketPoint m=market.get(observed);if(m!=null){p.setPrice(m.price);p.setTradeVolume(m.volume);p.setIntervalTradeAmount(m.amount);p.setCumulativeTradeAmount(m.cumulativeAmount);p.setTurnoverRate(m.turnoverRate);mergeProvenance(p,m);}else{p.setQualityStatus("PARTIAL");warnings.add("TIMELINE_ALIGNMENT_GAP:"+observed);}
                result.add(p);}catch(Exception e){throw drift("invalid kline value: "+line.asText());}}
        return result;
    }
    private Map<LocalDateTime,MarketPoint> parseTrends(FinanceHttpResponse response)throws Exception{JsonNode lines=data(response).path("trends");Map<LocalDateTime,MarketPoint> values=new HashMap<LocalDateTime,MarketPoint>();if(!lines.isArray())return values;
        BigDecimal previousVolume=null,previousAmount=null;LocalDate previousDate=null;
        for(JsonNode line:lines){String[]f=line.asText().split(",",-1);if(f.length<4)continue;LocalDateTime observed=LocalDateTime.parse(f[0],MINUTE);BigDecimal cumulativeVolume=decimal(f[2]),cumulativeAmount=decimal(f[3]);
            if(previousDate==null||!previousDate.equals(observed.toLocalDate())){previousVolume=null;previousAmount=null;}
            BigDecimal intervalVolume=difference(cumulativeVolume,previousVolume);BigDecimal intervalAmount=difference(cumulativeAmount,previousAmount);
            values.put(observed,new MarketPoint(decimal(f[1]),intervalVolume,intervalAmount,cumulativeAmount,null,response.getPayloadHash(),response.getRetrievedAt()));previousVolume=cumulativeVolume;previousAmount=cumulativeAmount;previousDate=observed.toLocalDate();}return values;}
    private Map<LocalDateTime,MarketPoint> parseDailyMarket(FinanceHttpResponse response)throws Exception{JsonNode lines=data(response).path("klines");Map<LocalDateTime,MarketPoint> values=new HashMap<LocalDateTime,MarketPoint>();if(!lines.isArray())throw drift("missing daily market klines");
        for(JsonNode line:lines){String[]f=line.asText().split(",",-1);if(f.length<11)throw drift("daily market kline field count");LocalDateTime observed=LocalDate.parse(f[0]).atTime(15,0);
            values.put(observed,new MarketPoint(decimal(f[2]),decimal(f[5]),decimal(f[6]),null,decimal(f[10]),response.getPayloadHash(),response.getRetrievedAt()));}return values;}
    private static void mergeProvenance(CapitalFlowPoint point,MarketPoint market){point.setPayloadHash(JdkFinanceHttpClient.sha256(point.getPayloadHash()+"|market:"+market.payloadHash));point.setRetrievedAt(max(point.getRetrievedAt(),market.retrievedAt));}
    private static void mergeProvenance(CapitalFlowPoint point,FinanceHttpResponse response){point.setPayloadHash(JdkFinanceHttpClient.sha256(point.getPayloadHash()+"|quote:"+response.getPayloadHash()));point.setRetrievedAt(max(point.getRetrievedAt(),response.getRetrievedAt()));}
    private static LocalDateTime max(LocalDateTime current,Instant candidate){LocalDateTime converted=LocalDateTime.ofInstant(candidate,ZoneId.systemDefault());return current==null||converted.isAfter(current)?converted:current;}
    private static BigDecimal difference(BigDecimal current,BigDecimal previous){if(current==null)return null;if(previous==null||current.compareTo(previous)<0)return current;return current.subtract(previous);}
    private static BigDecimal decimal(String value){return value==null||value.isEmpty()||"-".equals(value)?null:new BigDecimal(value);}
    private static BigDecimal scaled(JsonNode value,int scale){return value==null||!value.isNumber()?null:new BigDecimal(value.asText()).movePointLeft(scale);}
    private static ProviderContractException drift(String message){return new ProviderContractException("SCHEMA_DRIFT",message,false);}
    private static class MarketPoint{final BigDecimal price,volume,amount,cumulativeAmount,turnoverRate;final String payloadHash;final Instant retrievedAt;MarketPoint(BigDecimal p,BigDecimal v,BigDecimal a,BigDecimal c,BigDecimal t,String h,Instant r){price=p;volume=v;amount=a;cumulativeAmount=c;turnoverRate=t;payloadHash=h;retrievedAt=r;}}
}
