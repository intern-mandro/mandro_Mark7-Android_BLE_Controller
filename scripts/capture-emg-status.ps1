<#
.SYNOPSIS
Captures every Mark7 STATUS frame the app received (millisecond timestamp, parsed fields,
raw bytes) from Android logcat and counts how many arrive per second.

.DESCRIPTION
Source: the app's "Mark7Ble: RX frame: STATUS ..." log lines via `adb logcat -v epoch`
(millisecond resolution). Timestamps are when the phone logged each frame, not when the hand
sent it. Frames that never reached the app (lost before the phone, or bytes dropped while
re-aligning the stream) cannot be seen here.

Every frame the app assembled counts as received, whatever its state:
  ok            checksum matches
  bad_checksum  arrived, but the XOR checksum does not match
  parse_failed  arrived, but the app could not decode it (e.g. dof out of range)

While capturing, each frame is printed as it arrives, and after every wall-clock second an
"==" line shows how many frames arrived in that second (ok / bad). Seconds with no frame
show 0. Two CSV files are written:
  <name>.csv             one row per frame: timestamp, index within its second, status,
                         dof, temp, turn, EMG, voltage, raw bytes
  <name>-per-second.csv  one row per second: count, ok, bad, arrival ms, raw EMG ch1 / ch2
The first and last seconds are cut short (capture started/stopped mid-second); they are
marked and left out of the per-second statistics.

Raw bytes need an app build that logs "raw=..." in the STATUS line. Older builds still work,
but the raw column stays empty.

