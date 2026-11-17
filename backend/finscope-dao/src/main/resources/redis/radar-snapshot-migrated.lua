-- Unlink the old JSON only after all its points have been imported successfully.
-- Compare its value so a concurrent legacy writer cannot lose a newer revision.
local payload = redis.call('GET', KEYS[1])
if payload and redis.sha1hex(payload) == ARGV[1] then
    redis.call('UNLINK', KEYS[1])
    redis.call('ZREM', KEYS[2], ARGV[2])
    return 1
end
return 0
