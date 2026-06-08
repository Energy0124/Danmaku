local mp = require "mp"
local msg = require "mp.msg"

local visible = true
local hide_timer = nil
local controls = {}
local osd = mp.create_osd_overlay("ass-events")
local logged_inputs = 0
local fullscreen_request = 0
local app_fullscreen = false

local HIDE_SECONDS = 6
local TOP_HEIGHT = 72
local BOTTOM_HEIGHT = 136
local FULLSCREEN_BOTTOM_HEIGHT = 106
local BUTTON_Y_OFFSET = 48
local FULLSCREEN_BUTTON_Y_OFFSET = 40
local BUTTON_HEIGHT = 42
local SEEK_HEIGHT = 10

local function ass_color(value)
    value = tostring(value or "FFFFFF"):gsub("#", "")
    value = string.sub(string.rep("0", 6) .. value, -6)
    return string.sub(value, 5, 6) .. string.sub(value, 3, 4) .. string.sub(value, 1, 2)
end

local function escape_ass(value)
    value = tostring(value or "")
    value = value:gsub("\\", "\\\\")
    value = value:gsub("{", "\\{")
    value = value:gsub("}", "\\}")
    value = value:gsub("[\r\n]", " ")
    return value
end

local function rect(x, y, width, height, color, alpha)
    return string.format(
        "{\\an7\\pos(%d,%d)\\bord0\\shad0\\1c&H%s&\\alpha&H%s&\\p1}m 0 0 l %d 0 l %d %d l 0 %d{\\p0}",
        x,
        y,
        ass_color(color or "000000"),
        alpha or "80",
        width,
        width,
        height,
        height
    )
end

local function text(x, y, value, size, color, align, bold)
    return string.format(
        "{\\an%d\\pos(%d,%d)\\fs%d\\bord2\\shad0\\b%d\\1c&H%s&}%s",
        align or 7,
        x,
        y,
        size,
        bold and 1 or 0,
        ass_color(color or "FFFFFF"),
        escape_ass(value)
    )
end

local function fmt_time(seconds)
    seconds = math.max(0, tonumber(seconds or 0) or 0)
    local total = math.floor(seconds)
    local hours = math.floor(total / 3600)
    local minutes = math.floor((total % 3600) / 60)
    local secs = total % 60
    if hours > 0 then
        return string.format("%d:%02d:%02d", hours, minutes, secs)
    end
    return string.format("%d:%02d", minutes, secs)
end

local function truncate(value, max_len)
    value = tostring(value or "")
    if #value <= max_len then
        return value
    end
    return string.sub(value, 1, max_len - 3) .. "..."
end

local function dimensions()
    local dims = mp.get_property_native("osd-dimensions") or {}
    local width = math.floor(tonumber(dims.w or dims.width or 0) or 0)
    local height = math.floor(tonumber(dims.h or dims.height or 0) or 0)
    if width <= 0 then
        width = 1920
    end
    if height <= 0 then
        height = 1080
    end
    return width, height
end

local function button(label, x, y, width, action)
    controls[#controls + 1] = {
        x1 = x,
        y1 = y,
        x2 = x + width,
        y2 = y + BUTTON_HEIGHT,
        action = action,
    }
    return {
        rect(x, y, width, BUTTON_HEIGHT, "FFFFFF", "D8"),
        text(x + math.floor(width / 2), y + 10, label, 22, "FFFFFF", 8, true),
    }
end

local function set_visible(next_visible)
    visible = next_visible
end

local function hide()
    set_visible(false)
    local width, height = dimensions()
    osd.res_x = width
    osd.res_y = height
    osd.data = ""
    osd:update()
end

local function schedule_hide()
    if hide_timer then
        hide_timer:kill()
    end
    hide_timer = mp.add_timeout(HIDE_SECONDS, hide)
end

local render

local function show()
    set_visible(true)
    schedule_hide()
    if render then
        render()
    end
end

local function log_input(name, event)
    if logged_inputs >= 12 then
        return
    end
    logged_inputs = logged_inputs + 1
    local phase = "press"
    if type(event) == "table" and event.event then
        phase = event.event
    end
    msg.verbose("danmaku osc input: " .. name .. " " .. phase)
end

local function mouse_from_event(event)
    if type(event) == "table" and type(event.arg) == "string" then
        local x, y, host_width, host_height = event.arg:match("^([%-%d%.]+),([%-%d%.]+),?([%-%d%.]*),?([%-%d%.]*)$")
        if x and y then
            local mouse_x = tonumber(x) or -1
            local mouse_y = tonumber(y) or -1
            local source_width = tonumber(host_width) or 0
            local source_height = tonumber(host_height) or 0
            if source_width > 0 and source_height > 0 then
                local osd_width, osd_height = dimensions()
                mouse_x = mouse_x * osd_width / source_width
                mouse_y = mouse_y * osd_height / source_height
            end
            return { x = mouse_x, y = mouse_y }
        end
    end
    local x, y = mp.get_mouse_pos()
    return { x = x or -1, y = y or -1 }
