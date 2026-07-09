# FinScope Intake Workflow Design

Date: 2026-07-09

## Background

The current Sources tab is a thin source configuration and manual fetch page. Fetched items go through the existing fetch pipeline and can enter the Article workspace directly. This is acceptable for a small number of curated sources, but it does not scale once FinScope supports richer source types, larger batch fetches, and scheduled daily intake.

The new direction is to turn Sources into the configuration side of an information intake system, and add a separate Intake workspace for reviewing fetched candidates before they become formal Articles.

## Product Principles

1. All fetched content enters an intake candidate pool first.
2. No candidate is promoted to Article automatically in Phase 1.
3. Agent Harness is used for review, scoring, Chinese decision summaries, and batch summaries.
4. The user is the final decision maker. Human status controls whether a candidate enters Article.
5. Promoting a candidate uses the full existing Article ingest and insight card generation chain.
6. The system must reduce decision load, not create a larger unread queue.

## Phase 1 Scope

Phase 1 implements a two-stage Agent workflow:

```text
Source manual/scheduled fetch
  -> Fetch Batch
  -> Raw Candidate persistence
  -> Deterministic precheck
     - 3-day lookback
     - max items per run
     - URL/title/body dedupe
     - extraction quality metadata
  -> CandidateReviewAgent
     - Chinese research title
     - decision summary
     - key facts
     - why it matters
     - novelty judgment
     - risk flags
     - score and recommendation
  -> BatchSummaryAgent
     - batch quality overview
     - recommended review order
     - noise/duplicate/low-value shape
  -> Intake tab human review
  -> Promote
  -> ArticleIngestCoordinator
  -> Article + Insight Card
```

Phase 1 source support:

1. RSS feeds.
2. Single web article URLs.
3. X/Twitter status URLs.
4. Web list pages that produce multiple article links.

Out of scope for Phase 1:

1. Automatic promotion.
2. Direct promotion to Brief, Evidence, Learning, or Topic.
3. Custom lookback windows. Lookback is fixed to the latest 3 days.
4. PDF, newsletter mailbox ingestion, authenticated sites, and JavaScript-heavy browser extraction.
5. Multi-agent editorial board workflow. This is reserved for Phase 2.

## Domain Model

### Source

The existing `Source` remains the configuration root. It gains intake-specific configuration:

```text
maxItemsPerRun: integer, default 10
scheduleTimes: text, comma-separated HH:mm values, e.g. "08:30,12:30,21:30"
scheduledEnabled: boolean, default false
```

Existing `fetchFrequencyMinutes` remains for backward compatibility but is not used by the Phase 1 daily fixed-time scheduler.

Phase 1 source type options:

```text
RSS
WEB
X_POST
WEB_LIST
```

`WEB` means a single article page. `WEB_LIST` means a listing/index page that should produce candidate article links.

### Fetch Batch

`fetch_batch` records one manual or scheduled intake run.

Fields:

```text
id
source_id
source_name
trigger_type              MANUAL | SCHEDULED
status                    RUNNING | COMPLETED | PARTIAL_SUCCESS | FAILED
started_at
ended_at
lookback_days             always 3 in Phase 1
max_items_requested
raw_item_count
candidate_count
agent_reviewed_count
duplicate_count
low_value_count
error_message
batch_summary_json
batch_summary_text
created_at
updated_at
```

The existing `fetch_run` remains for backward compatibility and dashboard continuity. Phase 1 keeps `fetch_run` intact and adds `fetch_batch` for Intake. Manual intake fetch creates a `fetch_batch` and also records a compatible `fetch_run` result so existing dashboard behavior remains stable.

### Intake Candidate

`intake_candidate` stores every fetched item that is eligible for human review or trace.

Fields:

```text
id
batch_id
source_id
source_name
source_type

original_title
original_url
original_summary
original_body
content_type
extraction_method
extraction_quality_score
published_at
fetched_at

chinese_title
decision_summary
key_facts_json
why_it_matters
novelty_judgment
risk_flags_json

agent_score
agent_recommendation
agent_reason
agent_model
agent_status              PENDING | SUCCESS | FAILED | FALLBACK
agent_error_message
agent_review_json

human_status              PENDING | PROMOTED | SAVED_FOR_LATER | SKIPPED | REJECTED
human_note
promoted_article_id
promoted_at

duplicate_of_candidate_id
duplicate_of_article_id
url_fingerprint
title_fingerprint
body_fingerprint

created_at
updated_at
```

