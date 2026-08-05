# Radar Redis and Docker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis-backed research material caching and a reproducible Docker Compose startup for Redis, the Java backend, the React frontend, and the optional market-data sidecar.

**Architecture:** Redis is an acceleration layer only. The existing SQLite database remains the source of truth for radar signals, events, refresh runs, and user research state. `ResearchMaterialGateway` will read/write a small JSON cache through a DAO-owned cache repository; cache failures fall back to the existing provider path. Docker will run Redis and all application services on one internal network, with a Docker Spring profile pointing SQLite at a mounted `/data` directory.

**Tech Stack:** Java 8, Spring Boot 2.7, Spring Data Redis/Lettuce, SQLite, Maven, React/Vite, Nginx, Docker Compose.

---

### Task 1: Define the cache contract and prove gateway cache behavior

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/material/ResearchMaterialCacheEntry.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/ResearchMaterialCacheRepository.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/material/ResearchMaterialGatewayTest.java`

- [ ] **Step 1: Write cache-hit and cache-write tests first**

Add a small in-memory fake repository inside `ResearchMaterialGatewayTest` and test these exact behaviors:

```java
@Test
void returnsCachedMaterialsWithoutCallingProviders() {
    ResearchMaterial cached = material("cached", "缓存资讯");
    FakeCache cache = new FakeCache(new ResearchMaterialCacheEntry(
            Collections.singletonList(cached), Collections.singletonList("cached-warning"), LocalDateTime.now()));
    ResearchMaterialProvider provider = provider("NETWORK", 10, false);
    ResearchMaterialGateway gateway = new ResearchMaterialGateway(
            Collections.singletonList(provider), new ProviderRoutePolicy(new ProviderRequestGuard()),
            new ProviderRequestGuard(), cache);

    ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
            new ResearchMaterialRequest("000001", "订单", 10));

    assertEquals("cached", result.getMaterials().get(0).getExternalId());
    assertEquals(1, cache.reads);
    assertEquals(0, cache.writes);
}

@Test
void storesSuccessfulProviderResultsAfterCacheMiss() {
    FakeCache cache = new FakeCache();
    ResearchMaterialGateway gateway = new ResearchMaterialGateway(
            Collections.singletonList(provider("NETWORK", 10, false)),
            new ProviderRoutePolicy(new ProviderRequestGuard()), new ProviderRequestGuard(), cache);

    ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
            new ResearchMaterialRequest("000001", "订单", 10));

    assertEquals(1, result.getMaterials().size());
    assertEquals(1, cache.writes);
}
```

- [ ] **Step 2: Run the focused test and verify it fails for the missing cache contract**

Run:

```bash
cd backend
mvn -pl finscope-service -am -Dtest=ResearchMaterialGatewayTest test
```

Expected: compilation failure because the new cache constructor and cache contract do not exist yet.

- [ ] **Step 3: Add the minimal domain entry and repository contract**

`ResearchMaterialCacheEntry` will contain `List<ResearchMaterial> materials`, `List<String> warnings`, and `LocalDateTime fetchedAt`, with a no-argument constructor, a full constructor, getters, and setters. The repository contract will expose:

```java
Optional<ResearchMaterialCacheEntry> get(String key);
void put(String key, ResearchMaterialCacheEntry value, Duration ttl);
```

- [ ] **Step 4: Add gateway cache lookup and write-through behavior**

Update `ResearchMaterialGateway` to accept the repository through an overloaded constructor while keeping the existing three-argument constructor backed by a no-op repository. Build a stable key from material type, stock code, query, and limit. On a valid cache hit, return the cached materials and warnings without invoking providers. On a miss, keep the existing provider routing/error isolation and cache the merged result only after the search completes.

- [ ] **Step 5: Run the focused test and the existing gateway tests**

Run:

```bash
cd backend
mvn -pl finscope-service -am -Dtest=ResearchMaterialGatewayTest test
```

Expected: all gateway tests pass, including the two cache tests.

- [ ] **Step 6: Commit the cache contract and gateway behavior**

```bash
git add backend/finscope-domain/src/main/java/com/finscope/domain/research/material/ResearchMaterialCacheEntry.java \
  backend/finscope-dao/src/main/java/com/finscope/dao/cache/ResearchMaterialCacheRepository.java \
  backend/finscope-service/src/main/java/com/finscope/service/research/material/ResearchMaterialGateway.java \
  backend/finscope-service/src/test/java/com/finscope/service/research/material/ResearchMaterialGatewayTest.java
git commit -m "feat: 增加研究资料缓存契约"
```

### Task 2: Implement Redis storage and application configuration

**Files:**
- Modify: `backend/finscope-dao/pom.xml`
- Modify: `backend/finscope-web/pom.xml`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/RedisResearchMaterialCacheRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/NoopResearchMaterialCacheRepository.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/material/ResearchMaterialGateway.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/cache/RedisResearchMaterialCacheRepositoryTest.java`

