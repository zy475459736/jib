@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

ls -al \cygdrive\c\Program\ Files\Docker
ls -al \cygdrive\c\Program\ Files\Docker\Docker
ls -al \cygdrive\c\Program\ Files\Docker\Docker\Resources
ls -al '\cygdrive\c\Program Files\Docker\Docker\Resources\bin'
ls -al "\cygdrive\c\Program Files\Docker\Docker\Resources\bin"
ls -al \cygdrive\c\Program\ Files\Docker\Docker\Resources\bin
which docker
whereis docker
echo %DOCKER_HOST%

cd github/jib

cat %USERPROFILE%\.docker\config.json
echo { "experimental": "enabled" } > %USERPROFILE%\.docker\config.json
cat %USERPROFILE%\.docker\config.json

REM docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
set GOOGLE_APPLICATION_CREDENTIALS=%KOKORO_KEYSTORE_DIR%\72743_jib_integration_testing_key
docker-credential-gcr configure-docker

REM Stops any left-over containers.
FOR /f "tokens=*" %%i IN ('docker ps -aq') DO docker rm -vf %%i

REM Sets the integration testing project.
set JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

pushd %USERPROFILE%
dir /a .docker
cat .docker\config.json
echo { "auths": {}, "credHelpers": { "appengine.gcr.io": "gcr", "asia.gcr.io": "gcr", "eu.gcr.io": "gcr", "gcr.io": "gcr", "gcr.kubernetes.io": "gcr", "us.gcr.io": "gcr" }, "experimental": "enabled" } > %USERPROFILE%\.docker\config.json
cat .docker\config.json
cat .docker\daemon.json
echo {"registry-mirrors":[], "insecure-registries":[], "debug":true, "experimental":true} > .docker\daemon.json
cat .docker\daemon.json
popd

cat C:\ProgramData\Docker\config\daemon.json
echo {"registry-mirrors":[], "insecure-registries":[], "experimental":true, "hosts":["tcp://0.0.0.0:2375","npipe://"]} > C:\ProgramData\Docker\config\daemon.json
cat C:\ProgramData\Docker\config\daemon.json

cat %USERPROFILE%\.docker\daemon.json
dir C:\ProgramData
dir C:\ProgramData\Docker
dir C:\ProgramData\Docker\config
cat C:\ProgramData\Docker\config\daemon.json

restart-service docker

sc queryex com.docker.service
sc query com.docker.service
sc stop com.docker.service

:CheckDockerStopped
sc query com.docker.service
sc query com.docker.service | FINDSTR "STOPPED"
IF ERRORLEVEL 1 (
  timeout 1
  GOTO CheckDockerStopped
)
REM for /F "tokens=3 delims=: " %%H in ('sc query "MyServiceName" ^| findstr "        STATE"') do (
REM  if /I "%%H" NEQ "RUNNING" (
REM   REM Put your code you want to execute here
REM   REM For example, the following line
REM   net start "MyServiceName"
REM  )
REM)

docker version

docker pull microsoft/nanoserver
docker pull registry:2
docker images
docker pull --platform linux registry:2

sc queryex com.docker.service
sc start com.docker.service
sc queryex com.docker.service

:CheckDockerRunning
sc query com.docker.service
sc query com.docker.service | FINDSTR "RUNNING"
IF ERRORLEVEL 1 {
  timeout 1
  GOTO CheckDockerRunning
)

restart-service docker

docker version

docker images
docker pull registry:2
docker pull --platform linux registry:2

cd jib-core && call gradlew.bat clean build integrationTest --info --stacktrace && ^
cd ../jib-plugins-common && call gradlew.bat clean build --info --stacktrace && ^
cd ../jib-maven-plugin && call mvnw.cmd clean install -P integration-tests -B -U -X && ^
cd ../jib-gradle-plugin && call gradlew.bat clean build integrationTest --info --stacktrace

exit /b %ERRORLEVEL%
