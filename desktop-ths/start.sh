#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p .build
if [ ! -x .build/ths-reader ] || [ native/main.swift -nt .build/ths-reader ]; then
    swiftc native/main.swift -o .build/ths-reader
fi
exec python3 server.py
