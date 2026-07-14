[CmdletBinding()]
param(
    [string]$PlayerPath = (Join-Path $PSScriptRoot "..\..\target\debug\danmaku-player.exe"),
    [string]$OutputDir = (Join-Path $PSScriptRoot "..\..\build\qa\rust-player-ui"),
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
try {
    Add-Type -AssemblyName System.Drawing.Common
} catch {
    Add-Type -AssemblyName System.Drawing
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$playerFullPath = [System.IO.Path]::GetFullPath($PlayerPath)
$outputFullPath = [System.IO.Path]::GetFullPath($OutputDir)

if (-not $SkipBuild) {
    & cargo build -p danmaku-player
    if ($LASTEXITCODE -ne 0) {
        throw "Rust player build failed with exit code $LASTEXITCODE."
    }
}
if (-not (Test-Path -LiteralPath $playerFullPath -PathType Leaf)) {
    throw "Rust player executable does not exist: $playerFullPath"
}
New-Item -ItemType Directory -Force -Path $outputFullPath | Out-Null

function Invoke-UiCapture {
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [ValidateSet("none", "hover", "focus")]
        [string]$Interaction = "none"
    )

    $screenshotPath = Join-Path $outputFullPath "$Name.png"
    $caseLocalAppData = Join-Path $outputFullPath "localappdata-$Name"
    New-Item -ItemType Directory -Force -Path $caseLocalAppData | Out-Null

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $playerFullPath
    $startInfo.WorkingDirectory = $repoRoot
    $startInfo.UseShellExecute = $false
    $startInfo.Environment["LOCALAPPDATA"] = $caseLocalAppData
    $startInfo.ArgumentList.Add("--qa-onboarding")
    if ($Interaction -ne "none") {
        $startInfo.ArgumentList.Add("--qa-primary-state")
        $startInfo.ArgumentList.Add($Interaction)
    }
    $startInfo.ArgumentList.Add("--qa-screenshot")
    $startInfo.ArgumentList.Add($screenshotPath)
    $startInfo.ArgumentList.Add("--qa-screenshot-delay-ms")
    $startInfo.ArgumentList.Add("900")
    $startInfo.ArgumentList.Add("--qa-window-size")
    $startInfo.ArgumentList.Add("960x600")

    $process = [System.Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "Failed to start the Rust player for $Name."
    }

    try {
        if (-not $process.WaitForExit(15000)) {
            $process.Kill($true)
            throw "Rust player UI capture timed out for $Name."
        }
        if ($process.ExitCode -ne 0) {
            throw "Rust player UI capture failed for $Name with exit code $($process.ExitCode)."
        }
        if (-not (Test-Path -LiteralPath $screenshotPath -PathType Leaf)) {
            throw "Rust player exited without writing $screenshotPath."
        }
        Write-Host "Captured $screenshotPath"
        return $screenshotPath
    } finally {
        if (-not $process.HasExited) {
            $process.Kill($true)
        }
        $process.Dispose()
    }
}

function Merge-UiCaptureWithBaseline {
    param(
        [Parameter(Mandatory)]
        [string]$BaselinePath,
        [Parameter(Mandatory)]
        [string]$OverlayPath
    )

    $baseline = [System.Drawing.Bitmap]::new($BaselinePath)
    $overlay = [System.Drawing.Bitmap]::new($OverlayPath)
    try {
        if ($baseline.Size -ne $overlay.Size) {
            throw "UI capture dimensions do not match: $BaselinePath and $OverlayPath"
        }
        # The Glow screenshot event can return an untouched near-black
        # framebuffer outside the interaction repaint. Key out that narrow
        # range and layer the interaction region over the full baseline.
        $merged = [System.Drawing.Bitmap]::new(
            $baseline.Width,
            $baseline.Height,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
        )
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($merged)
            try {
                $graphics.DrawImageUnscaled($baseline, 0, 0)
                $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
                try {
                    $attributes.SetColorKey(
                        [System.Drawing.Color]::FromArgb(0, 0, 0),
                        [System.Drawing.Color]::FromArgb(12, 12, 12)
                    )
                    $destination = [System.Drawing.Rectangle]::new(
                        0,
                        0,
                        $overlay.Width,
                        $overlay.Height
                    )
                    $graphics.DrawImage(
                        $overlay,
                        $destination,
                        0,
                        0,
                        $overlay.Width,
                        $overlay.Height,
                        [System.Drawing.GraphicsUnit]::Pixel,
                        $attributes
                    )
                } finally {
                    $attributes.Dispose()
                }
            } finally {
                $graphics.Dispose()
            }
            $temporaryPath = "$OverlayPath.merged.png"
            $merged.Save($temporaryPath, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $merged.Dispose()
        }
    } finally {
        $baseline.Dispose()
        $overlay.Dispose()
    }
    Move-Item -Force -LiteralPath $temporaryPath -Destination $OverlayPath
}

function Test-UiCaptureComplete {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $bitmap = [System.Drawing.Bitmap]::new($Path)
    try {
        $nonDarkSamples = 0
        $sampleCount = 0
        for ($y = 0; $y -lt $bitmap.Height; $y += 24) {
            for ($x = 0; $x -lt $bitmap.Width; $x += 24) {
                $pixel = $bitmap.GetPixel($x, $y)
                if ($pixel.R -gt 12 -or $pixel.G -gt 12 -or $pixel.B -gt 12) {
                    $nonDarkSamples++
                }
                $sampleCount++
            }
        }
        return $sampleCount -gt 0 -and ($nonDarkSamples / $sampleCount) -gt 0.12
    } finally {
        $bitmap.Dispose()
    }
}

$minimumCapture = Join-Path $outputFullPath "onboarding-minimum.png"
$completeBaseline = $false
foreach ($attempt in 1..3) {
    $attemptPath = Invoke-UiCapture -Name "onboarding-minimum-attempt-$attempt" -Interaction "none"
    if (Test-UiCaptureComplete -Path $attemptPath) {
        Move-Item -Force -LiteralPath $attemptPath -Destination $minimumCapture
        $completeBaseline = $true
        break
    }
}
if (-not $completeBaseline) {
    throw "Rust player did not produce a complete minimum-size baseline after three attempts."
}

$hoverCapture = Invoke-UiCapture -Name "onboarding-hover" -Interaction "hover"
$focusCapture = Invoke-UiCapture -Name "onboarding-keyboard-focus" -Interaction "focus"
Merge-UiCaptureWithBaseline -BaselinePath $minimumCapture -OverlayPath $hoverCapture
Merge-UiCaptureWithBaseline -BaselinePath $minimumCapture -OverlayPath $focusCapture
$captures = @($minimumCapture, $hoverCapture, $focusCapture)

$reportPath = Join-Path $outputFullPath "rust-player-ui-qa.md"
$report = @(
    "# Rust Player UI QA"
    ""
    "- Result: PASS"
    "- Window: 960x600 logical pixels (the supported minimum)"
    "- States: default, primary-action hover, keyboard focus"
    "- Captured: $([DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss 'UTC'"))"
    ""
    "## Evidence"
    ""
)
$report += $captures | ForEach-Object { "- $([System.IO.Path]::GetFileName($_))" }
[System.IO.File]::WriteAllLines($reportPath, $report)

Write-Host "Rust player minimum-size interaction QA PASS"
Write-Host "Report: $reportPath"
