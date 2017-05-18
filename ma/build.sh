#!/bin/bash

if [ ! -d "build" ]; then
  mkdir build
fi

cp chapters/* build
cp images/* build
cp bibtex/* build

cd build

pdflatex -draftmode 0_main.tex
bibtex 0_main
pdflatex -draftmode 0_main.tex
pdflatex 0_main.tex

if [ $? == 0 ]; then
  cp 0_main.pdf ../ma.pdf
fi
