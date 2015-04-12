#!/bin/sh

port=$1
model=$2
java -mx150m -cp ./*: edu.stanford.nlp.parser.server.LexicalizedParserServer2 \
    --port $port --model $model