.EXAMPLE
.\scripts\capture-emg-status.ps1 `
    -AdbPath "C:\Android\platform-tools\adb.exe" `
    -Serial "192.168.0.37:43149" `
    -DurationSeconds 30

.EXAMPLE
.\scripts\capture-emg-status.ps1 -InputLogPath ".\saved-logcat.txt"
#>
[CmdletBinding()]
param(
    [string]$AdbPath = "adb",
    [string]$Serial = "",
    [ValidateRange(1, 86400)]
    [int]$DurationSeconds = 30,
    [string]$OutputPath = "",
    [string]$PerSecondOutputPath = "",
    [string]$InputLogPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$MaxEmptySecondLines = 10
$StartNote = "capture start - not a full second"
$EndNote = "capture end - not a full second"
$FrameLineFormat = "{0,-12}  {1,-4} {2,-13} {3,-8} {4}"
$culture = [System.Globalization.CultureInfo]::InvariantCulture

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $fileName = "emg-status-{0}.csv" -f (Get-Date -Format "yyyyMMdd-HHmmss")
    $OutputPath = Join-Path $repoRoot (Join-Path "captures" $fileName)
}
if ([string]::IsNullOrWhiteSpace($PerSecondOutputPath)) {
    $perSecondFileName = "{0}-per-second.csv" -f [System.IO.Path]::GetFileNameWithoutExtension($OutputPath)
    $PerSecondOutputPath = [System.IO.Path]::Combine((Split-Path -Parent $OutputPath), $perSecondFileName)
}

foreach ($path in @($OutputPath, $PerSecondOutputPath)) {
    $directory = Split-Path -Parent $path
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    }
}

$samples = [System.Collections.Generic.List[object]]::new()
$previousEpochSeconds = $null
$firstEpochSeconds = $null

# Live display state: the second being counted, its frame / ok counts, and what was printed.
$liveSecond = $null
$liveCount = 0
$liveOk = 0
$summaryLinesWritten = 0
$frameHeaderWritten = $false

function Format-SecondLabel {
    param([long]$EpochSecond, [string]$Format)
    [DateTimeOffset]::FromUnixTimeSeconds($EpochSecond).ToLocalTime().ToString($Format, $culture)
}

function ConvertTo-SpaceList {
    param([AllowEmptyString()][string]$Text)
    # "37, 36, 36" -> "37 36 36"
    (($Text -split ",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }) -join " "
}

function Read-StatusFrame {
    param([AllowEmptyString()][string]$Rest)

    # $Rest is the log text after "RX frame: STATUS".
    $frame = @{ status = "parse_failed"; dof = $null; temp = ""; turn = ""; emg1 = $null; emg2 = $null; voltage = $null; raw = "" }
    if ($Rest -match '\braw=(?<raw>[0-9A-Fa-f]{2}(?: [0-9A-Fa-f]{2})*)') {
        $frame.raw = $Matches.raw.ToUpperInvariant()
    }
    if ($Rest -match 'parse FAILED') {
        return $frame
    }
    if (-not ($Rest -match '\bemg=\[(?<e1>\d+),\s*(?<e2>\d+)\]')) {
        return $frame
    }
    $frame.emg1 = [int]$Matches.e1
    $frame.emg2 = [int]$Matches.e2
    if ($Rest -match '\bdof=(?<dof>\d+)') {
        $frame.dof = [int]$Matches.dof
    }
    if ($Rest -match '\btemp=\[(?<temp>[^\]]*)\]') {
        $frame.temp = ConvertTo-SpaceList -Text $Matches.temp
    }
    if ($Rest -match '\bturn=\[(?<turn>[^\]]*)\]') {
        $frame.turn = ConvertTo-SpaceList -Text $Matches.turn
    }
    if ($Rest -match '\bvoltage=(?<voltage>\d+)') {
        $frame.voltage = [int]$Matches.voltage
    }
    $frame.status = if ($Rest -match '\bchkOk=false') { "bad_checksum" } else { "ok" }
    return $frame
}

function Write-SecondSummary {
    param([long]$EpochSecond, [int]$Count, [int]$Ok, [string]$Note = "")

    $body = if ($Count -eq 0) {
        "received   0 frame(s)/s  (no data)"
    } else {
        "received {0,3} frame(s)/s  (ok {1}, bad {2})" -f $Count, $Ok, ($Count - $Ok)
    }
    $noteText = if ($Note) { "  [$Note]" } else { "" }
    Write-Host ("== {0}  {1}{2}" -f (Format-SecondLabel -EpochSecond $EpochSecond -Format "HH:mm:ss"), $body, $noteText)
    $script:summaryLinesWritten++
}

function Write-EmptySeconds {
    param([long]$FromSecond, [long]$ToSecond)

    $total = $ToSecond - $FromSecond + 1
    if ($total -le 0) {
        return
    }
    $shown = [Math]::Min($total, $MaxEmptySecondLines)
    for ($i = 0; $i -lt $shown; $i++) {
        Write-SecondSummary -EpochSecond ($FromSecond + $i) -Count 0 -Ok 0
    }
    if ($total -gt $shown) {
        Write-Host ("... {0} more second(s) with no STATUS" -f ($total - $shown))
    }
}

function Write-FrameLine {
    param([Parameter(Mandatory)]$Sample)

    if (-not $script:frameHeaderWritten) {
        Write-Host ($FrameLineFormat -f "time", "#", "status", "emg", "frame")
        $script:frameHeaderWritten = $true
    }
    $emg = if ($null -eq $Sample.emg_1) { "-" } else { "{0},{1}" -f $Sample.emg_1, $Sample.emg_2 }
    $detail = if ($Sample.raw) {
        "raw=$($Sample.raw)"
    } elseif ($Sample.status -eq "parse_failed") {
        "(no raw bytes in this log - update the app build)"
    } else {
        "dof={0} temp=[{1}] turn=[{2}] voltage={3}" -f $Sample.dof, $Sample.temp, $Sample.turn, $Sample.voltage
    }
    $time = $Sample.timestamp_iso.Substring(11, 12)
    Write-Host ($FrameLineFormat -f $time, ("#{0}" -f $Sample.index_in_second), $Sample.status, $emg, $detail)
}

function Update-LiveSecond {
    param([double]$EpochSeconds, [bool]$IsOk)

    $second = [long][Math]::Floor($EpochSeconds)
    if ($null -ne $script:liveSecond -and $second -gt $script:liveSecond) {
        # The first finished second is cut short: the capture started in the middle of it.
        $note = if ($script:summaryLinesWritten -eq 0) { $StartNote } else { "" }
        Write-SecondSummary -EpochSecond $script:liveSecond -Count $script:liveCount -Ok $script:liveOk -Note $note
        Write-EmptySeconds -FromSecond ($script:liveSecond + 1) -ToSecond ($second - 1)
        $script:liveSecond = $null
    }
    if ($null -eq $script:liveSecond) {
        $script:liveSecond = $second
        $script:liveCount = 0
        $script:liveOk = 0
    }
    $script:liveCount++
    if ($IsOk) {
        $script:liveOk++
    }
}

function Complete-LiveSeconds {
    # The last second is cut short: the capture stopped in the middle of it.
    if ($null -ne $script:liveSecond) {
        Write-SecondSummary -EpochSecond $script:liveSecond -Count $script:liveCount -Ok $script:liveOk -Note $EndNote
    }
}

function Add-EmgLogLine {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Line)

    if ($Line -notmatch '^\s*(?<epoch>\d+\.\d+)\s+.*\bMark7Ble:\s+RX frame: STATUS\b(?<rest>.*)$') {
        return
    }

    $epochSeconds = [double]::Parse($Matches.epoch, $culture)
    $frame = Read-StatusFrame -Rest $Matches.rest

    if ($null -eq $script:firstEpochSeconds) {
        $script:firstEpochSeconds = $epochSeconds
    }

    $deltaMs = if ($null -eq $script:previousEpochSeconds) {
        $null
    } else {
        ($epochSeconds - $script:previousEpochSeconds) * 1000.0
    }
    $instantaneousHz = if ($null -eq $deltaMs -or $deltaMs -le 0.0) {
        $null
    } else {
        1000.0 / $deltaMs
    }

    # Counts this frame into its second; afterwards liveCount is its number within that second.
    Update-LiveSecond -EpochSeconds $epochSeconds -IsOk ($frame.status -eq "ok")

    $epochMilliseconds = [long][Math]::Round($epochSeconds * 1000.0)
    $timestamp = [DateTimeOffset]::FromUnixTimeMilliseconds($epochMilliseconds).ToLocalTime()
    $sample = [pscustomobject]@{
        timestamp_iso = $timestamp.ToString("yyyy-MM-ddTHH:mm:ss.fffzzz")
        second = $timestamp.ToString("HH:mm:ss", $culture)
        index_in_second = $script:liveCount
        epoch_seconds = $epochSeconds.ToString("F3", $culture)
        elapsed_ms = [Math]::Round(($epochSeconds - $script:firstEpochSeconds) * 1000.0, 3)
        delta_ms = if ($null -eq $deltaMs) { $null } else { [Math]::Round($deltaMs, 3) }
        instantaneous_hz = if ($null -eq $instantaneousHz) { $null } else { [Math]::Round($instantaneousHz, 3) }
        status = $frame.status
        dof = $frame.dof
        temp = $frame.temp
        turn = $frame.turn
        emg_1 = $frame.emg1
        emg_2 = $frame.emg2
        voltage = $frame.voltage
        raw = $frame.raw
    }
    $script:samples.Add($sample)
    Write-FrameLine -Sample $sample
    $script:previousEpochSeconds = $epochSeconds
}

function Get-PerSecondRows {
    param([object[]]$Samples)

    $bySecond = @{}
    foreach ($sample in $Samples) {
        $second = [long][Math]::Floor([double]::Parse($sample.epoch_seconds, $culture))
        if (-not $bySecond.ContainsKey($second)) {
            $bySecond[$second] = [System.Collections.Generic.List[object]]::new()
        }
        $bySecond[$second].Add($sample)
    }
    if ($bySecond.Count -eq 0) {
        return
    }

    $keys = @($bySecond.Keys)
    $firstSecond = [long]($keys | Measure-Object -Minimum).Minimum
    $lastSecond = [long]($keys | Measure-Object -Maximum).Maximum
    for ($second = $firstSecond; $second -le $lastSecond; $second++) {
        # @(...) outside the if: a one-sample second must stay an array (StrictMode has no .Count on a single object).
        $inSecond = @(if ($bySecond.ContainsKey($second)) { $bySecond[$second] })
        $okCount = @($inSecond | Where-Object { $_.status -eq "ok" }).Count
        $arrivalMs = @($inSecond | ForEach-Object {
            ([long][Math]::Round([double]::Parse($_.epoch_seconds, $culture) * 1000.0) % 1000).ToString("000", $culture)
        })
        [pscustomobject]@{
            second = Format-SecondLabel -EpochSecond $second -Format "yyyy-MM-dd HH:mm:ss"
            epoch_second = $second
            count = $inSecond.Count
            ok = $okCount
            bad = $inSecond.Count - $okCount
            partial = ($second -eq $firstSecond -or $second -eq $lastSecond)
            arrival_ms = $arrivalMs -join " "
            emg_1 = @($inSecond | ForEach-Object { if ($null -eq $_.emg_1) { "-" } else { $_.emg_1 } }) -join " "
            emg_2 = @($inSecond | ForEach-Object { if ($null -eq $_.emg_2) { "-" } else { $_.emg_2 } }) -join " "
        }
    }
}

function Write-CsvPaths {
    Write-Host "CSV (frames)    : $OutputPath"
    Write-Host "CSV (per second): $PerSecondOutputPath"
}

if (-not [string]::IsNullOrWhiteSpace($InputLogPath)) {
    Get-Content -LiteralPath $InputLogPath | ForEach-Object { Add-EmgLogLine -Line $_ }
} else {
    if ($Serial -and $Serial -notmatch '^[A-Za-z0-9._:-]+$') {
        throw "Invalid adb serial: $Serial"
    }

    $adbCommand = Get-Command $AdbPath -ErrorAction SilentlyContinue
    $resolvedAdbPath = if ($null -ne $adbCommand) { $adbCommand.Source } elseif (Test-Path -LiteralPath $AdbPath) { $AdbPath } else { $null }
    if ($null -eq $resolvedAdbPath) {
        throw "adb was not found. Pass -AdbPath with the full path to adb.exe."
    }

    $arguments = [System.Collections.Generic.List[string]]::new()
    if ($Serial) {
        $arguments.Add("-s")
        $arguments.Add($Serial)
    }
    $arguments.Add("logcat")
    $arguments.Add("-v")
    $arguments.Add("epoch")
    $arguments.Add("-T")
    $arguments.Add("1")
    $arguments.Add("-s")
    $arguments.Add("Mark7Ble:D")
    $arguments.Add("*:S")

    $quotedArguments = $arguments | ForEach-Object { '"{0}"' -f $_.Replace('"', '\"') }
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $resolvedAdbPath
    $startInfo.Arguments = $quotedArguments -join " "
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Failed to start adb logcat."
    }

    Write-Host "Capturing Mark7 STATUS frames for $DurationSeconds second(s)... (every frame, plus one == line per second)"
    $deadline = [DateTime]::UtcNow.AddSeconds($DurationSeconds)
    $readTask = $process.StandardOutput.ReadLineAsync()
    try {
        while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
            if (-not $readTask.Wait(100)) {
                continue
            }

            $line = $readTask.Result
            if ($null -eq $line) {
                break
            }
            Add-EmgLogLine -Line $line
            $readTask = $process.StandardOutput.ReadLineAsync()
        }
    } finally {
        if (-not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit()
        }
        $process.Dispose()
    }
}
Complete-LiveSeconds

$samples | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
$perSecond = @(Get-PerSecondRows -Samples $samples.ToArray())
$perSecond | Export-Csv -LiteralPath $PerSecondOutputPath -NoTypeInformation -Encoding UTF8

Write-Host ""
if ($samples.Count -lt 2) {
    Write-Warning "Captured $($samples.Count) STATUS frame(s). Connect the hand and keep the app running on a screen that receives BLE notifications."
    Write-CsvPaths
    exit 0
}

$okTotal = @($samples | Where-Object { $_.status -eq "ok" }).Count
$badChecksumTotal = @($samples | Where-Object { $_.status -eq "bad_checksum" }).Count
$parseFailedTotal = @($samples | Where-Object { $_.status -eq "parse_failed" }).Count
Write-Host ("Frames        : {0} (ok {1}, bad checksum {2}, parse failed {3})" -f $samples.Count, $okTotal, $badChecksumTotal, $parseFailedTotal)
$fullSeconds = @($perSecond | Where-Object { -not $_.partial })
if ($fullSeconds.Count -gt 0) {
    $counts = $fullSeconds | ForEach-Object { [double]$_.count }
    Write-Host ("Per second    : avg {0:N1} / min {1} / max {2} frames ({3} full second(s); first/last seconds excluded - capture started/stopped mid-second)" -f `
        ($counts | Measure-Object -Average).Average,
        ($counts | Measure-Object -Minimum).Minimum,
        ($counts | Measure-Object -Maximum).Maximum,
        $fullSeconds.Count)
} else {
    Write-Host "Per second    : no full second captured - capture for at least 3 seconds."
}

