# Major Event Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a durable, filterable Major Event timeline that preserves important live-news, article, and radar-event snapshots for later investment review.

**Architecture:** A SQLite table stores historical snapshots with their origin type/key. The service derives article/radar snapshots from trusted persisted sources, while live news submits the content currently visible to the user. React adds a reusable confirmation dialog and a grouped timeline workspace.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate/SQLite, JUnit 5 + Mockito, React 18, TypeScript strict mode, Vitest + Testing Library.

---

## File structure

- Create `backend/finscope-domain/src/main/java/com/finscope/domain/majorevent/MajorEvent.java` — snapshot domain model.
- Create `backend/finscope-dao/src/main/java/com/finscope/dao/majorevent/MajorEventRepository.java` — SQLite CRUD and filtering.
- Create `backend/finscope-service/src/main/java/com/finscope/service/majorevent/MajorEventService.java` — origin validation and snapshot creation.
- Create `backend/finscope-web/src/main/java/com/finscope/web/controller/MajorEventController.java` — REST boundary.
- Create `frontend/src/features/major-events/MajorEventView.tsx` and `MajorEventSaveDialog.tsx` — workspace and entry dialog.
- Modify `DatabaseInitializer.java`, article/news/radar components, app routing/types, and styles.

### Task 1: Persist major-event snapshots

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/majorevent/MajorEvent.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/majorevent/MajorEventRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/majorevent/MajorEventRepositoryTest.java`

- [ ] **Step 1: Write the failing repository test.**

```java
@Test
void returnsSnapshotsInOccurredDateOrderAndFiltersByOriginAndCategory() {
    repository.save(event("NEWS_ITEM", "CLS:1", "政策出台", "MACRO", LocalDate.of(2026, 8, 4)));
    repository.save(event("RADAR_EVENT", "7", "公司发布", "COMPANY", LocalDate.of(2026, 8, 3)));
    assertEquals(1, repository.find("NEWS_ITEM", "MACRO", null, null).size());
    assertEquals("政策出台", repository.find(null, null, null, null).get(0).getTitle());
}
```

- [ ] **Step 2: Verify the test fails.**

Run: `cd backend && mvn -pl finscope-dao test -Dtest=MajorEventRepositoryTest`

Expected: compilation failure because the model/repository do not yet exist.

- [ ] **Step 3: Add the model, table, indexes, and repository.**

```java
jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS major_event ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type TEXT NOT NULL,origin_key TEXT NOT NULL,"
        + "title TEXT NOT NULL,summary TEXT,source_name TEXT,source_url TEXT,category_code TEXT,"
        + "occurred_date TEXT NOT NULL,note TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
        + "UNIQUE(origin_type,origin_key))");
jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_major_event_timeline ON major_event(occurred_date DESC,created_at DESC)");
```

Implement `save`, `find`, `findByOrigin`, `update`, and `deleteById` in the repository. Persist dates/timestamps with the existing `TimeUtil` helpers.

- [ ] **Step 4: Verify the DAO test passes.**

Run: `cd backend && mvn -pl finscope-dao test -Dtest=MajorEventRepositoryTest`

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit and push the persistence slice.**

Run: `git add backend/finscope-domain/src/main/java/com/finscope/domain/majorevent backend/finscope-dao/src/main/java/com/finscope/dao/majorevent backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java backend/finscope-dao/src/test/java/com/finscope/dao/majorevent && git commit -m "feat: 新增大事记快照存储" && git push`

### Task 2: Add service and REST contract

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/majorevent/MajorEventService.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/CreateMajorEventRequest.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/UpdateMajorEventRequest.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/MajorEventController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/majorevent/MajorEventServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/MajorEventControllerTest.java`

- [ ] **Step 1: Write failing service tests for source derivation and duplicate rejection.**