end

local function cycle_speed()
    mp.commandv("cycle-values", "speed", "0.5", "1", "1.25", "1.5", "2")
end

local function cycle_aspect()
    local current = mp.get_property("video-aspect-override", "no")
    if current == "no" then
        mp.set_property("video-aspect-override", "16:9")
    elseif current == "16:9" then
        mp.set_property("video-aspect-override", "4:3")
    else
        mp.set_property("video-aspect-override", "no")
    end
end

local function request_app_fullscreen_toggle()
    fullscreen_request = fullscreen_request + 1
    mp.set_property("user-data/danmaku-osc/fullscreen-toggle-request", tostring(fullscreen_request))
end

local function set_app_fullscreen(enabled)
    app_fullscreen = enabled
    render()
end

local function add_button_parts(parts, label, x, y, width, action)
    local drawn = button(label, x, y, width, action)
    parts[#parts + 1] = drawn[1]
    parts[#parts + 1] = drawn[2]
end

render = function()
    local width, height = dimensions()
    local fullscreen_chrome = app_fullscreen
    local top_height = fullscreen_chrome and 0 or TOP_HEIGHT
    local bottom_height = fullscreen_chrome and FULLSCREEN_BOTTOM_HEIGHT or BOTTOM_HEIGHT
    if not visible then
        osd.res_x = width
        osd.res_y = height
        osd.data = ""
        osd:update()
        return
    end

    controls = {}
    local parts = {}
    local duration = mp.get_property_number("duration", 0) or 0
    local position = mp.get_property_number("time-pos", 0) or 0
    local volume = math.floor(mp.get_property_number("volume", 100) or 100)
    local speed = mp.get_property_number("speed", 1) or 1
    local paused = mp.get_property_bool("pause", true)
    local title = mp.get_property("media-title") or mp.get_property("filename") or "Danmaku"
    local status = paused and "PAUSED" or "PLAYING"

    local bottom_y = math.max(top_height, height - bottom_height)
    local seek_x = width >= 460 and 92 or 24
    local seek_w = math.max(120, width - (seek_x * 2))
    local seek_y = bottom_y + (fullscreen_chrome and 18 or 34)
    local progress = 0
    if duration > 0 then
        progress = math.max(0, math.min(1, position / duration))
    end

    if top_height > 0 then
        parts[#parts + 1] = rect(0, 0, width, top_height, "000000", "A8")
        parts[#parts + 1] = text(24, 16, truncate(title, math.max(16, math.floor((width - 160) / 14))), 24, "FFFFFF", 7, true)
        if width >= 520 then
            parts[#parts + 1] = text(width - 24, 18, status, 20, "BFBFBF", 9, false)
        end
    end
    parts[#parts + 1] = rect(0, bottom_y, width, bottom_height, "000000", fullscreen_chrome and "A4" or "90")
    parts[#parts + 1] = text(24, seek_y - 9, fmt_time(position), 22, "FFFFFF", 7, false)
    parts[#parts + 1] = text(width - 24, seek_y - 9, duration > 0 and fmt_time(duration) or "--:--", 22, "FFFFFF", 9, false)
    parts[#parts + 1] = rect(seek_x, seek_y, seek_w, SEEK_HEIGHT, "5E5E5E", "55")
    parts[#parts + 1] = rect(seek_x, seek_y, math.max(2, math.floor(seek_w * progress)), SEEK_HEIGHT, "FF2B6D", "00")

    controls[#controls + 1] = {
        x1 = seek_x,
        y1 = seek_y - 16,
        x2 = seek_x + seek_w,
        y2 = seek_y + 24,
        action = function(mouse)
            if duration > 0 then
                local ratio = math.max(0, math.min(1, (mouse.x - seek_x) / seek_w))
                mp.commandv("seek", tostring(duration * ratio), "absolute+exact")
            end
        end,
    }

    local y = bottom_y + (fullscreen_chrome and FULLSCREEN_BUTTON_Y_OFFSET or BUTTON_Y_OFFSET)
    local x = 24
    local show_large_seek = width >= 760
    local show_volume = width >= 900
    local show_speed = width >= 520
    local show_track_buttons = width >= 1120
    local show_aspect = width >= 1240
    if show_large_seek then
        add_button_parts(parts, "<<30", x, y, 74, function() mp.commandv("seek", "-30", "relative+exact") end)
        x = x + 84
    end
    add_button_parts(parts, "-10", x, y, 62, function() mp.commandv("seek", "-10", "relative+exact") end)
    x = x + 72
    add_button_parts(parts, paused and "Play" or "Pause", x, y, 88, function() mp.commandv("cycle", "pause") end)
    x = x + 98
    add_button_parts(parts, "+10", x, y, 62, function() mp.commandv("seek", "10", "relative+exact") end)
    x = x + 72
    if show_large_seek then
        add_button_parts(parts, "30>>", x, y, 74, function() mp.commandv("seek", "30", "relative+exact") end)
        x = x + 96
    else
        x = x + 14
    end

    if show_volume then
        parts[#parts + 1] = text(x, y + 10, "Vol", 21, "BFBFBF", 7, false)
        local vol_x = x + 48
        local vol_y = y + 19
        local vol_w = show_track_buttons and 136 or 108
        parts[#parts + 1] = rect(vol_x, vol_y, vol_w, 8, "5E5E5E", "55")
        parts[#parts + 1] = rect(vol_x, vol_y, math.max(2, math.floor(vol_w * math.max(0, math.min(100, volume)) / 100)), 8, "FF2B6D", "00")
        parts[#parts + 1] = text(vol_x + vol_w + 12, y + 10, tostring(volume) .. "%", 21, "FFFFFF", 7, false)
        controls[#controls + 1] = {
            x1 = vol_x,
            y1 = y,
            x2 = vol_x + vol_w,
            y2 = y + BUTTON_HEIGHT,
            action = function(mouse)
                local percent = math.max(0, math.min(100, ((mouse.x - vol_x) / vol_w) * 100))
                mp.set_property_number("volume", percent)
            end,
        }
        x = vol_x + vol_w + 76
    end

    if show_speed then
        add_button_parts(parts, string.format("%.2gx", speed), x, y, 78, cycle_speed)
        x = x + 88
    end
    if show_track_buttons then
        add_button_parts(parts, "Audio", x, y, 86, function() mp.commandv("cycle", "audio") end)
        x = x + 96
        add_button_parts(parts, "Sub", x, y, 72, function() mp.commandv("cycle", "sub") end)
        x = x + 82
    end
    if show_aspect then
        add_button_parts(parts, "Aspect", x, y, 94, cycle_aspect)
    end
    add_button_parts(parts, app_fullscreen and "Exit" or "Full", width - 110, y, 86, request_app_fullscreen_toggle)

    osd.res_x = width
    osd.res_y = height
    osd.data = table.concat(parts, "\n")
    osd:update()
end

local function handle_click(event)
    if type(event) == "table" and event.event == "up" then
        return
    end
    log_input("left-click", event)
    local mouse = mouse_from_event(event)
    show()
    if mouse.x < 0 or mouse.y < 0 then
        return
    end
    for _, control in ipairs(controls) do
        if mouse.x >= control.x1 and mouse.x <= control.x2 and mouse.y >= control.y1 and mouse.y <= control.y2 then
            control.action(mouse)
            render()
            return
        end
    end
end

local function bind_input(key, name, handler)
    mp.add_forced_key_binding(key, name, handler, { complex = true, repeatable = true, scalable = true })
end

bind_input("MOUSE_MOVE", "danmaku-osc-mouse-move", function(event)
    if type(event) == "table" and event.event == "up" then
        return
    end
    log_input("mouse-move", event)
    show()
end)
bind_input("MOUSE_LEAVE", "danmaku-osc-mouse-leave", function(event)
    log_input("mouse-leave", event)
    hide()
end)
bind_input("MBTN_LEFT", "danmaku-osc-left-click", handle_click)
bind_input("WHEEL_UP", "danmaku-osc-wheel-up", function(event)
    if type(event) == "table" and event.event == "up" then
        return
    end
    log_input("wheel-up", event)
    show()
    mp.commandv("add", "volume", "5")
end)
bind_input("WHEEL_DOWN", "danmaku-osc-wheel-down", function(event)
    if type(event) == "table" and event.event == "up" then
        return
    end
    log_input("wheel-down", event)
    show()
    mp.commandv("add", "volume", "-5")
end)

mp.add_key_binding(nil, "danmaku-osc-app-fullscreen-on", function()
    set_app_fullscreen(true)
end)
mp.add_key_binding(nil, "danmaku-osc-app-fullscreen-off", function()
    set_app_fullscreen(false)
end)

for _, property in ipairs({ "pause", "duration", "time-pos", "volume", "speed", "media-title", "fullscreen" }) do
    mp.observe_property(property, "native", function()
        if visible then
            render()
        end
    end)
end

mp.register_event("file-loaded", show)
mp.register_event("end-file", hide)
mp.add_periodic_timer(0.25, function()
    if visible then
        render()
    end
end)

show()
