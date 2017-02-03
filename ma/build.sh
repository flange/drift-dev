#!/bin/bash

cp chapters/* build
cp images/* build
cp bibtex/* build

cd build

pdflatex 0_main.tex
bibtex 0_main
pdflatex 0_main.tex
pdflatex 0_main.tex


if [ $? == 0 ]; then
  echo "yo"
  cp 0_main.pdf ../ma.pdf
fi
