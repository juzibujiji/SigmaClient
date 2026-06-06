[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Maven {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $mavenArguments = @("--batch-mode", "-Dstyle.color=never") + $Arguments
    & mvn @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven command failed with exit code ${LASTEXITCODE}: mvn $($mavenArguments -join ' ')"
    }
}

function Get-Sha1Hex {
    param([Parameter(Mandatory = $true)][string]$Path)

    $sha1File = "$Path.sha1"
    if (Test-Path -LiteralPath $sha1File) {
        return ((Get-Content -LiteralPath $sha1File -Raw).Trim()).ToLowerInvariant()
    }

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash.ToLowerInvariant()
}

function Get-RepositoryMap {
    param([Parameter(Mandatory = $true)][xml]$Pom)

    $map = [ordered]@{}

    foreach ($repo in @($Pom.project.repositories.repository)) {
        if ($null -ne $repo.id -and $null -ne $repo.url) {
            $map[[string]$repo.id] = ([string]$repo.url).TrimEnd("/")
        }
    }

    if (-not $map.Contains("central")) {
        $map["central"] = "https://repo1.maven.org/maven2"
    }

    return $map
}

function Get-SnapshotResolvedVersion {
    param(
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
        [Parameter(Mandatory = $true)][string]$RepositoryId,
        [AllowEmptyString()][string]$Classifier = ""
    )

    $metadataPath = Join-Path $ArtifactDirectory "maven-metadata-$RepositoryId.xml"
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        throw "Missing snapshot metadata: $metadataPath"
    }

    [xml]$metadata = Get-Content -LiteralPath $metadataPath
    foreach ($snapshotVersion in @($metadata.metadata.versioning.snapshotVersions.snapshotVersion)) {
        $extension = [string]$snapshotVersion.extension
        $classifierProperty = $snapshotVersion.PSObject.Properties["classifier"]
        $snapshotClassifier = if ($null -ne $classifierProperty) { [string]$classifierProperty.Value } else { "" }
        if ($extension -ne "jar") {
            continue
        }

        if (($Classifier -eq "" -and [string]::IsNullOrEmpty($snapshotClassifier)) -or ($Classifier -eq $snapshotClassifier)) {
            return [string]$snapshotVersion.value
        }
    }

    $timestamp = [string]$metadata.metadata.versioning.snapshot.timestamp
    $buildNumber = [string]$metadata.metadata.versioning.snapshot.buildNumber
    if ([string]::IsNullOrWhiteSpace($timestamp) -or [string]::IsNullOrWhiteSpace($buildNumber)) {
        throw "Unable to resolve snapshot version from $metadataPath"
    }

    return ([string]$metadata.metadata.version).Replace("-SNAPSHOT", "") + "-$timestamp-$buildNumber"
}

function Get-ResolvedArtifactInfo {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Dependency,
        [Parameter(Mandatory = $true)][hashtable]$RepositoryMap
    )

    $jarPath = $Dependency.Path
    $artifactDirectory = Split-Path -Parent $jarPath
    $repoMetadataPath = Join-Path $artifactDirectory "_remote.repositories"
    $repoId = $null

    if (Test-Path -LiteralPath $repoMetadataPath) {
        $repoLines = Get-Content -LiteralPath $repoMetadataPath | Where-Object { $_ -match ">" -and $_ -match "=" }
        if ($repoLines.Count -gt 0) {
            $repoId = (($repoLines[0] -split ">", 2)[1] -split "=", 2)[0]
        }
    }

    if ([string]::IsNullOrWhiteSpace($repoId)) {
        $repoId = "central"
    }

    if (-not $RepositoryMap.ContainsKey($repoId)) {
        throw "Unknown Maven repository id '$repoId' for $($Dependency.GroupId):$($Dependency.ArtifactId):$($Dependency.Version)"
    }

    $classifier = [string]$Dependency.Classifier
    $resolvedVersion = [string]$Dependency.Version
    $fileNameVersion = $resolvedVersion

    if ($resolvedVersion.EndsWith("-SNAPSHOT")) {
        $fileNameVersion = Get-SnapshotResolvedVersion -ArtifactDirectory $artifactDirectory -RepositoryId $repoId -Classifier $classifier
    }

    $fileName = if ([string]::IsNullOrWhiteSpace($classifier)) {
        "$($Dependency.ArtifactId)-$fileNameVersion.jar"
    } else {
        "$($Dependency.ArtifactId)-$fileNameVersion-$classifier.jar"
    }

    $path = "{0}/{1}/{2}/{3}" -f `
        $Dependency.GroupId.Replace(".", "/"), `
        $Dependency.ArtifactId, `
        $Dependency.Version, `
        $fileName

    return @{
        FileName = $fileName
        Path = $path
        Url = "{0}/{1}" -f $RepositoryMap[$repoId], $path
        Sha1 = Get-Sha1Hex -Path $jarPath
        Size = (Get-Item -LiteralPath $jarPath).Length
        RepositoryId = $repoId
    }
}

