[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_.-]+/JOKESONG$')]
    [string]$Repo = 'liuchengzhiaa/JOKESONG',
    [switch]$PlanOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gitRoot = $root.Replace('\', '/')
function Invoke-Git {
    $output = & git -c "safe.directory=$gitRoot" @args
    if ($LASTEXITCODE -ne 0) { throw "Git command failed: $($args -join ' ')" }
    $output
}
function Invoke-Gh {
    $output = & $script:gh @args
    if ($LASTEXITCODE -ne 0) { throw "GitHub command failed: $($args -join ' ')" }
    $output
}
$originalLocation = Get-Location
try {
    Set-Location -LiteralPath $root
    $config = Get-Content -LiteralPath 'app/build.gradle.kts' -Raw
    if ($config -notmatch 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"') { throw 'Expected a semantic versionName' }
    $version = $Matches[1]
    if ($config -notmatch 'versionCode\s*=\s*([0-9]+)') { throw 'Missing versionCode' }
    $versionCode = [int]$Matches[1]
    $tag = "v$version"
    $build = Join-Path (Split-Path -Parent $root) '.asmrplayer-build/app'
    $apk = Join-Path $build 'outputs/apk/debug/app-debug.apk'
    if ($PlanOnly) {
        [pscustomobject]@{ Repo = $Repo; Version = $version; VersionCode = $versionCode; Tag = $tag; Apk = $apk } | ConvertTo-Json
        return
    }
    $ghCommand = Get-Command gh -CommandType Application -ErrorAction SilentlyContinue
    $script:gh = if ($ghCommand) { $ghCommand.Source } else { Join-Path $root '.tools/github-cli/v2.102.0/bin/gh.exe' }
    if (-not (Test-Path -LiteralPath $script:gh)) { throw 'Install/authenticate the official GitHub CLI first' }
    if (Test-Path -LiteralPath '.tools/gh-config') { $env:GH_CONFIG_DIR = Join-Path $root '.tools/gh-config' }
    $info = (Invoke-Gh repo view $Repo --json 'nameWithOwner,isPrivate,url') | ConvertFrom-Json
    if (-not $info.isPrivate) { throw 'Refusing to publish this development project to a public repository' }
    $origin = Invoke-Git remote get-url origin
    if ($origin -notin @("https://github.com/$Repo", "https://github.com/$Repo.git", "git@github.com:$Repo.git")) {
        throw 'origin does not match the explicitly selected repository'
    }
    if (@(Invoke-Git status --porcelain).Count) { throw 'Review and commit source/docs first; this script never auto-stages files' }
    $commit = Invoke-Git rev-parse HEAD
    $branch = Invoke-Git branch --show-current
    if (-not $branch) { throw 'Detached HEAD: choose a branch before publishing' }
    if (@(Invoke-Git tag --list $tag).Count -or @(Invoke-Git ls-remote --tags origin "refs/tags/$tag").Count) { throw "Tag $tag already exists; do not overwrite it" }
    $olderTags = @(Invoke-Git tag --list 'v*' --sort=-version:refname)
    if ($olderTags.Count) {
        $old = (Invoke-Git show "$($olderTags[0]):app/build.gradle.kts") -join "`n"
        if ($old -match 'versionCode\s*=\s*([0-9]+)' -and $versionCode -le [int]$Matches[1]) { throw 'Increase versionCode before a new APK release' }
    }
    if (Test-Path -LiteralPath 'C:/jtmp') { $env:TEMP = 'C:\jtmp'; $env:TMP = 'C:\jtmp' }
    & '.\gradlew.bat' -I tools/ascii-build-dir.init.gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Verification/build failed; nothing pushed or released' }
    if (@(Invoke-Git status --porcelain).Count -or (Invoke-Git rev-parse HEAD) -ne $commit) { throw 'Checkout changed during verification; review before publishing' }
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Verified APK not found in the expected build directory' }
    $sdk = $env:ANDROID_SDK_ROOT
    if (-not $sdk) { $sdk = $env:ANDROID_HOME }
    if (-not $sdk) {
        $line = Get-Content -LiteralPath local.properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if (-not $line) { throw 'Set ANDROID_SDK_ROOT or local.properties for APK verification' }
        $sdk = $line.Substring(8).Replace('\:', ':').Replace('\\', '\').Replace('\ ', ' ')
    }
    $tool = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object Name -Match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $tool) { throw 'Android build-tools missing' }
    $cert = & (Join-Path $tool.FullName 'apksigner.bat') verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    if (($cert -join "`n") -notmatch '(?im)^Signer #1 certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$') { throw 'APK signer fingerprint missing' }
    $signer = $Matches[1].ToLowerInvariant()
    $expectedSigner = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release-signing-certificate.sha256') -Raw).Trim().ToLowerInvariant()
    if ($signer -ne $expectedSigner) { throw 'Signer changed: refusing an incompatible update' }
    $badging = & (Join-Path $tool.FullName 'aapt2.exe') dump badging $apk
    if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='com.example.asmrplayer' versionCode='$versionCode' versionName='$([regex]::Escape($version))'") {
        throw 'APK package/version does not match the committed configuration'
    }
    $tests = 0
    Get-ChildItem -LiteralPath (Join-Path $build 'test-results/testDebugUnitTest') -Filter 'TEST-*.xml' | ForEach-Object {
        [xml]$suite = Get-Content -LiteralPath $_.FullName -Raw
        if ([int]$suite.testsuite.failures -or [int]$suite.testsuite.errors) { throw 'Failed JVM tests' }
        $tests += [int]$suite.testsuite.tests
    }
    if ($tests -le 0) { throw 'No JVM results found' }
    [xml]$lint = Get-Content -LiteralPath (Join-Path $build 'reports/lint-results-debug.xml') -Raw
    if (@($lint.issues.issue | Where-Object severity -In @('Error','Fatal')).Count) { throw 'Lint errors' }
    $warnings = @($lint.issues.issue | Where-Object severity -EQ Warning).Count
    $notes = Get-Content -LiteralPath CHANGELOG.md -Raw -Encoding UTF8
    $section = [regex]::Match($notes, "(?ms)^##\s+$([regex]::Escape($version))\b.*?(?=^##\s|\z)")
    if (-not $section.Success) { throw 'Add a version section to CHANGELOG.md first' }
    $out = Join-Path $root "build/jokesong-release/$tag"
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    $name = 'JOKESONG.apk' # Stable /releases/latest/download/JOKESONG.apk across versions.
    $asset = Join-Path $out $name
    Copy-Item -LiteralPath $apk -Destination $asset -Force
    $sha = (Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash.ToLowerInvariant()
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $manifest = [ordered]@{ applicationId='com.example.asmrplayer'; versionName=$version; versionCode=$versionCode; gitCommit=$commit; verifiedAtUtc=[DateTime]::UtcNow.ToString('o'); apkBuiltAtUtc=(Get-Item -LiteralPath $apk).LastWriteTimeUtc.ToString('o'); apk=$name; bytes=(Get-Item -LiteralPath $asset).Length; sha256=$sha; signingCertificateSha256=$signer; jvmTests=$tests; lintErrors=0; lintWarnings=$warnings; deviceVerification='Not performed by this publisher; consult release notes and HANDOFF for owner reports' }
    [IO.File]::WriteAllText((Join-Path $out 'JOKESONG-build.json'), ($manifest | ConvertTo-Json), $utf8)
    [IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS.txt'), "$sha  $name`n", $utf8)
    $receipt = $section.Value.Trim() + "`n`nCommit: $commit`nJVM tests: $tests; lint errors: 0; lint warnings: $warnings.`nAPK SHA256: $sha`n"
    [IO.File]::WriteAllText((Join-Path $out 'release-notes.md'), $receipt, $utf8)
    Invoke-Git push origin $branch | Out-Host
    $remote = Invoke-Git ls-remote origin "refs/heads/$branch"
    if (($remote -split '\s+')[0] -ne $commit) { throw 'Remote branch does not match the verified commit' }
    Invoke-Git tag $tag $commit
    Invoke-Git push origin "refs/tags/$tag" | Out-Host
    Invoke-Gh release create $tag $asset (Join-Path $out 'SHA256SUMS.txt') (Join-Path $out 'JOKESONG-build.json') --repo $Repo --verify-tag --title "JOKESONG $version" --notes-file (Join-Path $out 'release-notes.md') --latest | Out-Host
    $release = (Invoke-Gh api "repos/$Repo/releases/tags/$tag") | ConvertFrom-Json
    foreach ($file in @($asset, (Join-Path $out 'SHA256SUMS.txt'), (Join-Path $out 'JOKESONG-build.json'))) {
        $local = Get-Item -LiteralPath $file
        $remoteAsset = @($release.assets | Where-Object name -EQ $local.Name)
        $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($remoteAsset.Count -ne 1 -or $remoteAsset[0].size -ne $local.Length -or $remoteAsset[0].digest -ne "sha256:$hash") {
            throw "Uploaded asset verification failed: $($local.Name). Preserve the release and investigate; do not overwrite it."
        }
    }
    Write-Output "Verified release: $($release.html_url)"
    Write-Output "Phone download: $($info.url)/releases/latest/download/$name"
} finally { Set-Location -LiteralPath $originalLocation }
