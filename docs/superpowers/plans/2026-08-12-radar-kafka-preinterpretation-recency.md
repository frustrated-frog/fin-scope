# Radar Kafka Pre-interpretation and Recency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish completed radar rankings immediately, asynchronously pre-generate Top 20 interpretations through Kafka, and prevent stale news from ranking highly because of repeated collection.

**Architecture:** The refresh service publishes Redis snapshots first and then emits a compact batch event through a messaging port. A Spring Kafka adapter publishes the event and a web-layer listener delegates event IDs to the existing fingerprint-idempotent interpretation service. Recency uses source publish time with first-seen fallback and never uses collection last-seen time.

**Tech Stack:** Java 21, Spring Boot 2.7, Spring Kafka, SQLite, Redis, JUnit 5, Mockito, Docker Compose

---

### Task 1: Lock down event contracts and Kafka wiring

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarInterpretationBatchMessage.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarInterpretationBatchPublisher.java`
- Modify: `backend/finscope-rpc/pom.xml`
- Modify: `backend/finscope-web/pom.xml`
- Modify: `backend/finscope-web/src/main/resources/application.yml`
- Modify: `docker-compose.yml`

- [ ] Add a serializable batch DTO containing `runId`, `completedAt`, and at most 20 unique event IDs.
- [ ] Add the publisher port and Spring Kafka dependencies.
- [ ] Configure JSON producer/consumer, topic, group, retries, and conditional enablement.
- [ ] Add a single-node Kafka service and backend dependency to Compose.
- [ ] Run `cd backend && mvn -pl finscope-web -am -DskipTests compile` and expect `BUILD SUCCESS`.

### Task 2: Publish only after ranking snapshots are visible

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotRefreshService.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/radar/KafkaRadarInterpretationBatchPublisher.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotRefreshServiceTest.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/radar/KafkaRadarInterpretationBatchPublisherTest.java`

- [ ] Write tests asserting no message before/without successful `prewarm`, Top 20 order, and publish failure isolation.
- [ ] Run the focused tests and confirm they fail.
- [ ] Inject the publisher port and publish after successful snapshot prewarm.
- [ ] Implement the conditional Kafka publisher and disabled no-op publisher.
- [ ] Re-run focused tests and expect success.

### Task 3: Consume batches and pre-generate interpretations idempotently

**Files:**
- Create: `backend/finscope-web/src/main/java/com/finscope/web/messaging/RadarInterpretationBatchListener.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventInterpretationService.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/messaging/RadarInterpretationBatchListenerTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventInterpretationServiceTest.java`

- [ ] Write tests for unique event delegation, missing-event isolation, and revision invalidation after terminal persistence.
- [ ] Run focused tests and confirm they fail.
- [ ] Implement the Kafka listener and publish a radar revision after interpretation completion.
- [ ] Re-run focused tests and expect success.

### Task 4: Correct time semantics and strengthen decay

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotScoreService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarPriorityService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotScoreServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarPriorityServiceTest.java`

- [ ] Add failing tests proving `publishedAt` wins, `firstSeenAt` is fallback, and `lastSeenAt` cannot refresh an old item.
- [ ] Add failing assertions for strong 6/24/48-hour decay.
- [ ] Implement the new recency curve and rebalance priority components to 100 points.
- [ ] Re-run focused tests and expect success.

### Task 5: Documentation and regression verification

**Files:**
- Modify: `README.md`
- Modify: `docs/热点呈现工作流-prd方案.md`

- [ ] Document Kafka startup, topic/group, configuration, failure behavior, and scoring semantics without copying credentials.
- [ ] Run `cd backend && mvn test` and expect `BUILD SUCCESS`.
- [ ] Run `cd frontend && npm test -- --run` and expect all tests to pass.
- [ ] Run `cd frontend && npm run build` and expect a successful production build.
- [ ] Review `git diff` for unrelated changes and secrets.
- [ ] Commit each independently verified batch using English Conventional Commit types with Chinese subjects, then push the current branch.
