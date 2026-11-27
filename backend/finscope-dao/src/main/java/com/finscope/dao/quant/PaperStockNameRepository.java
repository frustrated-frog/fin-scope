package com.finscope.dao.quant;

import com.finscope.domain.quant.paper.ObservedIndustry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    /** 行业仅采用估值时已经完成的发现记录。相同批次出现多个行业时保留未知。 */
    public Map<String, ObservedIndustry> industriesAt(List<String> codes, LocalDateTime cutoff) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "(?)"));
        String sql = """
                WITH pool(code) AS (VALUES %s), observations AS (
                    SELECT p.code, r.business_date, min(s.sector_name) AS industry,
                           count(DISTINCT s.sector_name) AS matches,
                           row_number() OVER (PARTITION BY p.code
                               ORDER BY r.business_date DESC, r.completed_at DESC, r.id DESC) AS sequence
                    FROM pool p JOIN stock_discovery_run r
                    JOIN stock_discovery_candidate c ON c.run_id=r.id AND c.instrument_code IN
                        (p.code, p.code || CASE WHEN p.code LIKE '6%%' THEN '.SH' ELSE '.SZ' END)
                    JOIN json_each(c.sector_codes_json) j
                    JOIN stock_discovery_sector s ON s.run_id=r.id AND s.sector_code=j.value
                    WHERE r.status='SUCCEEDED' AND r.business_date<=? AND r.completed_at<=?
                        AND s.category='INDUSTRY' AND s.retrieved_at<=? AND trim(s.sector_name)<>''
                    GROUP BY p.code, r.id
                )
                SELECT code, business_date, industry FROM observations WHERE sequence=1 AND matches=1
                """.formatted(placeholders);
        String at = cutoff.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        List<Object> args = new ArrayList<>(codes);
        args.addAll(List.of(cutoff.toLocalDate().toString(), at, at));
        return jdbcTemplate.query(sql, rs -> {
            Map<String, ObservedIndustry> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("code"), new ObservedIndustry(rs.getString("industry"),
                        LocalDate.parse(rs.getString("business_date"))));
            }
            return result;
        }, args.toArray());
    }
}
