package com.finscope.service.research;

import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResearchService {
    private final ThemeProfileService themeProfileService;
    private final SourcePlanner sourcePlanner;
    private final SourceRepository sourceRepository;
    private final ResearchRunRepository researchRunRepository;

    public ResearchService(ThemeProfileService themeProfileService,
                           SourcePlanner sourcePlanner,
                           SourceRepository sourceRepository,
                           ResearchRunRepository researchRunRepository) {
        this.themeProfileService = themeProfileService;
        this.sourcePlanner = sourcePlanner;
        this.sourceRepository = sourceRepository;
        this.researchRunRepository = researchRunRepository;
    }

    public ResearchRunPlan createRun(LocalDate runDate,
                                     List<String> themeCodes,
                                     Integer maxSourcesPerTheme,
                                     Boolean includeDisabled) {
        LocalDate actualRunDate = runDate == null ? LocalDate.now() : runDate;
        int actualMaxSources = maxSourcesPerTheme == null ? 3 : maxSourcesPerTheme;
        boolean actualIncludeDisabled = includeDisabled != null && includeDisabled;

        List<ThemeProfile> themes = themeProfileService.getRequired(themeCodes);
        List<SourceProfile> plannedSources = sourcePlanner.plan(
                actualRunDate,
                themeCodes,
                actualMaxSources,
                actualIncludeDisabled,
                toProfiles(sourceRepository.findAll()));

        ResearchRun run = new ResearchRun();
        run.setRunDate(actualRunDate);
        run.setThemeCodes(extractCodes(themes));
        run.setSourceCount(plannedSources.size());
        run.setStatus(ResearchEnums.RUN_STATUS_PLANNED);
        run.setSummary(buildSummary(themes, plannedSources));
        run.setErrorMessage(null);

        ResearchRun saved = researchRunRepository.save(run);
        ResearchRunPlan plan = new ResearchRunPlan();
        plan.setRun(saved);
        plan.setPlannedSources(plannedSources);
        return plan;
    }

    public List<ResearchRun> listRuns() {
        return researchRunRepository.findAll();
    }

    private List<SourceProfile> toProfiles(List<Source> sources) {
        List<SourceProfile> profiles = new ArrayList<SourceProfile>();
        for (Source source : sources) {
            profiles.add(SourceProfile.from(source));
        }
        return profiles;
    }

    private List<String> extractCodes(List<ThemeProfile> themes) {
        List<String> codes = new ArrayList<String>();
        for (ThemeProfile theme : themes) {
            codes.add(theme.getCode());
        }
        return codes;
    }

    private String buildSummary(List<ThemeProfile> themes, List<SourceProfile> plannedSources) {
        List<String> themeNames = new ArrayList<String>();
        for (ThemeProfile theme : themes) {
            themeNames.add(theme.getName());
        }
        return "Planned " + plannedSources.size() + " sources for themes: " + String.join(", ", themeNames);
    }
}