function Parse-RuntimeDependencies {
    param([Parameter(Mandatory = $true)][string]$Path)

    $escape = [regex]::Escape([string][char]27)
    $ansiPattern = "${escape}\[[0-9;]*[A-Za-z]"
    $pattern = '^\s*(?<groupId>[^:\s]+):(?<artifactId>[^:]+):jar(?::(?<classifier>[^:]+))?:(?<version>[^:]+):(?<scope>[^:]+):(?<path>[A-Za-z]:\\.+?)(?:\s+--.*)?$'
    $results = @()

    foreach ($line in Get-Content -LiteralPath $Path) {
        $cleanLine = ($line -replace $ansiPattern, "").Trim()
        if ($cleanLine -notmatch $pattern) {
            continue
        }

        $classifier = if ($matches["classifier"]) { $matches["classifier"] } else { "" }

        if ($classifier -eq "natives-linux" -or $classifier -eq "natives-osx" -or $classifier -eq "natives-macos" -or $classifier -eq "natives-macos-arm64" -or $classifier -eq "natives-linux-arm64" -or $classifier -eq "natives-linux-arm32") {
            continue
        }

        $results += @{
            GroupId = $matches["groupId"]
            ArtifactId = $matches["artifactId"]
            Classifier = $classifier
            Version = $matches["version"]
            Scope = $matches["scope"]
            Path = $matches["path"].Trim()
        }
    }

    return $results
}

function Get-LegacyLibraryHints {
    param([Parameter(Mandatory = $true)][string]$LegacyJsonPath)

    if (-not (Test-Path -LiteralPath $LegacyJsonPath)) {
        return @{}
    }

    $legacyJson = Get-Content -LiteralPath $LegacyJsonPath -Raw | ConvertFrom-Json
    $hints = @{}

    foreach ($library in @($legacyJson.libraries)) {
        $name = [string]$library.name
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }

        if (-not $hints.ContainsKey($name)) {
            $hints[$name] = @()
        }

        $hints[$name] += $library
    }

    return $hints
}

function Merge-LegacyHints {
    param(
        [Parameter(Mandatory = $true)][System.Collections.Specialized.OrderedDictionary]$Library,
        [Parameter(Mandatory = $true)][hashtable]$LegacyHints
    )

    $name = [string]$Library["name"]
    if (-not $LegacyHints.ContainsKey($name)) {
        return
    }

    if ($name -notin @("com.mojang:text2speech:1.11.3", "ca.weblite:java-objc-bridge:1.0.0")) {
        return
    }

    foreach ($candidate in $LegacyHints[$name]) {
        if ($candidate.PSObject.Properties.Name -contains "extract" -and -not $Library.Contains("extract")) {
            $Library["extract"] = $candidate.extract
        }

        if ($candidate.PSObject.Properties.Name -contains "rules" -and -not $Library.Contains("rules")) {
            $Library["rules"] = $candidate.rules
        }

        if ($candidate.PSObject.Properties.Name -contains "natives" -and -not $Library.Contains("natives")) {
            $Library["natives"] = $candidate.natives
        }

        $candidateDownloads = $candidate.downloads
        if ($null -eq $candidateDownloads) {
            continue
        }

        if ($candidateDownloads.PSObject.Properties.Name -contains "classifiers") {
            if (-not $Library["downloads"].Contains("classifiers")) {
                $Library["downloads"]["classifiers"] = @{}
            }

            foreach ($prop in $candidateDownloads.classifiers.PSObject.Properties) {
                if (-not $Library["downloads"]["classifiers"].Contains($prop.Name)) {
                    $Library["downloads"]["classifiers"][$prop.Name] = $prop.Value
                }
            }
        }
    }
}

