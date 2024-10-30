@echo off

set "args=%*"
set "currentDir=%~dp0"

"java.exe" -Dfile.encoding=UTF-8 -jar "%currentDir%\target\qw.jar" %args%
