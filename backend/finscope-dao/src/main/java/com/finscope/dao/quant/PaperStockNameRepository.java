package com.finscope.dao.quant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PaperStockNameRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 按实验已冻结的沪深股票池读取名称，包括已移出自选的标的。 */
    public Map<String, String> findNames(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        String values = String.join(",", Collections.nCopies(codes.size(), "(?)"));
        String sql = "WITH pool(code) AS (VALUES " + values + "), stocks AS ("
                + "SELECT code, CASE WHEN code LIKE '6%' THEN 'SH' ELSE 'SZ' END AS market FROM pool) "
                + "SELECT s.code, COALESCE("
                + "(SELECT trim(i.name) FROM instrument i WHERE i.type='STOCK' "
                + "AND i.market=s.market AND i.code=s.code AND trim(i.name) NOT IN ('',s.code,s.code||'.'||s.market) "
                + "ORDER BY i.id DESC LIMIT 1),"
                + "(SELECT trim(c.name) FROM stock_discovery_candidate c "
                + "WHERE c.instrument_code IN (s.code,s.code||'.'||s.market) "
                + "AND trim(c.name) NOT IN ('',s.code,s.code||'.'||s.market) ORDER BY c.id DESC LIMIT 1)"
                + ") AS name FROM stocks s";
        return jdbcTemplate.query(sql, rs -> {
            Map<String, String> names = new LinkedHashMap<>();
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null) {
                    names.put(rs.getString("code"), name);
                }
            }
            return names;
        }, codes.toArray());
    }
}
