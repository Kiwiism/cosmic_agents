@echo off
@title Cosmic
if not exist logs\gc mkdir logs\gc
if not exist logs\heapdumps mkdir logs\heapdumps
if not exist logs\crash mkdir logs\crash
java -Xmx2048m %JAVA_OPTS% -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs\heapdumps -XX:+ExitOnOutOfMemoryError -XX:ErrorFile=logs\crash\hs_err_pid%%p.log -Xlog:gc*,safepoint:file=logs/gc/gc.log:time,uptime,level,tags:filecount=10,filesize=20M -Dagents.scheduler.mode=central-sequential -Dwz-path=wz -jar target\Cosmic.jar
pause
