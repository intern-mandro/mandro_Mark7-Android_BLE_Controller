[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptUnderTest = Join-Path (Split-Path -Parent $PSScriptRoot) "capture-emg-status.ps1"
$testDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("mark7-emg-test-{0}" -f [Guid]::NewGuid())
[System.IO.Directory]::CreateDirectory($testDirectory) | Out-Null

function New-StatusLine {
    param(
        [Parameter(Mandatory)][string]$Epoch,
        [int]$Emg1 = 5,
        [int]$Emg2 = 6
    )
    "         $Epoch 15947 16032 D Mark7Ble: RX frame: STATUS 19B - dof=6 temp=[37, 36, 36, 35, 36, 36] turn=[0, 0, 0, 0, 0, 0] emg=[$Emg1, $Emg2] chkOk=true"
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ("$Expected" -ne "$Actual") {
        throw "$Message expected='$Expected' actual='$Actual'"
    }
}

try {
    # 1. Indented logcat line is parsed and exported.
    $inputPath = Join-Path $testDirectory "logcat.txt"
    $outputPath = Join-Path $testDirectory "emg.csv"
    [System.IO.File]::WriteAllText($inputPath, (New-StatusLine -Epoch "1789360573.037"))

    & $scriptUnderTest -InputLogPath $inputPath -OutputPath $outputPath

    $rows = @(Import-Csv -LiteralPath $outputPath)
    Assert-Equal 1 $rows.Count "STATUS rows from an indented logcat line:"
    Assert-Equal "5/6" ("{0}/{1}" -f $rows[0].emg_1, $rows[0].emg_2) "EMG values:"
    Write-Host "PASS: indented logcat STATUS line is exported to CSV."

    # 2. Samples are bucketed per second with their raw EMG, including seconds with no data.
    #    1000: 3 samples (first, partial) / 1001: 4 / 1002: none / 1003: 2 (last, partial)
    #    EMG ch1 = sample number (1..9), ch2 = sample number * 10.
    $multiInputPath = Join-Path $testDirectory "logcat-multi.txt"
    $multiOutputPath = Join-Path $testDirectory "multi.csv"
    $perSecondPath = Join-Path $testDirectory "multi-per-second.csv"
    $epochs = @("1000.100", "1000.400", "1000.900", "1001.000", "1001.250", "1001.500", "1001.750", "1003.100", "1003.600")
    $lines = for ($i = 0; $i -lt $epochs.Count; $i++) {
        New-StatusLine -Epoch $epochs[$i] -Emg1 ($i + 1) -Emg2 (($i + 1) * 10)
    }
    [System.IO.File]::WriteAllLines($multiInputPath, [string[]]@($lines))

    & $scriptUnderTest -InputLogPath $multiInputPath -OutputPath $multiOutputPath

    if (-not (Test-Path -LiteralPath $perSecondPath)) {
        throw "Expected a per-second CSV next to the sample CSV: $perSecondPath"
    }
    $perSecond = @(Import-Csv -LiteralPath $perSecondPath)
    $summary = @($perSecond | ForEach-Object { "{0}:{1}:{2}" -f $_.epoch_second, $_.count, $_.partial }) -join ","
    Assert-Equal "1000:3:True,1001:4:False,1002:0:False,1003:2:True" $summary "Per-second counts:"

    $full = $perSecond | Where-Object { $_.epoch_second -eq "1001" }
    Assert-Equal "000 250 500 750" $full.arrival_ms "Arrival ms within second 1001:"
    Assert-Equal "4 5 6 7" $full.emg_1 "Raw EMG ch1 within second 1001:"
    Assert-Equal "40 50 60 70" $full.emg_2 "Raw EMG ch2 within second 1001:"

    $empty = $perSecond | Where-Object { $_.epoch_second -eq "1002" }
    Assert-Equal "" $empty.emg_1 "Raw EMG ch1 of a second with no data:"
    Write-Host "PASS: per-second CSV shows count and raw EMG per second, keeping empty seconds."

    # 3. Sample CSV keeps millisecond precision and numbers each sample within its second.
    $samples = @(Import-Csv -LiteralPath $multiOutputPath)
    Assert-Equal "1000.100" $samples[0].epoch_seconds "Millisecond epoch:"
    Assert-Equal "300" $samples[1].delta_ms "delta_ms between 1000.100 and 1000.400:"
    Assert-Equal "1 2 3 1 2 3 4 1 2" (@($samples | ForEach-Object { $_.index_in_second }) -join " ") "index_in_second:"
    Write-Host "PASS: sample CSV keeps millisecond timestamps and numbers samples within each second."

    # 4. The whole STATUS frame is captured, and every received frame counts - even a bad checksum
    #    or a frame the app could not parse. Raw chunk lines (RX <<) are not frames.
    $frameInputPath = Join-Path $testDirectory "logcat-frames.txt"
    $frameOutputPath = Join-Path $testDirectory "frames.csv"
    $framePerSecondPath = Join-Path $testDirectory "frames-per-second.csv"
    $okRaw = "F1 06 25 24 24 23 24 24 00 00 00 00 00 00 00 00 05 06 7B 41"
    $badRaw = "F1 06 25 24 24 23 24 24 00 00 00 00 00 00 00 00 07 08 7C 00"
    $failedRaw = "F1 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
    $frameLines = @(
        "         2000.100 1 2 D Mark7Ble: RX << $okRaw (20B)",
        "         2000.100 1 2 D Mark7Ble: RX frame: STATUS 20B - dof=6 temp=[37, 36, 36, 35, 36, 36] turn=[0, 0, 0, 0, 0, 0] emg=[5, 6] voltage=123 chkOk=true raw=$okRaw",
        "         2000.200 1 2 D Mark7Ble: RX frame: STATUS 20B - dof=6 temp=[37, 36, 36, 35, 36, 36] turn=[0, 0, 0, 0, 0, 0] emg=[7, 8] voltage=124 chkOk=false raw=$badRaw",
        "         2000.300 1 2 D Mark7Ble: RX frame: STATUS 20B - parse FAILED raw=$failedRaw"
    )
    [System.IO.File]::WriteAllLines($frameInputPath, [string[]]$frameLines)

    & $scriptUnderTest -InputLogPath $frameInputPath -OutputPath $frameOutputPath

    $frames = @(Import-Csv -LiteralPath $frameOutputPath)
    Assert-Equal 3 $frames.Count "STATUS frames (RX << chunk lines excluded):"
    Assert-Equal "ok bad_checksum parse_failed" (@($frames | ForEach-Object { $_.status }) -join " ") "Frame status:"
    Assert-Equal "6" $frames[0].dof "dof:"
    Assert-Equal "37 36 36 35 36 36" $frames[0].temp "temp:"
    Assert-Equal "0 0 0 0 0 0" $frames[0].turn "turn:"
    Assert-Equal $okRaw $frames[0].raw "Raw frame bytes:"
    Assert-Equal "123" $frames[0].voltage "voltage:"
    Assert-Equal "" $frames[2].voltage "voltage of a frame that could not be parsed:"
    Assert-Equal "" $frames[2].emg_1 "EMG of a frame that could not be parsed:"
    Assert-Equal $failedRaw $frames[2].raw "Raw bytes of a frame that could not be parsed:"

    $frameSecond = @(Import-Csv -LiteralPath $framePerSecondPath)
    Assert-Equal "3/1/2" ("{0}/{1}/{2}" -f $frameSecond[0].count, $frameSecond[0].ok, $frameSecond[0].bad) "Per-second count/ok/bad:"
    Write-Host "PASS: whole STATUS frames are captured and every received frame is counted."
} finally {
    if (Test-Path -LiteralPath $testDirectory) {
        Remove-Item -LiteralPath $testDirectory -Recurse -Force
    }
}
