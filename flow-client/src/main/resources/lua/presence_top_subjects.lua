local rank_key = KEYS[1]
local active_key = KEYS[2]
local expired_before = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local result = {}
local cursor = 0

-- The rank index is maintained on presence changes. Remove entries whose
-- activity index has expired, and continue until the requested live entries
-- have been collected.
while #result < limit do
    local candidates = redis.call('ZREVRANGE', rank_key, cursor, cursor + limit - 1)
    if #candidates == 0 then
        break
    end

    local retained = 0
    for _, subject_id in ipairs(candidates) do
        local last_seen = redis.call('ZSCORE', active_key, subject_id)
        if not last_seen or tonumber(last_seen) <= expired_before then
            redis.call('ZREM', rank_key, subject_id)
        else
            table.insert(result, subject_id)
            retained = retained + 1
            if #result >= limit then
                break
            end
        end
    end

    -- Removed entries shift the sorted set left, so advance only by entries
    -- retained in the current batch.
    cursor = cursor + retained
end

return result
