package com.finscope.dao.fundamentals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.domain.fundamentals.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class FundamentalResearchRepositoryTest {
    @TempDir
    Path temp;
    private FundamentalResearchRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + temp.resolve("research.db"));
        jdbc = new JdbcTemplate(source);
        jdbc.execute("PRAGMA foreign_keys=ON");
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY)");
        jdbc.update("INSERT INTO instrument VALUES(5)");
        var migrator = new FundamentalResearchSchemaMigrator();
        ReflectionTestUtils.setField(migrator, "jdbc", jdbc);
        migrator.afterPropertiesSet();
        migrator.afterPropertiesSet();
        repository = new FundamentalResearchRepository();
        ReflectionTestUtils.setField(repository, "jdbc", jdbc);
        ReflectionTestUtils.setField(repository, "json", new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void preservesNotesAcrossProfileRefreshAndRejectsStaleEditing() {
        var notes = new CompanyBusinessNotes();
        notes.setInstrumentId(5L);
        notes.setRevenueModel("来自年报的主营业务记录");
        notes.setUpdatedAt(LocalDateTime.now());
        repository.saveNotes(notes);
        assertEquals(1, repository.notes(5).getRevision());
        assertThrows(BusinessConflictException.class, () -> repository.saveNotes(notes));
        var profile = new CompanyBusinessProfile();
        profile.setInstrumentId(5L);
        profile.setDescription("公开公司简介");
        profile.setFetchedAt(LocalDateTime.now());
        repository.saveProfile(profile);
        assertEquals("来自年报的主营业务记录", repository.notes(5).getRevenueModel());
        assertEquals("公开公司简介", repository.profile(5).orElseThrow().getDescription());
    }

    @Test
    void duplicateWatchCannotOverwriteBaselineReviewOrUserStatus() {
        var watch = watch();
        repository.addWatch(watch);
        var stored = repository.watches(5).get(0);
        repository.review(5, stored.getId(), 0, FundamentalWatchStatus.CHALLENGED, "新一期回款低于原假设", LocalDateTime.now());
        watch.setBaseline("新值不得覆盖事前基线");
        repository.addWatch(watch);
        var updated = repository.watches(5).get(0);
        assertEquals("事前基线", updated.getBaseline());
        assertEquals(FundamentalWatchStatus.CHALLENGED, updated.getStatus());
        assertEquals(1, updated.getReviews().size());
        assertEquals(1, repository.watches(5).size());
    }

    @Test
    void reviewStatusRollsBackWhenHistoryCannotBeWritten() {
        repository.addWatch(watch());
        long id = repository.watches(5).get(0).getId();
        jdbc.execute("CREATE TRIGGER reject_review BEFORE INSERT ON fundamental_watch_review BEGIN SELECT RAISE(ABORT,'write failure'); END");
        var proxy = new ProxyFactory(repository);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(jdbc.getDataSource()),
                new AnnotationTransactionAttributeSource()));
        var transactional = (FundamentalResearchRepository) proxy.getProxy();
        assertThrows(DataAccessException.class, () -> transactional.review(5, id, 0,
                FundamentalWatchStatus.CLOSED, "保存失败", LocalDateTime.now()));
        var stored = repository.watches(5).get(0);
        assertEquals(FundamentalWatchStatus.TRACKING, stored.getStatus());
        assertEquals(0, stored.getRevision());
        assertTrue(stored.getReviews().isEmpty());
    }

    @Test
    void anotherCompanyOrStaleRevisionCannotReviewWatch() {
        repository.addWatch(watch());
        long id = repository.watches(5).get(0).getId();
        assertThrows(BusinessConflictException.class, () -> repository.review(6, id, 0, FundamentalWatchStatus.SUPPORTED, "错误公司", LocalDateTime.now()));
        repository.review(5, id, 0, FundamentalWatchStatus.SUPPORTED, "新披露支持假设", LocalDateTime.now());
        assertThrows(BusinessConflictException.class, () -> repository.review(5, id, 0, FundamentalWatchStatus.CLOSED, "旧版本", LocalDateTime.now()));
        assertEquals(1, repository.watches(5).get(0).getReviews().size());
    }

    private FundamentalWatch watch() {
        var watch = new FundamentalWatch();
        watch.setInstrumentId(5L);
        watch.setOriginKey("EVENT:123");
        watch.setTitle("核对回款");
        watch.setBaseline("事前基线");
        watch.setNextReviewDate(LocalDate.now().plusMonths(1));
        watch.setCreatedAt(LocalDateTime.now());
        return watch;
    }
}
