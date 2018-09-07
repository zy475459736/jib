@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/jib

docker version

REM REM Stops any left-over containers.
REM FOR /f "tokens=*" %%i IN ('docker ps -aq') DO docker rm -vf %%i

REM Sets the integration testing project.
set JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

docker version

cat %USERPROFILE%\.docker\config.json
COPY kokoro\config.json %USERPROFILE%\.docker\config.json
cat %USERPROFILE%\.docker\config.json

cat %USERPROFILE%\.docker\daemon.json
COPY kokoro\daemon-user.json %USERPROFILE%\.docker\daemon.json
cat %USERPROFILE%\.docker\daemon.json

cat %ProgramData%\Docker\config\daemon.json
COPY kokoro\daemon.json %ProgramData%\Docker\config\daemon.json
cat %ProgramData%\Docker\config\daemon.json

tasklist

sc queryex com.docker.service
sc stop com.docker.service

:CheckDockerStopped
sc query com.docker.service
sc query com.docker.service | FINDSTR "STOPPED"
IF ERRORLEVEL 1 (
  sleep 3s
  GOTO CheckDockerStopped
)

sc queryex dockerd
sc stop dockerd

:CheckDockerDStopped
sc query dockerd
sc query dockerd | FINDSTR "STOPPED"
IF ERRORLEVEL 1 (
  sleep 3s
  GOTO CheckDockerDStopped
)

docker version
docker images
docker pull microsoft/nanoserver
docker rmi microsoft/nanoserver
docker pull registry:2
docker pull --platform linux registry:2

tasklist

sc queryex com.docker.service
sc start com.docker.service

:CheckDockerRunning
sc query com.docker.service
sc query com.docker.service | FINDSTR "RUNNING"
IF ERRORLEVEL 1 (
  sleep 3s
  GOTO CheckDockerRunning
)

tasklist

sc queryex dockerd
sc start dockerd

:CheckDockerDRunning
sc query dockerd
sc query dockerd | FINDSTR "RUNNING"
IF ERRORLEVEL 1 (
  sleep 3s
  GOTO CheckDockerDRunning
)

tasklist

docker version
docker images
docker pull microsoft/nanoserver
docker rmi microsoft/nanoserver
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
