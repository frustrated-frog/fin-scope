local result = {}
for i = 1, #KEYS, 2 do
    if redis.call('PTTL', KEYS[i]) > 0 then
        local rows = redis.call('ZREVRANGEBYLEX', KEYS[i + 1], '(' .. ARGV[1] .. '|', '[' .. ARGV[2] .. '|', 'LIMIT', 0, 1)
        result[#result + 1] = rows[1] and string.sub(rows[1], 31) or ''
    else
        result[#result + 1] = ''
    end
end
return result
