@echo off

setlocal
set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.1"
set JDK_HOME=%JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

call mvn clean package -T 2C

del target\*SNAPSHOT.jar
move /Y target\qw-jar-with-dependencies.jar target\qw.jar
endlocal