- [ ] **Step 1: Write the Redis repository serialization test**

Mock `StringRedisTemplate` and `ValueOperations<String,String>`, call `put`, verify the JSON contains the material and TTL is applied, then mock `get` and verify `get` reconstructs the cache entry. Also verify malformed Redis JSON returns `Optional.empty()`.

- [ ] **Step 2: Run the repository test and verify the expected missing-class failure**

```bash
cd backend
mvn -pl finscope-dao -Dtest=RedisResearchMaterialCacheRepositoryTest test
```

Expected: compilation failure because Redis dependencies and the repository implementation are not present.

- [ ] **Step 3: Add Spring Data Redis dependencies and the repository implementation**

Use `spring-boot-starter-data-redis` in DAO and web. `RedisResearchMaterialCacheRepository` will use `StringRedisTemplate`, Jackson JSON, a configurable TTL, and catch connection/serialization failures so Redis downtime never blocks the existing provider path. `NoopResearchMaterialCacheRepository` will satisfy contexts where Redis is disabled or unavailable at bean creation time.

- [ ] **Step 4: Add Redis settings with a short research-material TTL**

Add the default local settings:

```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    timeout: 1000ms

finscope:
  redis:
    enabled: true
    cache:
      research-material-ttl-seconds: 240
```

Keep the existing external API credentials unchanged.

- [ ] **Step 5: Run DAO and service tests**

```bash
cd backend
mvn -pl finscope-dao,finscope-service -am test
```

Expected: all tests pass without requiring a running Redis instance; unit tests use the no-op/fake repository path.

- [ ] **Step 6: Commit Redis integration**

```bash
git add backend/finscope-dao/pom.xml backend/finscope-web/pom.xml \
  backend/finscope-dao/src/main/java/com/finscope/dao/cache \
  backend/finscope-dao/src/test/java/com/finscope/dao/cache \
  backend/finscope-web/src/main/resources/application.yml \
  backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java \
  backend/finscope-service/src/main/java/com/finscope/service/research/material/ResearchMaterialGateway.java
git commit -m "feat: 接入Redis研究资料缓存"
```

### Task 3: Add Docker startup for the complete local stack

**Files:**
- Create: `docker-compose.yml`
- Create: `.dockerignore`
- Create: `backend/finscope-web/Dockerfile`
- Create: `backend/finscope-web/src/main/resources/application-docker.yml`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Modify: `README.md`

- [ ] **Step 1: Add the Docker Spring profile**

Configure the backend profile to use `jdbc:sqlite:/data/finance.db`, `finscope.data-root=/data`, Redis host `redis`, and market-data URL `http://market-data:8000`.

- [ ] **Step 2: Add backend and frontend container builds**

The backend Dockerfile will use a Maven/JDK 8 builder and a JRE 8 runtime. The frontend Dockerfile will use Node to run `npm ci` and `npm run build`, then serve the generated app from Nginx. Nginx will proxy `/api/` to the `backend` service and fall back to `index.html` for React routes.

- [ ] **Step 3: Add Compose services and persistent volumes**

Compose will provide `redis`, `market-data`, `backend`, and `frontend`; Redis and market-data will use named volumes, while backend SQLite data will bind-mount `./data:/data`. Redis will use a healthcheck and the backend will wait for Redis health before starting.

- [ ] **Step 4: Validate Docker configuration and builds**

Run:

```bash
docker compose config
docker compose build backend frontend
```

Expected: valid Compose output and successful backend/frontend images. If Docker is unavailable, run the equivalent Maven package and frontend build and report the Docker limitation explicitly.

- [ ] **Step 5: Update local startup documentation**

Document:

```bash
docker compose up --build
```

and the URLs `http://localhost:5173`, `http://localhost:8080/actuator/health`, and Redis `localhost:6379` for local diagnostics.

- [ ] **Step 6: Run final verification and commit Docker startup**

```bash
cd backend && mvn test
cd ../frontend && npm test && npm run build
cd .. && git diff --check && git status --short
git add docker-compose.yml .dockerignore backend/finscope-web/Dockerfile \
  backend/finscope-web/src/main/resources/application-docker.yml frontend/Dockerfile \
  frontend/nginx.conf README.md
git commit -m "feat: 增加Redis和Docker启动栈"
git push
```

---

## Scope review

- Covered: Redis acceleration, cache fallback, TTL, Docker Redis, Docker backend, Docker frontend, Docker market-data sidecar, SQLite persistence, and startup documentation.
- Deliberately deferred: Eastmoney provider implementation, source snapshot tables, embedding/LLM batch aggregation, multi-scene configuration, sentiment, comments, and publishing. They will consume this cache layer in later radar pipeline tasks.
- Safety boundary: Redis is never the only copy of research material or radar state; cache read/write errors degrade to the existing network/database path.
