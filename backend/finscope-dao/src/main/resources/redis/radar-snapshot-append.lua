-- Each pair is (parent event, time-ordered snapshots). All members have score zero.
-- Fixed-width timestamps provide nanosecond precision without floating-point scores.
local cutoff = ARGV[1]
local migrate = ARGV[2] == '1'
for i = 1, #KEYS, 2 do
    local kind = redis.call('TYPE', KEYS[i + 1]).ok
    if kind ~= 'none' and kind ~= 'zset' then
        return redis.error_reply('RADAR_SNAPSHOT_WRONGTYPE')
    end
end
local written = 0
for i = 1, #KEYS, 2 do
    local ttl = redis.call('PTTL', KEYS[i])
    if ttl > 0 then
        local member = ARGV[2 + (i + 1) / 2]
        local stamp = string.sub(member, 1, 29)
        if stamp >= cutoff then
            local lower = '[' .. stamp .. '|'
            local upper = '(' .. stamp .. '}'
            if not migrate or redis.call('ZLEXCOUNT', KEYS[i + 1], lower, upper) == 0 then
                redis.call('ZREMRANGEBYLEX', KEYS[i + 1], lower, upper)
                redis.call('ZADD', KEYS[i + 1], 0, member)
            end
            redis.call('ZREMRANGEBYLEX', KEYS[i + 1], '-', '(' .. cutoff .. '|')
            redis.call('PEXPIRE', KEYS[i + 1], ttl)
            written = written + 1
        end
    end
end
return written
