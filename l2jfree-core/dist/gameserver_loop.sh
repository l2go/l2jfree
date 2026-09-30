#!/bin/bash

err=1
until [ $err == 0 ];
do
	. ./setenv.sh
	[ -d log/ ] || mkdir log/
	[ -f log/stdout.log ] && mv log/stdout.log "log/stdout/`date +%Y-%m-%d_%H-%M-%S`_stdout.log"
# For developers mostly (1. line gc logrotate, 2. line parameters for gc logging):
#	[ -f log/gc.log ] && mv log/gc.log "log/gc/`date +%Y-%m-%d_%H-%M-%S`_gc.log"
#	-verbose:gc -Xloggc:log/gc.log -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintHeapAtGC -XX:+PrintTenuringDistribution
	java ${JAVA_AGENT_OPTS} -Xmn128m -Xms512m -Xmx1024m -server com.l2jfree.gameserver.GameServer > log/stdout.log 2>&1
	err=$?
	sleep 10
done
