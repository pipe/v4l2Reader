#!/bin/sh
JAVA_HOME=`/usr/libexec/java_home -v 23` export JAVA_HOME
PATH=${PATH}:/usr/local/apache-maven-3.6.3/bin:${JAVA_HOME}/bin
export PATH
which java
mvn clean install