function Build-LibraryList {
    param(
        [Parameter(Mandatory = $true)][array]$Dependencies,
        [Parameter(Mandatory = $true)][hashtable]$RepositoryMap,
        [Parameter(Mandatory = $true)][hashtable]$LegacyHints
    )

    $grouped = [ordered]@{}
    foreach ($dependency in $Dependencies) {
        $key = "{0}:{1}:{2}" -f $dependency.GroupId, $dependency.ArtifactId, $dependency.Version
        if (-not $grouped.Contains($key)) {
            $grouped[$key] = [ordered]@{
                GroupId = $dependency.GroupId
                ArtifactId = $dependency.ArtifactId
                Version = $dependency.Version
                Base = $null
                Classifiers = [ordered]@{}
            }
        }

        $resolvedInfo = Get-ResolvedArtifactInfo -Dependency $dependency -RepositoryMap $RepositoryMap
        $artifactRecord = [ordered]@{
            path = $resolvedInfo.Path
            sha1 = $resolvedInfo.Sha1
            size = [long]$resolvedInfo.Size
            url = $resolvedInfo.Url
        }

        if ([string]::IsNullOrWhiteSpace($dependency.Classifier)) {
            $grouped[$key]["Base"] = $artifactRecord
        } else {
            $grouped[$key]["Classifiers"][$dependency.Classifier] = $artifactRecord
        }
    }

    $libraries = New-Object System.Collections.ArrayList

    foreach ($entry in $grouped.Values) {
        $libraryName = "{0}:{1}:{2}" -f $entry.GroupId, $entry.ArtifactId, $entry.Version
        $downloads = [ordered]@{}

        if ($null -ne $entry.Base) {
            $downloads["artifact"] = $entry.Base
        }

        if ($entry.Classifiers.Count -gt 0) {
            $downloads["classifiers"] = [ordered]@{}
            foreach ($classifierKey in $entry.Classifiers.Keys) {
                $downloads["classifiers"][$classifierKey] = $entry.Classifiers[$classifierKey]
            }
        }

        $library = [ordered]@{
            name = $libraryName
            downloads = $downloads
        }

        if ($entry.Classifiers.Contains("natives-windows")) {
            $library["natives"] = [ordered]@{
                windows = "natives-windows"
            }
            $library["rules"] = @(
                [ordered]@{
                    action = "allow"
                    os = [ordered]@{
                        name = "windows"
                    }
                }
            )
        }

        if ($entry.GroupId -eq "org.lwjgl" -and $null -ne $entry.Base -and $entry.Classifiers.Contains("natives-windows")) {
            # Some third-party launchers fail to add the artifact jar to the classpath when a
            # single library entry also declares Windows natives. Emit a plain artifact-only
            # companion entry so classes like org.lwjgl.BufferUtils are always available.
            $classpathCompanion = [ordered]@{
                name = $libraryName
                downloads = [ordered]@{
                    artifact = $entry.Base
                }
                rules = @(
                    [ordered]@{
                        action = "allow"
                        os = [ordered]@{
                            name = "windows"
                        }
                    }
                )
            }

            [void]$libraries.Add($classpathCompanion)
        }

        if ($libraryName -eq "com.mojang:text2speech:1.11.3" -and $null -ne $entry.Base) {
            # HMCL follows the same pattern as Mojang's original metadata here: one plain
            # classpath entry plus one natives entry. Without the extra artifact-only entry,
            # Narrator classes may be omitted from the classpath on Windows.
            $classpathCompanion = [ordered]@{
                name = $libraryName
                downloads = [ordered]@{
                    artifact = $entry.Base
                }
            }

            [void]$libraries.Add($classpathCompanion)
        }

        if ($libraryName -eq "ca.weblite:java-objc-bridge:1.0.0" -and $null -ne $entry.Base) {
            $classpathCompanion = [ordered]@{
                name = $libraryName
                downloads = [ordered]@{
                    artifact = $entry.Base
                }
                rules = @(
                    [ordered]@{
                        action = "allow"
                        os = [ordered]@{
                            name = "osx"
                        }
                    }
                )
            }

            [void]$libraries.Add($classpathCompanion)
        }

        if ($entry.ArtifactId -eq "jinput-platform" -and $entry.Classifiers.Contains("natives-windows")) {
            $library["extract"] = [ordered]@{
                exclude = @("META-INF/")
            }
        }

        Merge-LegacyHints -Library $library -LegacyHints $LegacyHints
        [void]$libraries.Add($library)
    }

    return $libraries
}

