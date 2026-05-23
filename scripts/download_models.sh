#!/bin/bash
# STT 모델 다운로드 후 adb로 기기에 설치
set -e
PACKAGE="com.subtitleapp"
MODELS_DIR="/data/data/$PACKAGE/files/models"
TMP="/tmp/subtitle_models"
mkdir -p "$TMP" && cd "$TMP"

echo "=== [1/3] Vosk 영어 소형 모델 (~50MB) ==="
[ ! -d "vosk-small-en" ] && \
  curl -L -o vosk.zip "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip" && \
  unzip -q vosk.zip && mv vosk-model-small-en-us-0.15 vosk-small-en
echo "OK"

echo "=== [2/3] Whisper tiny (~150MB) ==="
[ ! -d "sherpa-whisper-tiny" ] && \
  curl -L -o tiny.tar.bz2 "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2" && \
  tar xf tiny.tar.bz2 && mv sherpa-onnx-whisper-tiny.en sherpa-whisper-tiny
echo "OK"

echo "=== [3/3] Whisper base (~300MB) ==="
[ ! -d "sherpa-whisper-base" ] && \
  curl -L -o base.tar.bz2 "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.en.tar.bz2" && \
  tar xf base.tar.bz2 && mv sherpa-onnx-whisper-base.en sherpa-whisper-base
echo "OK"

echo "=== adb 설치 중 ==="
adb shell mkdir -p "$MODELS_DIR" 2>/dev/null || true
for model in vosk-small-en sherpa-whisper-tiny sherpa-whisper-base; do
  echo "  $model ..."
  adb push "$model" "$MODELS_DIR/$model"
done
echo "완료: $MODELS_DIR"
