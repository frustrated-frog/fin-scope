-- KEYS: revision, then (record, expiration index, parent) for each change.
-- ARGV: expected revision, now, then (member, payload, deadline, requires parent).
local revision = redis.call('GET', KEYS[1]) or '0'
if not string.match(revision, '^%d+$') or string.len(revision) > 15 then
    return redis.error_reply('RADAR_CACHE_BAD_REVISION')
end
if revision ~= ARGV[1] then
    return 0
end
local now = tonumber(ARGV[2])
local count = (#KEYS - 1) / 3
local incoming = {}
local deleted = {}
local keepTtl = '__RADAR_KEEP_TTL__'
for i = 1, count do
    local a = 3 + (i - 1) * 4
    if ARGV[a + 1] ~= '' and ARGV[a + 1] ~= keepTtl and tonumber(ARGV[a + 2]) > now then
        incoming[KEYS[2 + (i - 1) * 3]] = true
    elseif ARGV[a + 1] == '' or tonumber(ARGV[a + 2]) <= now then
        deleted[KEYS[2 + (i - 1) * 3]] = true
    end
end
-- Validate before writing: scripts do not roll back commands after a runtime error.
for i = 1, count do
    local k = 2 + (i - 1) * 3
    local a = 3 + (i - 1) * 4
    local recordType = redis.call('TYPE', KEYS[k]).ok
    local indexType = redis.call('TYPE', KEYS[k + 1]).ok
    if (recordType ~= 'none' and recordType ~= 'string') or (indexType ~= 'none' and indexType ~= 'zset') then
        return redis.error_reply('RADAR_CACHE_WRONGTYPE')
    end
    if incoming[KEYS[k]] and ARGV[a + 3] == '1' and not incoming[KEYS[k + 2]] then
        if deleted[KEYS[k + 2]] or redis.call('EXISTS', KEYS[k + 2]) == 0 or redis.call('PTTL', KEYS[k + 2]) <= 0 then
            return -1
        end
    end
end
local indexes = {}
for i = 1, count do
    local k = 2 + (i - 1) * 3
    local a = 3 + (i - 1) * 4
    local deadline = tonumber(ARGV[a + 2])
    if ARGV[a + 1] == '' or deadline <= now then
        redis.call('DEL', KEYS[k])
        redis.call('ZREM', KEYS[k + 1], ARGV[a])
    elseif ARGV[a + 1] ~= keepTtl or redis.call('EXISTS', KEYS[k]) == 1 then
        if ARGV[a + 1] == keepTtl then
            redis.call('PEXPIREAT', KEYS[k], deadline)
        else
            redis.call('SET', KEYS[k], ARGV[a + 1], 'PXAT', deadline)
        end
        redis.call('ZADD', KEYS[k + 1], deadline, ARGV[a])
        local ttl = redis.call('PTTL', KEYS[k + 1])
        if ttl < 0 or now + ttl < deadline then
            redis.call('PEXPIREAT', KEYS[k + 1], deadline)
        end
    end
    indexes[KEYS[k + 1]] = true
end
for index, _ in pairs(indexes) do
    redis.call('ZREMRANGEBYSCORE', index, '-inf', now)
end
redis.call('INCR', KEYS[1])
return 1
