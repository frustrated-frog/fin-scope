package com.finscope.dao.strategy;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyPlaybookRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StrategyPlaybookRepository {
    @Resource private JdbcTemplate jdbcTemplate;
    private final RowMapper<StrategyPlaybook> mapper = (rs, n) -> {
        StrategyPlaybook value = new StrategyPlaybook();
        value.setId(rs.getLong("id")); value.setCode(rs.getString("code")); value.setStatus(rs.getString("status"));
        value.setTitle(rs.getString("title")); value.setScope(rs.getString("scope"));
        value.setSummary(rs.getString("summary")); value.setCadence(rs.getString("cadence"));
        value.setRiskBoundary(rs.getString("risk_boundary")); value.setAuthor(rs.getString("author"));
        value.setSourceTitle(rs.getString("source_title")); value.setSourceType(rs.getString("source_type"));
        value.setSourceRef(rs.getString("source_ref")); value.setSourcePublishedAt(rs.getString("source_published_at"));
        value.setValidationStatus(rs.getString("validation_status"));
        value.setNote(rs.getString("note")); value.setRevision(rs.getLong("revision"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };
    private final RowMapper<StrategyPlaybookRule> ruleMapper = (rs, n) -> {
        StrategyPlaybookRule value = new StrategyPlaybookRule();
        value.setId(rs.getLong("id")); value.setPlaybookId(rs.getLong("playbook_id"));
        value.setSectionCode(rs.getString("section_code")); value.setSectionTitle(rs.getString("section_title"));
        value.setRuleType(rs.getString("rule_type")); value.setRuleText(rs.getString("rule_text"));
        value.setTestability(rs.getString("testability"));
        value.setSourcePage((Integer) rs.getObject("source_page")); value.setParameterJson(rs.getString("parameter_json"));
        value.setSortOrder(rs.getInt("sort_order"));
        return value;
    };

    public StrategyPlaybook save(StrategyPlaybook value, List<StrategyPlaybookRule> rules) {
        if (findByCode(value.getCode()).isPresent()) {
            throw new DuplicateKeyException("策略编码已存在：" + value.getCode());
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO strategy_playbook(code,title,scope,summary,cadence,risk_boundary,author,"
                        + "source_title,source_type,source_ref,source_published_at,validation_status,status,note,revision,"
                        + "created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                value.getCode(), value.getTitle(), value.getScope(), value.getSummary(), value.getCadence(),
                value.getRiskBoundary(), value.getAuthor(), value.getSourceTitle(), value.getSourceType(),
                value.getSourceRef(), value.getSourcePublishedAt(), value.getValidationStatus(), value.getStatus(),
                value.getNote(), 0, TimeUtil.text(now), TimeUtil.text(now));
        StrategyPlaybook saved = findByCode(value.getCode()).orElseThrow(IllegalStateException::new);
        for (StrategyPlaybookRule rule : rules) {
            jdbcTemplate.update("INSERT INTO strategy_playbook_rule(playbook_id,section_code,section_title,rule_type,"
                            + "rule_text,testability,source_page,parameter_json,sort_order) VALUES(?,?,?,?,?,?,?,?,?)",
                    saved.getId(), rule.getSectionCode(), rule.getSectionTitle(), rule.getRuleType(), rule.getRuleText(),
                    rule.getTestability(), rule.getSourcePage(), rule.getParameterJson(), rule.getSortOrder());
        }
        return saved;
    }
    public Optional<StrategyPlaybook> findByCode(String code) {
        List<StrategyPlaybook> list = jdbcTemplate.query("SELECT * FROM strategy_playbook WHERE code=?", mapper, code);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
    public List<StrategyPlaybook> findAll() { return jdbcTemplate.query("SELECT * FROM strategy_playbook ORDER BY id", mapper); }
    public List<StrategyPlaybookRule> findRules(Long playbookId) {
        return jdbcTemplate.query("SELECT * FROM strategy_playbook_rule WHERE playbook_id=? ORDER BY sort_order,id",
                ruleMapper, playbookId);
    }
    public boolean updateStatus(String code, String status, String note, long revision) {
        return jdbcTemplate.update("UPDATE strategy_playbook SET status=?,note=?,revision=revision+1,updated_at=? WHERE code=? AND revision=?",
                status, note, TimeUtil.text(LocalDateTime.now()), code, revision) == 1;
    }
}
