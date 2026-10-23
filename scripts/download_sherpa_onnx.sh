#!/usr/bin/env bash
# Puts the sherpa-onnx native libraries for the neural read-aloud voice into the reader module.
# They're too big for git. Without them the app still builds, and reading aloud uses the phone's voice.
set -euo pipefail

readonly VERSION="1.13.8"
readonly TARGET="features/reader/src/main/jniLibs/arm64-v8a"

if [[ -f "${TARGET}/libsherpa-onnx-jni.so" && -f "${TARGET}/libonnxruntime.so" ]]; then
  exit 0
fi

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
curl --fail --silent --show-error --location \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/sherpa-onnx-v${VERSION}-android.tar.bz2" \
  --output "${work}/android.tar.bz2"
tar -xjf "${work}/android.tar.bz2" -C "${work}" ./jniLibs/arm64-v8a/libsherpa-onnx-jni.so ./jniLibs/arm64-v8a/libonnxruntime.so
mkdir -p "${TARGET}"
cp "${work}/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" "${work}/jniLibs/arm64-v8a/libonnxruntime.so" "${TARGET}/"
