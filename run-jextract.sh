#!/bin/sh
JAVA_HOME=/home/khadas/no-jni/zulu23.32.11-ca-jdk23.0.2-linux_aarch64/
PATH=${JAVA_HOME}/bin:${PATH}
../jextract-22/bin/jextract \
  --include-dir /usr/include \
  --output src/main/java \
  --target-package pe.pi.v4l2Reader.videodev2 \
  --library libvpcodec \
  /usr/include/linux/videodev2.h

#--dump-includes includes.txt \
