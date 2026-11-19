package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.*;
import com.finscope.common.enums.news.NewsAiEventType;
import com.finscope.dao.fundamentals.FundamentalResearchRepository;
import com.finscope.dao.investmentobservation.ObservationNewsRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.investmentobservation.*;
import com.finscope.domain.news.ai.NewsAiContentIdentity;
import com.finscope.domain.news.ai.NewsCompanyAction;
import com.finscope.rpc.research.material.CompanyBusinessProfileClient;
import com.finscope.service.news.NewsAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class ObservationNewsAttributionService {
    private final ReactionEventRules rules = new ReactionEventRules();
    @Resource
    private ObservationNewsRepository repository;
    @Resource
    private ReactionStockResolver resolver;
    @Resource
    private ObservationPolicyVerifier policies;
    @Resource
    private FundamentalResearchRepository fundamentals;
    @Resource
    private CompanyBusinessProfileClient profileClient;
    @Resource
    private NewsAiProperties properties;

    public int refresh(LocalDateTime now) {
        repository.withdrawRevisions();
        if (properties.isEnabled()) {
            fillProfiles(now);
        }
        var companies = repository.companies(now,false);
        String catalogVersion = NewsAiContentIdentity.digest(companies.stream()
                .map(c -> c.code() + "|" + c.profile().getDescription() + "|" + c.profile().getSourceUrl())
                .collect(java.util.stream.Collectors.joining("\n")));
        var names = new HashMap<String,ReactionStockResolution>();
        int calls = 0;
        int added = 0;
        long stopAt = System.nanoTime() + java.time.Duration.ofSeconds(100).toNanos();
        for (var item : repository.inputs(now.minusDays(7), now, catalogVersion)) {
            if (System.nanoTime() >= stopAt) {
                break;
            }
            var candidates = item.analysis().getEventType() == NewsAiEventType.POLICY && item.policyRepresentative()
                    ? policies.candidates(item,companies) : List.<ObservationPolicyVerifier.Candidate>of();
            var keys = candidates.stream().map(ObservationPolicyVerifier.Candidate::key).toList();
            String signature = NewsAiContentIdentity.digest("association-v1|" + item.version() + "|" + item.eventKey() + "|"
                    + item.analysis().getCompanyActions() + "|" + keys);
            if (signature.equals(item.checkpoint())) {
                repository.confirmContext(item);
                continue;
            }
            Map<String,ReactionSample> matches = new LinkedHashMap<>();
            boolean resolved = collectDirect(item,names,matches,now);
            var verdicts = repository.verdicts(keys);
            var pending = candidates.stream().filter(c -> !verdicts.containsKey(c.key())).limit(6).toList();
            if (!pending.isEmpty() && calls < 2 && policies.verify(item,pending,now)) {
                calls++;
                verdicts.putAll(repository.verdicts(pending.stream().map(ObservationPolicyVerifier.Candidate::key).toList()));
            }
            for (var candidate : candidates) {
                String verdict = verdicts.get(candidate.key());
                if (verdict != null && !verdict.isEmpty()) {
                    var evidence = policies.evidence(verdict);
                    var company = candidate.company();
                    String note = evidence.reason() + "。公司资料：“" + evidence.business() + "” " + company.profile().getSourceUrl();
                    matches.putIfAbsent(company.code(),sample(item,company.code(),company.name(),true,note,evidence.news(),now));
                }
            }
            if (resolved && verdicts.size() == keys.size()
                    && verdicts.values().stream().noneMatch(Objects::isNull)) {
                added += repository.reconcile(item,signature,List.copyOf(matches.values()),true);
            } else if (!matches.isEmpty() || item.checkpoint() != null) {
                // 个别候选等待核对时，也能先展示有证据的股票。进度签名不等同于全量完成。
                String partial = NewsAiContentIdentity.digest(signature + "|partial|" + matches.keySet());
                added += repository.reconcile(item,partial,List.copyOf(matches.values()),false);
            }
        }
        return added;
    }

    private boolean collectDirect(ObservationNewsInput item, Map<String,ReactionStockResolution> cache,
                                  Map<String,ReactionSample> matches, LocalDateTime now) {
        boolean complete = true;
        for (var action : actions(item)) {
            if (!item.containsEvidence(action.quote()) || !action.quote().contains(action.company()) || action.overstatesAward()) {
                continue;
            }
            var result = cache.computeIfAbsent(action.company(),name -> {
                ReactionEventDecision decision = new ReactionEventDecision();
                decision.setSubjects(List.of(name));
                return resolver.resolveDirect(decision);
            });
            complete &= result.getStatus() != ReactionResolutionStatus.LOOKUP_UNAVAILABLE;
            for (var stock : result.getMatches()) {
                matches.put(stock.getCode(),sample(item,stock.getCode(),stock.getName(),false,action.role(),action.quote(),now));
            }
        }
        return complete;
    }

    /** 旧事实卡没有角色。仅复用原文能逐字定位且主体与已提取实体相符的明确动作。 */
    private List<NewsCompanyAction> actions(ObservationNewsInput item) {
        var explicit = item.analysis().getCompanyActions();
        if (!explicit.isEmpty() || item.analysis().getEventType() == NewsAiEventType.POLICY) {
            return explicit;
        }
        var result = new ArrayList<NewsCompanyAction>();
        for (String fact : item.analysis().getFacts()) {
            if (!item.containsEvidence(fact)) {
                continue;
            }
            var decision = rules.evaluate(fact);
            if (!decision.getSubtype().isSpecific()) {
                continue;
            }
            for (String name : decision.getSubjects()) {
                if (item.analysis().getEntities().contains(name)) {
                    result.add(new NewsCompanyAction(name, "公司直接参与原文所述事项", fact));
                }
            }
        }
        return result;
    }

    private ReactionSample sample(ObservationNewsInput news,String code,String name,boolean policy,String note,String quote,LocalDateTime now) {
        var sample = new ReactionSample();
        sample.setSourceIdentity("NEWS_EVENT:" + news.eventKey());
        sample.setSourceOriginType("NEWS_ITEM");
        sample.setSourceOriginKey(news.reportId());
        sample.setTitle(news.title());
        sample.setSummary(news.body());
        sample.setSourceUrl(news.url());
        sample.setPublishedAt(news.published());
        sample.setOccurredDate(news.published().toLocalDate());
        sample.setFirstCapturedAt(news.captured());
        sample.setRegisteredAt(now);
        sample.setHistoricalBackfill(news.published().toLocalDate().isBefore(now.toLocalDate()));
        sample.setInstrumentCode(code);
        sample.setInstrumentName(name);
        sample.setEventType(policy ? ReactionEventType.POLICY : ReactionEventType.COMPANY_EVENT);
        sample.setEventSubtype(policy ? ReactionEventSubtype.POLICY_BUSINESS_SCOPE : ReactionEventSubtype.DIRECT_COMPANY_ACTION);
        sample.setRuleVersion(ObservationNewsRepository.METHOD);
        sample.setRuleEvidence(quote);
        sample.setFact(quote);
        sample.setRelationNote(note + "；新闻原文：“" + quote + "”");
        sample.setResolutionStatus(ReactionResolutionStatus.RESOLVED);
        sample.setState(ReactionSampleState.OBSERVING);
        sample.setAutomatic(true);
        return sample;
    }

    private void fillProfiles(LocalDateTime now) {
        for (var company : repository.companies(now,true)) {
            var instrument = new Instrument();
            String[] identity = company.code().split("\\.");
            if (identity.length != 2) {
                continue;
            }
            instrument.setId(company.instrumentId());
            instrument.setCode(identity[0]);
            instrument.setMarket(identity[1]);
            try {
                var profile = profileClient.fetch(instrument);
                if (company.instrumentId() != null) {
                    fundamentals.saveProfile(profile);
                }
                repository.profileAttempt(company.code(),profile,now.plusDays(30));
            } catch (RuntimeException error) {
                repository.profileAttempt(company.code(),null,now.plusHours(6));
                log.warn("投资观察公司资料待重试 code={} type={}",company.code(),error.getClass().getSimpleName());
            }
        }
    }
}