Human status defaults to `PENDING` for every new non-duplicate candidate. Agent recommendation never changes human status by itself.

## Candidate Status Semantics

### Agent Recommendation

Agent recommendation is advisory:

```text
PROMOTABLE
NEED_REVIEW
LOW_VALUE
DUPLICATE
OFF_TOPIC
EXTRACTION_FAILED
```

### Human Status

Human status is authoritative:

```text
PENDING
PROMOTED
SAVED_FOR_LATER
SKIPPED
REJECTED
```

Promotion is only allowed from `PENDING` or `SAVED_FOR_LATER`. A promoted candidate stores `promotedArticleId` and becomes read-only for promotion.

## Agent Review Contract

`CandidateReviewAgent` must output structured JSON. The UI renders this structure directly.

```json
{
  "chineseTitle": "中文研究标题",
  "decisionSummary": "一句话说明这条信息值不值得入库，以及原因",
  "keyFacts": [
    "关键事实 1",
    "关键事实 2",
    "关键事实 3"
  ],
  "whyItMatters": "它对市场、行业、公司、创业或研究主题的意义",
  "noveltyJudgment": "NEW_EVENT | FOLLOW_UP | BACKGROUND | OPINION | DUPLICATE_LIKE",
  "riskFlags": [
    "发布时间不确定",
    "来源可能是转载",
    "数据缺少原始出处"
  ],
  "score": 0,
  "recommendation": "PROMOTABLE | NEED_REVIEW | LOW_VALUE | DUPLICATE | OFF_TOPIC | EXTRACTION_FAILED",
  "reason": "评分和推荐动作的理由"
}
```

Requirements:

1. The output is always Chinese, regardless of source language.
2. `decisionSummary` must be decision-oriented, not a generic abstract.
3. `keyFacts` should contain 2-4 concise facts when extraction quality allows it.
4. `score` is an integer from 0 to 100.
5. If the LLM is unavailable or returns malformed JSON, the system creates a deterministic fallback review with `agentStatus=FALLBACK`.

## Batch Summary Contract

`BatchSummaryAgent` summarizes a completed batch after candidate reviews finish.

Output fields:

```text
summaryText
topCandidateIds
qualityNotes
noiseNotes
suggestedReviewOrder
```

The batch summary helps the user decide where to spend attention before opening individual candidates.

If the LLM is unavailable, the fallback summary should use deterministic counts:

```text
本批共 N 条候选，高分 X 条，需复核 Y 条，低价值 Z 条，重复 D 条。
```

## Backend Architecture

### New Services

`IntakeService`

Responsibilities:

1. Start manual intake fetch for a source.
2. Create and update `fetch_batch`.
3. Convert adapter `RawItem` values into `intake_candidate` records.
4. Run deterministic precheck.
5. Invoke candidate review Agent.
6. Invoke batch summary Agent.
7. Update human status.
8. Promote a candidate to Article.

`CandidateReviewAgent`

Responsibilities:

1. Build the review prompt from source metadata and extracted content.
2. Call the existing OpenAI-compatible LLM client through the Agent Harness pattern.
3. Parse structured JSON.
4. Return deterministic fallback review on failure.
5. Record trace data in `agent_run`.

`BatchSummaryAgent`

Responsibilities:

1. Summarize scored candidates from one batch.
2. Return a structured batch-level review.
3. Record trace data in `agent_run`.

`WebListSourceAdapter`

Responsibilities:

1. Fetch a list page.
2. Extract likely article links using deterministic heuristics.
3. Resolve relative URLs against the list page URL.
4. Fetch each selected article through the existing web extraction path where possible.
5. Preserve risk metadata when publication time is unknown.

### Existing Services Reused