$intervals = @(
    $samples |
        Select-Object -Skip 1 |
        ForEach-Object { [double]$_.delta_ms } |
        Where-Object { $_ -gt 0.0 -and $_ -le 1000.0 }
)
if ($intervals.Count -eq 0) {
    Write-Warning "Frames were captured, but no continuous interval was available for a rate calculation."
    Write-CsvPaths
    exit 0
}
$averagePeriodMs = ($intervals | Measure-Object -Average).Average
$minPeriodMs = ($intervals | Measure-Object -Minimum).Minimum
$maxPeriodMs = ($intervals | Measure-Object -Maximum).Maximum
$variance = ($intervals | ForEach-Object { [Math]::Pow($_ - $averagePeriodMs, 2) } | Measure-Object -Average).Average
$jitterMs = [Math]::Sqrt($variance)
$effectiveHz = 1000.0 / $averagePeriodMs

Write-Host ("Average rate  : {0:N2} Hz" -f $effectiveHz)
Write-Host ("Average period: {0:N2} ms" -f $averagePeriodMs)
Write-Host ("Period range  : {0:N2} .. {1:N2} ms" -f $minPeriodMs, $maxPeriodMs)
Write-Host ("Jitter (std)  : {0:N2} ms" -f $jitterMs)
Write-CsvPaths
