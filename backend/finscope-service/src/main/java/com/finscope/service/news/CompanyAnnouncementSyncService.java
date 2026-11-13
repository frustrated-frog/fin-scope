package com.finscope.service.news;

import com.finscope.dao.news.NewsReportRepository;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.research.material.EastmoneyAnnouncementClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

import static com.finscope.common.constant.ContentConstants.PROVIDER_EASTMONEY_ANNOUNCEMENT;

/** 独立于快讯的公告生产；已成功的页先落库，单个原文失败不阻断其他公告。 */
@Service
@Slf4j
public class CompanyAnnouncementSyncService {
    @Resource
    private EastmoneyAnnouncementClient client;
    @Resource
    private NewsReportRepository reports;
    @Resource
    private NewsWindowService window;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public boolean sync() {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean complete = true;
        try (var ignored = ProviderCallDeadline.open(Duration.ofSeconds(60))) {
            for (int page = 1; page <= 200; page++) {
                var batch = client.page(now.minusDays(7), now, page);
                var additions = new ArrayList<ResearchMaterial>();
                for (ResearchMaterial material : batch.getMaterials()) {
                    var existing = reports.find(PROVIDER_EASTMONEY_ANNOUNCEMENT + ":" + material.getExternalId());
                    if (existing.isPresent() && existing.get().getTitle().equals(material.getTitle())
                            && (!material.getTitle().contains("回购") || existing.get().getContent().contains("公告原文："))) {
                        continue;
                    }
                    if (material.getTitle().contains("回购")) {
                        try {
                            client.enrich(material);
                        } catch (RuntimeException error) {
                            complete = false;
                            log.warn("公告原文暂不可用，将在下一轮重试 id={}", material.getExternalId(), error);
                        }
                    }
                    additions.add(material);
                }
                window.ingestAnnouncements(additions);
                if (!batch.isHasMore()) {
                    return complete;
                }
                if (ProviderCallDeadline.remainingMillis() <= 0) {
                    log.warn("公告同步达到本轮时限，下一轮会重读未完成窗口");
                    return false;
                }
            }
            log.warn("公告同步达到分页上限，近期窗口尚未完整覆盖");
        } catch (RuntimeException error) {
            log.warn("公司公告同步暂不可用，保留已入库公告", error);
        }
        return false;
    }
}
