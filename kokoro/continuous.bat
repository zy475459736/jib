@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%ProgramFiles%\Docker\Docker\resources;%JAVA_HOME%\bin;%PATH%

cd github/jib

docker version

REM Stops any left-over containers.
FOR /f "tokens=*" %%i IN ('docker ps -aq') DO docker rm -vf %%i

REM Sets the integration testing project.
set JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

docker version

cat %USERPROFILE%\.docker\config.json
COPY kokoro\config.json %USERPROFILE%\.docker\config.json
cat %USERPROFILE%\.docker\config.json

REM cat %USERPROFILE%\.docker\daemon.json
REM COPY kokoro\daemon-user.json %USERPROFILE%\.docker\daemon.json
REM cat %USERPROFILE%\.docker\daemon.json

cat %ProgramData%\Docker\config\daemon.json
COPY kokoro\daemon.json %ProgramData%\Docker\config\daemon.json
cat %ProgramData%\Docker\config\daemon.json

Taskkill /IM dockerd.exe /F

Tasklist

REM Below doesn't work becuase --experimental is given in the daemon.json too.
REM (Can only be specified either in daemon.json or through the flag.)
REM dockerd --experimental

CMD /C START dockerd

Tasklist

REM sleep 60s

:CheckDockerUp
docker info
IF ERRORLEVEL 1 (
  sleep 3s
  GOTO CheckDockerUp
)

REM docker version
REM docker images
REM docker pull registry:2
REM docker pull --platform linux registry:2

tasklist

REM docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
set GOOGLE_APPLICATION_CREDENTIALS=%KOKORO_KEYSTORE_DIR%\72743_jib_integration_testing_key
docker-credential-gcr configure-docker

cd jib-core && call gradlew.bat clean build integrationTest --info --stacktrace && ^
cd ../jib-plugins-common && call gradlew.bat clean build --info --stacktrace && ^
cd ../jib-maven-plugin && call mvnw.cmd clean install -P integration-tests -B -U -X && ^
cd ../jib-gradle-plugin && call gradlew.bat clean build integrationTest --info --stacktrace

exit /b %ERRORLEVEL%
