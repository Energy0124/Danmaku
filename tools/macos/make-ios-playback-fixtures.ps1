$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output = Join-Path $repo 'build/ios-fixtures'
New-Item -ItemType Directory -Path $output -Force | Out-Null
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) { throw 'Install ffmpeg to generate synthetic playback fixtures.' }
@'
1
00:00:00,500 --> 00:00:10,000
Danmaku SRT fixture
'@ | Set-Content -LiteralPath (Join-Path $output 'probe.srt') -Encoding utf8NoBOM
@'
[Script Info]
ScriptType: v4.00+
PlayResX: 320
PlayResY: 180
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,18,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.50,0:00:10.00,Default,,0,0,0,,Danmaku ASS fixture
'@ | Set-Content -LiteralPath (Join-Path $output 'probe.ass') -Encoding utf8NoBOM
& ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=320x180:rate=24:duration=12' -f lavfi -i 'sine=frequency=440:duration=12' -f lavfi -i 'sine=frequency=880:duration=12' -map 0:v -map 1:a -map 2:a -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -metadata:s:a:0 language=eng -metadata:s:a:1 language=zho -movflags +faststart (Join-Path $output 'probe.mp4')
if ($LASTEXITCODE -ne 0) { throw 'MP4 fixture generation failed.' }
& ffmpeg -hide_banner -loglevel error -y -i (Join-Path $output 'probe.mp4') -i (Join-Path $output 'probe.ass') -map 0 -map 1 -c copy (Join-Path $output 'probe.mkv')
if ($LASTEXITCODE -ne 0) { throw 'MKV fixture generation failed.' }
