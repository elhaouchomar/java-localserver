#!/bin/bash

INSTALL_DIR="$HOME/.local"
mkdir -p "$INSTALL_DIR"

wget -q https://download.joedog.org/siege/siege-4.1.6.tar.gz -O siege.tar.gz

tar xzf siege.tar.gz
cd siege-4.1.6 || exit 1

./configure --prefix="$INSTALL_DIR"

make
make install

export PATH="$INSTALL_DIR/bin:$PATH"


TEST_IP="127.0.0.1"
TEST_PORT="8080"

siege -b "$TEST_IP:$TEST_PORT"