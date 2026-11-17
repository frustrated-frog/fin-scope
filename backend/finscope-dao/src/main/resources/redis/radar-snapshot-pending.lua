local result = {}
for i = 1, #KEYS do
    if redis.call('EXISTS', KEYS[i]) == 1 then
        result[#result + 1] = i
    end
end
return result
