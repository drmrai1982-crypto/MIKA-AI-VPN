[CmdletBinding()]
param(
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64')]
    [string]$Abi = 'arm64-v8a',
    [int]$Api = 24
)

$ErrorActionPreference = 'Stop'

# Map Android ABI to Rust target triple
$targetTriple = switch ($Abi) {
    "arm64-v8a" { "aarch64-linux-android" }
    "armeabi-v7a" { "armv7-linux-androideabi" }
    "x86_64" { "x86_64-linux-android" }
}

# Map Android ABI to Clang target prefix
$clangPrefix = switch ($Abi) {
    "arm64-v8a" { "aarch64-linux-android" }
    "armeabi-v7a" { "armv7a-linux-androideabi" }
    "x86_64" { "x86_64-linux-android" }
}
$includeArch = switch ($Abi) {
    'arm64-v8a' { 'aarch64-linux-android' }
    'armeabi-v7a' { 'arm-linux-androideabi' }
    'x86_64' { 'x86_64-linux-android' }
}

# Ensure Rust target is installed
$installedTargets = & rustup target list --installed
if ($LASTEXITCODE -ne 0 -or $targetTriple -notin $installedTargets) {
    Write-Host "Error: Rust target $targetTriple is not installed." -ForegroundColor Red
    Write-Host "Please run: rustup target add $targetTriple" -ForegroundColor Yellow
    exit 1
}

$rustEnvSuffix = $targetTriple.ToUpper().Replace("-", "_")

$root = (Split-Path $PSScriptRoot -Parent).Replace('\', '/')
$crate = (Join-Path $PSScriptRoot 'aether').Replace('\', '/')
$target = (Join-Path $crate 'target-android').Replace('\', '/')

# Locate Android SDK
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$sdk = $sdk.Replace('\', '/')

# Locate NDK (try to find the version used in the project or any version)
$ndkBase = Join-Path $sdk 'ndk'
$ndkVersion = "26.3.11579264"
$ndk = (Join-Path $ndkBase $ndkVersion).Replace('\', '/')

if (-not (Test-Path -LiteralPath $ndk)) {
    # Fallback: find any installed NDK version
    $ndk = (Get-ChildItem -Path $ndkBase | Select-Object -First 1).FullName.Replace('\', '/')
}

$bin = (Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin').Replace('\', '/')

# Locate CMake
$cmake = (Join-Path $sdk 'cmake/3.22.1/bin/cmake.exe').Replace('\', '/')
if (-not (Test-Path -LiteralPath $cmake)) {
    $cmake = (Get-Command cmake.exe -ErrorAction SilentlyContinue).Source
}

if (-not $ndk -or -not $bin -or -not $cmake) {
    throw "Android build requirements missing (NDK or CMake). SDK path: $sdk"
}

$env:ANDROID_NDK_HOME = $ndk
$env:ANDROID_NDK_ROOT = $ndk
$env:CMAKE = $cmake
$env:CMAKE_GENERATOR = 'Ninja'
$env:CARGO_TARGET_DIR = $target
$env:PATH = "$(($cmake | Split-Path).Replace('\', '/'));$env:PATH"

# boring-sys 5.2 builds BoringSSL in one CMake configuration.
# Match the current Linux build path and bundle libc++ statically.
$rustTargetSuffix = $targetTriple.Replace('-', '_')
Set-Item "Env:BORING_BSSL_RUST_CPPLIB_$rustTargetSuffix" "static:-bundle=c++"

$sysroot = (Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/sysroot').Replace('\', '/')
$rustEnvSuffix = $targetTriple.ToUpper().Replace('-', '_')
$rustTargetSuffix = $targetTriple.Replace('-', '_')
Set-Item "Env:CARGO_TARGET_${rustEnvSuffix}_LINKER" (Join-Path $bin "$clangPrefix$Api-clang.cmd").Replace('\', '/')
Set-Item "Env:CARGO_TARGET_${rustEnvSuffix}_AR" (Join-Path $bin 'llvm-ar.exe').Replace('\', '/')
Set-Item "Env:AR_$rustTargetSuffix" (Get-Item "Env:CARGO_TARGET_${rustEnvSuffix}_AR").Value.Replace('\', '/')
Set-Item "Env:CC_$rustTargetSuffix" (Join-Path $bin 'clang.exe').Replace('\', '/')
Set-Item "Env:CXX_$rustTargetSuffix" (Join-Path $bin 'clang++.exe').Replace('\', '/')
Set-Item "Env:CFLAGS_$rustTargetSuffix" "--target=$clangPrefix$Api"
Set-Item "Env:CXXFLAGS_$rustTargetSuffix" "--target=$clangPrefix$Api"
Set-Item "Env:BINDGEN_EXTRA_CLANG_ARGS_$rustTargetSuffix" "--target=$clangPrefix$Api --sysroot=$sysroot -I$sysroot/usr/include/$includeArch"

# max-page-size enables 16 KB page compatibility. Do not add common-page-size:
# it can pad PT_GNU_RELRO beyond the end of its LOAD segment.
$env:RUSTFLAGS = "$env:RUSTFLAGS -C link-arg=-Wl,-soname,libaether.so -C link-arg=-Wl,-z,max-page-size=16384".Trim()

Push-Location $crate
try {
    cargo build --release --lib --target $targetTriple
    $library = (Join-Path $target "$targetTriple/release/libaether.so").Replace('\', '/')
    foreach ($destination in @(
        (Join-Path $root "core/android-libs/$Abi"),
        (Join-Path $root "app/src/main/jniLibs/$Abi")
    )) {
        $destPath = $destination.Replace('\', '/')
        New-Item -ItemType Directory -Path $destPath -Force | Out-Null
        Copy-Item -LiteralPath $library -Destination (Join-Path $destPath 'libaether.so').Replace('\', '/') -Force
    }
}
finally {
    Pop-Location
}
