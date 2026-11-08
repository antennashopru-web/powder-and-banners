#!/bin/sh
# Скачивает CC0-модели KayKit (Kay Lousberg) в tools/sprites/kk
set -e; cd "$(dirname "$0")"; T=$(mktemp -d)
git clone --depth 1 https://github.com/KayKit-Game-Assets/KayKit-Character-Pack-Adventures-1.0 "$T/chars"
git clone --depth 1 https://github.com/KayKit-Game-Assets/KayKit-Medieval-Hexagon-Pack-1.0 "$T/med"
mkdir -p kk/chars kk/med
cp "$T"/chars/addons/kaykit_character_pack_adventures/Characters/gltf/* kk/chars/
cp -r "$T"/med/addons/kaykit_medieval_hexagon_pack/Assets/gltf/* kk/med/
rm -rf "$T"
