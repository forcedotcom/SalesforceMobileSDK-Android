#!/bin/bash
if [ ! -d "external" ]
then
    echo "You must run this tool from the root directory of your repo clone"
else
    ./gradlew dokkaGeneratePublicationHtml
fi