```java
@Test
void createsArticleSnapshotFromTheStoredArticleInsteadOfClientFields() {
    Article article = new Article(); article.setId(3L); article.setTitle("央行降准");
    article.setSourceName("新华社"); article.setUrl("https://example.com/article");
    article.setCategory("宏观"); article.setPublishedAt(LocalDateTime.of(2026, 8, 4, 9, 0));
    when(articles.findById(3L)).thenReturn(Optional.of(article));
    MajorEvent saved = service.create(CreateMajorEventCommand.article(3L, null, "当时判断"));
    assertEquals("央行降准", saved.getTitle());
    assertEquals(LocalDate.of(2026, 8, 4), saved.getOccurredDate());
}
```

- [ ] **Step 2: Verify the service test fails.**

Run: `cd backend && mvn -pl finscope-service test -Dtest=MajorEventServiceTest`

Expected: compilation failure because `MajorEventService` does not exist.

- [ ] **Step 3: Implement the service and controller.**

`MajorEventService#create` accepts `NEWS_ITEM`, `ARTICLE`, and `RADAR_EVENT`; it derives stored article/radar fields, defaults dates from `publishedAt`/`firstSeenAt`, accepts client snapshot fields only for `NEWS_ITEM`, and throws `BusinessException` for unknown, blank, or duplicate origins.

```java
@PostMapping
public ApiResponse<MajorEvent> create(@RequestBody CreateMajorEventRequest request) {
    return ApiResponses.success(service.create(request.toCommand()));
}

@PatchMapping("/{id}")
public ApiResponse<MajorEvent> update(@PathVariable Long id, @RequestBody UpdateMajorEventRequest request) {
    return ApiResponses.success(service.update(id, request.getOccurredDate(), request.getNote()));
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id); return ResponseEntity.noContent().build();
}
```

- [ ] **Step 4: Verify service and controller tests pass.**

Run: `cd backend && mvn -pl finscope-service,finscope-web -am test -Dtest=MajorEventServiceTest,MajorEventControllerTest`

Expected: `BUILD SUCCESS`, standard API envelopes, and a 204 deletion response.

- [ ] **Step 5: Commit and push the REST slice.**

Run: `git add backend/finscope-service/src/main/java/com/finscope/service/majorevent backend/finscope-service/src/test/java/com/finscope/service/majorevent backend/finscope-web/src/main/java/com/finscope/web/controller/MajorEventController.java backend/finscope-web/src/main/java/com/finscope/web/request/CreateMajorEventRequest.java backend/finscope-web/src/main/java/com/finscope/web/request/UpdateMajorEventRequest.java backend/finscope-web/src/test/java/com/finscope/web/controller/MajorEventControllerTest.java && git commit -m "feat: 提供大事记管理接口" && git push`

### Task 3: Build the timeline workspace

**Files:**
- Create: `frontend/src/features/major-events/MajorEventView.tsx`
- Create: `frontend/src/features/major-events/MajorEventView.test.tsx`
- Modify: `frontend/src/shared/types/index.ts`, `frontend/src/app/AppShell.tsx`, `frontend/src/App.tsx`, `frontend/src/styles.css`

- [ ] **Step 1: Write a failing component test for grouping and filters.**

```tsx
test('groups major events by month and applies source filters', async () => {
  vi.mocked(api).mockResolvedValue([{ id: 1, originType: 'NEWS_ITEM', originKey: 'CLS:1', title: '降准', occurredDate: '2026-08-04', categoryCode: 'MACRO' }]);
  render(<MajorEventView addToast={vi.fn()} />);
  expect(await screen.findByText('2026 年 8 月')).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText('来源类型'), 'NEWS_ITEM');
  expect(api).toHaveBeenLastCalledWith('/api/major-events?originType=NEWS_ITEM');
});
```

- [ ] **Step 2: Verify the component test fails.**

Run: `cd frontend && npm test -- MajorEventView.test.tsx`

Expected: module-not-found error for `MajorEventView`.

- [ ] **Step 3: Implement the model, routing, and grouped timeline.**

