package com.finscope.dao.fundamentals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.domain.fundamentals.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class FundamentalResearchRepository {
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper json;

    public Optional<CompanyBusinessProfile> profile(long instrumentId) {
        return jdbc.query("SELECT profile_json FROM company_business_profile WHERE instrument_id=?",
                (rs, row) -> read(rs.getString(1), CompanyBusinessProfile.class), instrumentId).stream().findFirst();
    }

    public void saveProfile(CompanyBusinessProfile profile) {
        int changed = jdbc.update("INSERT INTO company_business_profile(instrument_id,profile_json,updated_at) VALUES(?,?,?) "
                        + "ON CONFLICT(instrument_id) DO UPDATE SET profile_json=excluded.profile_json,updated_at=excluded.updated_at",
                profile.getInstrumentId(), write(profile), profile.getFetchedAt().toString());
        if (changed != 1) {
            throw new IllegalStateException("公司资料更新影响行数异常");
        }
    }

    public CompanyBusinessNotes notes(long instrumentId) {
        return jdbc.query("SELECT notes_json,revision,updated_at FROM company_business_notes WHERE instrument_id=?", (rs, row) -> {
            CompanyBusinessNotes notes = read(rs.getString(1), CompanyBusinessNotes.class);
            notes.setRevision(rs.getInt(2));
            notes.setUpdatedAt(LocalDateTime.parse(rs.getString(3)));
            return notes;
        }, instrumentId).stream().findFirst().orElseGet(() -> {
            CompanyBusinessNotes empty = new CompanyBusinessNotes();
            empty.setInstrumentId(instrumentId);
            return empty;
        });
    }

    public void saveNotes(CompanyBusinessNotes notes) {
        int changed;
        if (notes.getRevision() == 0) {
            changed = jdbc.update("INSERT INTO company_business_notes(instrument_id,notes_json,revision,updated_at) "
                            + "VALUES(?,?,1,?) ON CONFLICT(instrument_id) DO NOTHING",
                    notes.getInstrumentId(), write(notes), notes.getUpdatedAt().toString());
        } else {
            changed = jdbc.update("UPDATE company_business_notes SET notes_json=?,revision=revision+1,updated_at=? "
                            + "WHERE instrument_id=? AND revision=?", write(notes), notes.getUpdatedAt().toString(),
                    notes.getInstrumentId(), notes.getRevision());
        }
        if (changed != 1) {
            throw new BusinessConflictException("经营底稿已更新，请重新读取后保存");
        }
    }

    public List<FundamentalWatch> watches(long instrumentId) {
        List<FundamentalWatch> watches = jdbc.query("SELECT * FROM fundamental_watch WHERE instrument_id=? "
                + "ORDER BY status='CLOSED',next_review_date,id DESC LIMIT 100", (rs, row) -> {
            FundamentalWatch watch = read(rs.getString("snapshot_json"), FundamentalWatch.class);
            watch.setId(rs.getLong("id"));
            watch.setStatus(FundamentalWatchStatus.valueOf(rs.getString("status")));
            watch.setRevision(rs.getInt("revision"));
            watch.setNextReviewDate(LocalDate.parse(rs.getString("next_review_date")));
            watch.setReviewedComparisonToken(rs.getString("reviewed_comparison_token"));
            return watch;
        }, instrumentId);
        List<FundamentalWatchReview> reviews = jdbc.query("SELECT * FROM (SELECT r.*,ROW_NUMBER() OVER "
                + "(PARTITION BY r.watch_id ORDER BY r.id DESC) AS review_rank FROM fundamental_watch_review r "
                + "JOIN fundamental_watch w ON w.id=r.watch_id WHERE w.instrument_id=?) "
                + "WHERE review_rank<=10 ORDER BY id DESC", (rs, row) -> {
            FundamentalWatchReview review = new FundamentalWatchReview();
            review.setId(rs.getLong("id"));
            review.setWatchId(rs.getLong("watch_id"));
            review.setStatus(FundamentalWatchStatus.valueOf(rs.getString("status")));
            review.setNote(rs.getString("note"));
            review.setReviewedAt(LocalDateTime.parse(rs.getString("reviewed_at")));
            String nextDate = rs.getString("next_review_date");
            review.setNextReviewDate(nextDate == null ? null : LocalDate.parse(nextDate));
            String comparison = rs.getString("comparison_json");
            review.setComparison(comparison == null ? null : read(comparison, FundamentalWatchComparison.class));
            return review;
        }, instrumentId);
        for (FundamentalWatch watch : watches) {
            watch.setReviews(reviews.stream().filter(review -> watch.getId().equals(review.getWatchId())).limit(10).toList());
        }
        return watches;
    }

    public void addWatch(FundamentalWatch watch) {
        int changed = jdbc.update("INSERT INTO fundamental_watch(instrument_id,origin_key,snapshot_json,status,next_review_date,created_at) "
                        + "VALUES(?,?,?,?,?,?) ON CONFLICT(instrument_id,origin_key) DO NOTHING",
                watch.getInstrumentId(), watch.getOriginKey(), write(watch), watch.getStatus().name(),
                watch.getNextReviewDate().toString(), watch.getCreatedAt().toString());
        if (changed != 0 && changed != 1) {
            throw new IllegalStateException("验证事项写入影响行数异常");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(long instrumentId, long watchId, int revision, FundamentalWatchReview review) {
        String token = review.getComparison() == null ? null : review.getComparison().getToken();
        int changed = jdbc.update("UPDATE fundamental_watch SET status=?,revision=revision+1,next_review_date=COALESCE(?,next_review_date),"
                        + "reviewed_comparison_token=COALESCE(?,reviewed_comparison_token) WHERE id=? AND instrument_id=? AND revision=?",
                review.getStatus().name(), review.getNextReviewDate() == null ? null : review.getNextReviewDate().toString(),
                token, watchId, instrumentId, revision);
        if (changed != 1) {
            throw new BusinessConflictException("验证记录已变化，请重新读取后复核");
        }
        int inserted = jdbc.update("INSERT INTO fundamental_watch_review(watch_id,status,note,reviewed_at,comparison_json,next_review_date) VALUES(?,?,?,?,?,?)",
                watchId, review.getStatus().name(), review.getNote(), review.getReviewedAt().toString(),
                review.getComparison() == null ? null : write(review.getComparison()),
                review.getNextReviewDate() == null ? null : review.getNextReviewDate().toString());
        if (inserted != 1) {
            throw new IllegalStateException("复核记录写入影响行数异常");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("基本面底稿保存失败", error);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("基本面底稿读取失败", error);
        }
    }
}