1. `SourceAdapterRegistry` remains the adapter selection point.
2. `RssSourceAdapter`, `WebSourceAdapter`, and `XPostSourceAdapter` continue to produce `RawItem`.
3. `RawItemSelector` can be reused for ranking and dedupe within one source run. Phase 1 adds lookback filtering before candidate persistence.
4. `ArticleIngestCoordinator` remains the only path for creating promoted Articles.
5. `InsightCardGenerator` continues to create the post-promotion insight card.

## Deterministic Precheck

Before Agent review, the system applies deterministic rules:

1. Drop items older than 3 days when `publishedAt` is known.
2. Keep items with unknown `publishedAt`, but add risk flag `发布时间不确定`.
3. Limit selected candidates to `source.maxItemsPerRun`.
4. Detect duplicate URL against existing candidates and existing articles.
5. Detect duplicate title/body similarity when URL differs.
6. Mark duplicate candidates with `agentRecommendation=DUPLICATE` and `humanStatus=REJECTED` only if the duplicate is certain.
7. When duplicate certainty is not high, keep `humanStatus=PENDING` and expose duplicate risk.

The system should prefer preserving a borderline candidate over silently dropping it. The Intake tab exists so the user can make the final call.

## API Design

### Sources

Existing:

```text
GET    /api/sources
POST   /api/sources
PUT    /api/sources/{id}
DELETE /api/sources/{id}
```

Updated source payload includes:

```json
{
  "name": "Macro RSS",
  "type": "RSS",
  "url": "https://example.com/rss",
  "enabled": true,
  "scheduledEnabled": true,
  "scheduleTimes": "08:30,21:30",
  "maxItemsPerRun": 10,
  "credibility": 4,
  "tags": "宏观,市场"
}
```

New manual intake endpoint:

```text
POST /api/sources/{id}/intake-fetch
```

Response:

```json
{
  "batchId": 1,
  "status": "COMPLETED",
  "candidateCount": 8,
  "agentReviewedCount": 8
}
```

The existing `POST /api/sources/{id}/fetch` should remain for compatibility until the UI fully moves to intake fetch.

### Intake

```text
GET  /api/intake/batches
GET  /api/intake/batches/{id}
GET  /api/intake/candidates?status=PENDING&batchId=1&sourceId=2
GET  /api/intake/candidates/{id}
POST /api/intake/candidates/{id}/status
POST /api/intake/candidates/{id}/promote
```

Status request:

```json
{
  "humanStatus": "SAVED_FOR_LATER",
  "humanNote": "晚上再看"
}
```

Promote response:

```json
{
  "candidateId": 12,
  "articleId": 45,
  "status": "PROMOTED"
}
```

## Frontend Design

### Sources Tab

Sources remains the source management workspace.

Capabilities:

1. Create a source.
2. Edit a source.
3. Delete a source.
4. Enable or disable scheduled fetch.
5. Configure `maxItemsPerRun`.
6. Configure fixed daily schedule times.
7. Manually run intake fetch.
8. Show recent batch status per source.

Phase 1 form fields:

```text
Name
Type: RSS | Web Article | X/Twitter | Web List
URL
Tags
Credibility
Max items per run
Scheduled enabled
Schedule times
Enabled
```

The page should stay operational and dense. It is a configuration tool, not the review workspace.

### Intake Tab

Add a new navigation item:

```text
Intake
```

Intake is the daily review workspace.

Views:

1. Left-side batch list with the selected batch summary above the candidate list.
2. Status filters: Pending, Later, Promoted, Skipped, Rejected.
3. Candidate list sorted by `agentScore` descending by default.
4. Candidate detail drawer or expanded panel.

Candidate card fields:

```text
Chinese research title
Score and Agent recommendation
Decision summary
Key facts
Why it matters
Novelty judgment
Risk flags
Source name
Published time
Original link
Human status
```

Candidate actions:

```text
入文章库
稍后看
跳过
低价值
```

After promotion:

1. Candidate status becomes `PROMOTED`.
2. `promotedArticleId` is shown.
3. User can jump to Article workspace.

## Scheduling Design

Phase 1 uses fixed daily times.

Rules:

1. A source participates only when `scheduledEnabled=true`.
2. `scheduleTimes` contains comma-separated local times in `HH:mm`.
3. Scheduler checks due sources periodically.
4. The scheduler must avoid running the same source/time slot twice in one day.
5. Manual fetch is always allowed even when scheduled fetch is disabled.
6. Lookback is fixed to 3 days.

Implementation options:

1. Add a simple Spring scheduled poller that scans sources and recent batches.
2. Record scheduled execution in `fetch_batch` to prevent duplicate runs.

Phase 1 uses the simple Spring scheduled poller rather than a general job scheduler.

## Promotion Flow

When the user clicks "入文章库":

1. Load candidate.
2. Validate human status is promotable.
3. Build a `RawItem` from candidate content.
4. Load the original `Source`.
5. Call `ArticleIngestCoordinator.ingest(source, rawItem)`.
6. Store `promoted_article_id`.
7. Set `human_status=PROMOTED`.
8. Return the created Article ID.

Promotion must be idempotent:

1. If candidate is already promoted, return the existing `promoted_article_id`.
2. If Article ingest identifies a duplicate article, still mark candidate as promoted only when a stable article ID exists.

## Error Handling

Fetch errors:

1. Mark batch `FAILED` if no candidate can be produced.
2. Mark batch `PARTIAL_SUCCESS` if some candidates were produced and some failed.
3. Store error message on batch.

Agent errors:

1. Candidate remains visible.
2. Store fallback Chinese decision card.
3. Set `agentStatus=FALLBACK` or `FAILED`.
4. Expose the risk on the candidate card.

Promotion errors:

1. Candidate remains in its previous human status.
2. UI shows an error toast.
3. No partial status update should claim promotion without an Article ID.

## Testing Strategy

Backend tests:

1. Source repository persists new fields.
2. Intake fetch creates a batch and candidates.
3. RSS candidate filtering respects the 3-day lookback.
4. `maxItemsPerRun` limits candidate count.
5. Duplicate URL does not create duplicate pending review noise.
6. Candidate review fallback works when LLM is disabled.
7. Batch summary fallback works when LLM is disabled.
8. Promote creates Article and Insight Card through the existing pipeline.
9. Promote is idempotent.
10. Scheduler does not run the same source/time slot twice in one day.

Frontend tests:

1. Sources tab creates and edits source intake settings.
2. Sources tab can trigger manual intake fetch.
3. Intake tab lists pending candidates.
4. Candidate card shows Chinese decision fields.
5. Status actions update the candidate list.
6. Promote calls the API and shows the promoted Article link.
7. Promoted candidates do not remain in the default Pending view.

Manual QA:

1. Add an RSS source with `maxItemsPerRun=3`.
2. Run manual intake fetch.
3. Confirm candidates show Chinese titles and decision summaries.
4. Promote one candidate.
5. Confirm Article tab shows the promoted article and insight card.
6. Mark one candidate as low value.
7. Confirm it disappears from Pending and appears under Rejected or low-value filter.

## Phase 2 Direction

Phase 2 can evolve from the two-stage workflow into a multi-agent editorial board:

1. Translation Agent.
2. Fact Extraction Agent.
3. Novelty/Dedupe Agent.
4. Investment Relevance Agent.
5. Startup/Learning Relevance Agent.
6. Orchestrator Agent that merges judgments.

Phase 2 should not change the Phase 1 human-only promotion principle unless explicitly redesigned.

## Acceptance Criteria

Phase 1 is complete when:

1. Sources can configure type, URL, max items per run, scheduled enabled, and schedule times.
2. Manual intake fetch creates a fetch batch and candidate records.
3. Candidates are reviewed by Agent or deterministic fallback and shown in Chinese.
4. Intake tab supports filtering and human status changes.
5. No candidate enters Article automatically.
6. Manual promotion creates a normal Article and insight card.
7. Fetch, review, and promotion failures are visible and recoverable.
8. Automated tests cover the core backend and frontend paths.

## Self-Review

1. No unfinished markers remain.
2. Phase 1 and Phase 2 are separated.
3. Automatic promotion is explicitly out of scope.
4. The 3-day lookback is fixed and not exposed in UI.
5. Human status is authoritative; Agent recommendation is advisory.
6. Promotion uses the existing Article ingest chain.
