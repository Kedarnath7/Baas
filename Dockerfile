FROM ubuntu:latest
LABEL authors="kedar"

ENTRYPOINT ["top", "-b"]