function Assert-RequiredLibraries {
    param([Parameter(Mandatory = $true)][array]$Libraries)

    $required = @(
        "org.lwjgl:lwjgl-nanovg:3.3.6",
        "com.google.zxing:core:3.5.3",
        "com.google.zxing:javase:3.5.3",
        "club.minnced:discord-rpc-release:v3.3.0",
        "com.beust:jcommander:1.82",
        "com.github.jai-imageio:jai-imageio-core:1.4.0",
        "com.ibm.icu:icu4j-core-mojang:51.2",
        "commons-io:commons-io:2.14.0",
        "io.netty:netty-all:4.1.42.Final",
        "org.apache.commons:commons-compress:1.26.0",
        "org.apache.logging.log4j:log4j-api:2.17.1",
        "org.apache.logging.log4j:log4j-core:2.17.1"
    )

    $libraryNames = @($Libraries | ForEach-Object { [string]$_.name })
    foreach ($requiredName in $required) {
        if ($libraryNames -notcontains $requiredName) {
            throw "Required runtime library is missing from generated launcher JSON: $requiredName"
        }
    }
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$targetDir = Join-Path $repoRoot "target"
$distDir = Join-Path $repoRoot "dist"
$templatePath = Join-Path $repoRoot "release\launcher-template.json"
$legacyReleasePath = Join-Path $repoRoot "release\sigma-jello-5.1.0.json"
$runtimeDepsPath = Join-Path $targetDir "runtime-deps.txt"

Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath $templatePath)) {
        throw "Missing launcher template: $templatePath"
    }

    [xml]$pom = Get-Content -LiteralPath "pom.xml"
    $artifactId = [string]$pom.project.artifactId
    $version = [string]$pom.project.version
    $versionId = "$artifactId-$version"
    $jarPath = Join-Path $targetDir "$versionId.jar"
    $versionDir = Join-Path $distDir "$versionId-launcher"
    $versionRootDir = Join-Path $versionDir "versions\$versionId"
    $outputJarPath = Join-Path $versionRootDir "$versionId.jar"
    $outputJsonPath = Join-Path $versionRootDir "$versionId.json"
    $readmePath = Join-Path $versionDir "README.txt"
    $zipPath = Join-Path $distDir "$versionId-launcher.zip"

    Write-Step "Building Maven jar"
    Invoke-Maven -Arguments @("-DskipTests", "package")

    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "Maven build completed without producing $jarPath"
    }

    Write-Step "Resolving runtime dependencies"
    Invoke-Maven -Arguments @(
        "dependency:list",
        "-DincludeScope=runtime",
        "-DoutputAbsoluteArtifactFilename=true",
        "-DoutputFile=target/runtime-deps.txt",
        "-DappendOutput=false"
    )

    if (-not (Test-Path -LiteralPath $runtimeDepsPath)) {
        throw "Missing runtime dependency report: $runtimeDepsPath"
    }

    Write-Step "Generating launcher JSON"
    $template = Get-Content -LiteralPath $templatePath -Raw | ConvertFrom-Json
    $repositoryMap = Get-RepositoryMap -Pom $pom
    $legacyHints = Get-LegacyLibraryHints -LegacyJsonPath $legacyReleasePath
    $runtimeDependencies = Parse-RuntimeDependencies -Path $runtimeDepsPath
    $libraries = Build-LibraryList -Dependencies $runtimeDependencies -RepositoryMap $repositoryMap -LegacyHints $legacyHints
    Assert-RequiredLibraries -Libraries $libraries

    $clientSha1 = Get-Sha1Hex -Path $jarPath
    $clientSize = (Get-Item -LiteralPath $jarPath).Length

    $versionJson = [ordered]@{
        arguments = $template.arguments
        assetIndex = $template.assetIndex
        assets = $template.assets
        javaVersion = $template.javaVersion
        id = $versionId
        libraries = $libraries
        logging = $template.logging
        mainClass = $template.mainClass
        minimumLauncherVersion = $template.minimumLauncherVersion
        releaseTime = $template.releaseTime
        time = $template.time
        type = $template.type
        downloads = [ordered]@{
            client = [ordered]@{
                sha1 = $clientSha1
                size = [long]$clientSize
                url = "https://example.invalid/local-package-only/$versionId.jar"
            }
        }
    }

    Write-Step "Preparing launcher package layout"
    if (Test-Path -LiteralPath $versionDir) {
        try {
            Remove-Item -LiteralPath $versionDir -Recurse -Force
        }
        catch {
            $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
            $versionDir = Join-Path $distDir "$versionId-launcher-$timestamp"
            $versionRootDir = Join-Path $versionDir "versions\$versionId"
            $outputJarPath = Join-Path $versionRootDir "$versionId.jar"
            $outputJsonPath = Join-Path $versionRootDir "$versionId.json"
            $readmePath = Join-Path $versionDir "README.txt"
            $zipPath = Join-Path $distDir "$versionId-launcher-$timestamp.zip"

            Write-Host "Existing package directory is in use; writing rebuilt package to $versionDir" -ForegroundColor Yellow
        }
    }

    New-Item -ItemType Directory -Path $versionRootDir -Force | Out-Null
    Copy-Item -LiteralPath $jarPath -Destination $outputJarPath -Force
    $versionJson | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $outputJsonPath -Encoding UTF8

    $readme = @(
        "Sigma launcher package: $versionId",
        "",
        "Use on Windows third-party launchers such as HMCL, PCL2, or Plain Craft Launcher.",
        "",
        "How to install:",
        "1. Extract this archive into your .minecraft directory.",
        "2. Keep versions\$versionId\$versionId.jar and versions\$versionId\$versionId.json together.",
        "3. Start the launcher with Java 17.",
        "",
        "Notes:",
        "- This package does not bundle libraries.",
        "- The launcher will download required libraries on first launch.",
        "- The bundled version jar is already included locally in this package."
    ) -join [Environment]::NewLine
    Set-Content -LiteralPath $readmePath -Value $readme -Encoding UTF8

    Write-Step "Creating launcher zip"
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $versionDir "*") -DestinationPath $zipPath -CompressionLevel Optimal

    Write-Step "Verifying generated package"
    $generatedJson = Get-Content -LiteralPath $outputJsonPath -Raw | ConvertFrom-Json
    if ([string]$generatedJson.downloads.client.sha1 -ne $clientSha1) {
        throw "Generated launcher JSON client sha1 does not match local jar"
    }
    if ([long]$generatedJson.downloads.client.size -ne [long]$clientSize) {
        throw "Generated launcher JSON client size does not match local jar"
    }
    Assert-RequiredLibraries -Libraries @($generatedJson.libraries)

    Write-Step "Done"
    Write-Host "Launcher version id : $versionId" -ForegroundColor Green
    Write-Host "Version directory   : $versionDir" -ForegroundColor Green
    Write-Host "Version JSON        : $outputJsonPath" -ForegroundColor Green
    Write-Host "Launcher ZIP        : $zipPath" -ForegroundColor Green
}
finally {
    Pop-Location
}
