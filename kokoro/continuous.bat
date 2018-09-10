@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%ProgramFiles%\Docker\Docker\resources;%JAVA_HOME%\bin;%PATH%

cd github/jib

where docker
whic docker
whereis docker
where dockerd
which dockerd
whereis dockerd

ls -al "/cygdrive/c/Program Files"
ls -al "/cygdrive/c/Program Files/Docker"
ls -al "/cygdrive/c/Program Files/Docker/Docker"
ls -al "/cygdrive/c/Program Files/Docker/Docker/resources"

docker version

REM Stops any left-over containers.
FOR /f "tokens=*" %%i IN ('docker ps -aq') DO docker rm -vf %%i

REM Sets the integration testing project.
set JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

docker version

REM cat %USERPROFILE%\.docker\config.json
REM COPY kokoro\config.json %USERPROFILE%\.docker\config.json
REM cat %USERPROFILE%\.docker\config.json

REM cat %USERPROFILE%\.docker\daemon.json
REM COPY kokoro\daemon-user.json %USERPROFILE%\.docker\daemon.json
REM cat %USERPROFILE%\.docker\daemon.json

REM cat %ProgramData%\Docker\config\daemon.json
REM COPY kokoro\daemon.json %ProgramData%\Docker\config\daemon.json
REM cat %ProgramData%\Docker\config\daemon.json

REM tasklist

REM sc queryex com.docker.service
REM sc stop com.docker.service

REM :CheckDockerStopped
REM sc query com.docker.service
REM sc query com.docker.service | FINDSTR "STOPPED"
REM IF ERRORLEVEL 1 (
  REM sleep 3s
  REM GOTO CheckDockerStopped
REM )

REM docker version
REM docker images
REM docker pull microsoft/nanoserver
REM docker rmi microsoft/nanoserver
REM docker pull registry:2
REM docker pull --platform linux registry:2

REM tasklist

Taskkill /IM dockerd.exe /F

Tasklist

REM net start com.docker.service

REM sc queryex com.docker.service
REM sc start com.docker.service

REM :CheckDockerRunning
REM sc query com.docker.service
REM sc query com.docker.service | FINDSTR "RUNNING"
REM IF ERRORLEVEL 1 (
REM   sleep 3s
REM   GOTO CheckDockerRunning
REM )

REM tasklist

REM ls -al "/cygdrive/c/Program Files/Docker/Docker/resources/"

CMD /C START dockerd --experimental

:CheckDockerUp
docker info
IF ERRORLEVEL 1 (
  sleep 3s
  GOTO CheckDockerUp
)

docker version
docker images
REM docker pull microsoft/nanoserver
REM docker rmi microsoft/nanoserver
docker pull registry:2
docker pull --platform linux registry:2

tasklist

REM REM docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
REM set GOOGLE_APPLICATION_CREDENTIALS=%KOKORO_KEYSTORE_DIR%\72743_jib_integration_testing_key
REM docker-credential-gcr configure-docker

REM cd jib-core && call gradlew.bat clean build integrationTest --info --stacktrace && ^
REM cd ../jib-plugins-common && call gradlew.bat clean build --info --stacktrace && ^
REM cd ../jib-maven-plugin && call mvnw.cmd clean install -P integration-tests -B -U -X && ^
REM cd ../jib-gradle-plugin && call gradlew.bat clean build integrationTest --info --stacktrace

exit /b %ERRORLEVEL%
