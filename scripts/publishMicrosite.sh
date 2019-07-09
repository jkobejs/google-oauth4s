#!/bin/bash
set -e

git config --global user.email "josip.grgurica@gmail.com"
git config --global user.name "Josip Grgurica"
git config --global push.default simple

sbt publishMicrosite
