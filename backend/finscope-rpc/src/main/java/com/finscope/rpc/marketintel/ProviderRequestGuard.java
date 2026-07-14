package com.finscope.rpc.marketintel;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class ProviderRequestGuard {
    public interface Sleeper { void sleep(long millis) throws InterruptedException; }
    public interface Operation<T> { T run() throws Exception; }
    private static class State { Instant lastCall; Instant openUntil; int consecutiveFailures; }
    private final Clock clock; private final Sleeper sleeper; private final Duration minInterval;
    private final int maxRetries; private final int failureThreshold; private final Duration openDuration;
    private final Map<String,State> states=new HashMap<String,State>();

    public ProviderRequestGuard(){this(Clock.systemUTC(),Thread::sleep,Duration.ofSeconds(1),2,3,Duration.ofSeconds(60));}
    public ProviderRequestGuard(Clock clock,Sleeper sleeper,Duration minInterval,int maxRetries,int failureThreshold,Duration openDuration){
        this.clock=clock;this.sleeper=sleeper;this.minInterval=minInterval;this.maxRetries=maxRetries;
        this.failureThreshold=failureThreshold;this.openDuration=openDuration;
    }
    public synchronized <T> T execute(String provider,Operation<T> operation){
        State state=states.computeIfAbsent(provider,key->new State()); Instant now=clock.instant();
        if(state.openUntil!=null&&now.isBefore(state.openUntil)) throw new ProviderContractException("CIRCUIT_OPEN","provider circuit is open: "+provider,false);
        for(int attempt=0;;attempt++){
            throttle(state);
            try{T value=operation.run();state.consecutiveFailures=0;return value;}
            catch(ProviderContractException e){
                if(!e.isRetryable()){state.consecutiveFailures=0;throw e;}
                if(attempt<maxRetries)continue;
                state.consecutiveFailures++;if(state.consecutiveFailures>=failureThreshold)state.openUntil=clock.instant().plus(openDuration);throw e;
            }catch(Exception e){
                if(attempt<maxRetries)continue;
                state.consecutiveFailures++;if(state.consecutiveFailures>=failureThreshold)state.openUntil=clock.instant().plus(openDuration);
                throw new ProviderContractException("CONNECTION_ERROR",e.getMessage(),true,e);
            }
        }
    }
    private void throttle(State state){
        Instant now=clock.instant();if(state.lastCall!=null){long remaining=minInterval.toMillis()-Duration.between(state.lastCall,now).toMillis();
            if(remaining>0)try{sleeper.sleep(remaining);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProviderContractException("INTERRUPTED","provider call interrupted",false,e);}}
        state.lastCall=clock.instant();
    }
}