```ts
export type MajorEvent = {
  id: number; originType: 'NEWS_ITEM' | 'ARTICLE' | 'RADAR_EVENT'; originKey: string;
  title: string; summary?: string; sourceName?: string; sourceUrl?: string;
  categoryCode?: string; occurredDate: string; note?: string; createdAt: string; updatedAt: string;
};
```

Add `majorEvents` to `View`, then a `Timeline / 大事记 / TL` navigation item under “知识与判断”. `MajorEventView` loads `/api/major-events`, serializes only selected filters, groups by `occurredDate.slice(0, 7)`, supports date/note edit and confirms deletion before calling the REST API.

- [ ] **Step 4: Verify UI tests and TypeScript pass.**

Run: `cd frontend && npm test -- MajorEventView.test.tsx AppShell.test.tsx && npm run build`

Expected: selected tests pass and `tsc -b && vite build` completes.

- [ ] **Step 5: Commit and push the timeline UI.**

Run: `git add frontend/src/features/major-events frontend/src/shared/types/index.ts frontend/src/app/AppShell.tsx frontend/src/App.tsx frontend/src/styles.css && git commit -m "feat: 新增大事记时间线页面" && git push`

### Task 4: Add save dialog and source-page entry points

**Files:**
- Create: `frontend/src/features/major-events/MajorEventSaveDialog.tsx`
- Modify: `frontend/src/features/articles/ArticleCard.tsx`, `frontend/src/features/articles/ArticleView.tsx`
- Modify: `frontend/src/features/news/LiveNewsPanel.tsx`, `frontend/src/features/news/RadarEventCard.tsx`, `frontend/src/features/news/NewsView.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/articles/ArticleView.test.tsx`, `frontend/src/features/news/NewsView.test.tsx`

- [ ] **Step 1: Write failing entry-point tests.**

```tsx
test('saves a visible live-news snapshot with an optional note', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} />);
  await userEvent.click(await screen.findByRole('button', { name: '记入大事记：宁德时代发布新一代电池' }));
  await userEvent.type(screen.getByLabelText('当时判断'), '关注量产节奏');
  await userEvent.click(screen.getByRole('button', { name: '保存大事' }));
  expect(api).toHaveBeenCalledWith('/api/major-events', expect.objectContaining({ method: 'POST' }));
});
```

- [ ] **Step 2: Verify the entry-point tests fail.**

Run: `cd frontend && npm test -- NewsView.test.tsx ArticleView.test.tsx`

Expected: controls named “记入大事记” are absent.

- [ ] **Step 3: Implement the shared dialog and invoke it from all source types.**

The dialog posts `{ originType, originKey, occurredDate, note, title, summary, sourceName, sourceUrl, categoryCode }`. For articles/radar only the origin, date, and note are authoritative; for live news it includes current item snapshot fields. Add sibling buttons, never buttons nested in existing article links. Use `aria-label={'记入大事记：' + title}`. On success close and toast “已记入大事记”; on failure keep the form open and show the API message.

- [ ] **Step 4: Verify source-page tests and full frontend build.**

Run: `cd frontend && npm test -- NewsView.test.tsx ArticleView.test.tsx && npm run build`

Expected: selected tests pass and the production build completes.

- [ ] **Step 5: Commit and push source entry points.**

Run: `git add frontend/src/features/major-events/MajorEventSaveDialog.tsx frontend/src/features/articles frontend/src/features/news frontend/src/styles.css && git commit -m "feat: 支持从资讯和雷达记入大事记" && git push`

### Task 5: Verify the complete feature

**Files:**
- Modify: `docs/superpowers/plans/2026-08-04-major-event-timeline.md` — mark only executed steps complete.

- [ ] **Step 1: Run the full backend suite.**

Run: `cd backend && mvn test`

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run frontend tests and production build.**

Run: `cd frontend && npm test && npm run build`

Expected: Vitest passes and Vite emits a production build.

- [ ] **Step 3: Inspect final state, commit the executed plan, and push.**

Run: `git diff main...HEAD --check && git status --short && git add docs/superpowers/plans/2026-08-04-major-event-timeline.md && git commit -m "docs: 记录大事记实现计划" && git push`
