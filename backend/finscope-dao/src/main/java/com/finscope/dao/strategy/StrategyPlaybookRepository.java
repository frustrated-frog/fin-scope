package com.finscope.dao.strategy;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.strategy.StrategyPlaybook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
        value.setNote(rs.getString("note")); value.setRevision(rs.getLong("revision"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    public StrategyPlaybook upsert(String code, String status, String note) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO strategy_playbook(code,status,note,revision,created_at,updated_at) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(code) DO NOTHING", code, status, note, 0, TimeUtil.text(now), TimeUtil.text(now));
        return findByCode(code).orElseThrow(IllegalStateException::new);
    }
    public Optional<StrategyPlaybook> findByCode(String code) {
        List<StrategyPlaybook> list = jdbcTemplate.query("SELECT * FROM strategy_playbook WHERE code=?", mapper, code);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
    public List<StrategyPlaybook> findAll() { return jdbcTemplate.query("SELECT * FROM strategy_playbook ORDER BY id", mapper); }
    public boolean updateStatus(String code, String status, String note, long revision) {
        return jdbcTemplate.update("UPDATE strategy_playbook SET status=?,note=?,revision=revision+1,updated_at=? WHERE code=? AND revision=?",
                status, note, TimeUtil.text(LocalDateTime.now()), code, revision) == 1;
    }
}
