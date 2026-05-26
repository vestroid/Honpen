-- Webnovel.lua
SOURCE = {
    id = "webnovel-lua",
    name = "Webnovel",
    lang = "en",
    baseUrl = "https://www.webnovel.com",
    supportsText = true,
}

function SOURCE:search(query, page)
    -- Webnovel mobile search page HTML parsing
    local url = "https://m.webnovel.com/search?keywords=" .. query
    -- In a real environment, the Android user agent would be passed.
    local html = httpGet(url)
    
    local results = {}
    local seen = {}
    -- Pattern to find book slugs in the search result
    for slug in html:gmatch("/book/([a-zA-Z0-9%-]+)") do
        if not seen[slug] and slug ~= "shadow-slave" and slug ~= "supreme-magus" then -- Simple filters
            seen[slug] = true
            local title = slug:gsub("%%-", " "):gsub("^%%l", string.upper)
            table.insert(results, {
                title = title,
                url = "https://m.webnovel.com/book/" .. slug,
                author = "Webnovel Author",
                description = "Webnovel Book: " .. slug
            })
        end
    end
    return results
end

function SOURCE:getChapterList(novelUrl)
    local html = httpGet(novelUrl)
    -- Try to find bookId in the page source
    local bookId = html:match("bookId: '(%%d+)'") or html:match("data%%-bookid=\"(%%d+)\"")
    
    if not bookId then
        -- Fallback if regex fails due to HTML escaping or dynamic nature
        return {{ name = "Error: Could not find Book ID on page", url = "" }}
    end
    
    local apiUrl = "https://www.webnovel.com/go/pcm/chapter/getChapterList?bookId=" .. bookId
    local jsonStr = httpGet(apiUrl)
    local data = jsonToTable(jsonStr)
    
    local chapters = {}
    if data and data.data and data.data.volumeItems then
        for _, volume in ipairs(data.data.volumeItems) do
            for _, chapter in ipairs(volume.chapterItems) do
                table.insert(chapters, {
                    name = chapter.chapterName,
                    url = "https://www.webnovel.com/go/pcm/chapter/getContent?bookId=" .. bookId .. "&chapterId=" .. chapter.chapterId,
                    dateUpload = chapter.createTime or 0,
                    chapterNumber = chapter.chapterIndex or 0
                })
            end
        end
    end
    return chapters
end

function SOURCE:getTextContent(chapterUrl)
    local jsonStr = httpGet(chapterUrl)
    local data = jsonToTable(jsonStr)
    
    if data and data.data and data.data.chapterInfo then
        local info = data.data.chapterInfo
        local content = ""
        if info.contents then
            for _, p in ipairs(info.contents) do
                content = content .. "<p>" .. (p.content or "") .. "</p>\n"
            end
        end
        return {
            title = info.chapterName or "Untitled",
            content = content
        }
    end
    
    return { title = "Error", content = "Could not load content. Check if chapter is locked or premium." }
end

return SOURCE
