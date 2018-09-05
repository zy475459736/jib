@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/jib

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
cat .docker\daemon.json
echo "{\"registry-mirrors\":[],\"insecure-registries\":[], \"debug\":true, \"experimental\": true}" > .docker\daemon.json
cat .docker\daemon.json
popd

cat %USERPROFILE%\.docker\daemon.json

Net stop com.docker.service
Net start com.docker.service

docker version

cd jib-core && call gradlew.bat clean build integrationTest --info --stacktrace && ^
cd ../jib-plugins-common && call gradlew.bat clean build --info --stacktrace && ^
cd ../jib-maven-plugin && call mvnw.cmd clean install -P integration-tests -B -U -X && ^
cd ../jib-gradle-plugin && call gradlew.bat clean build integrationTest --info --stacktrace

exit /b %ERRORLEVEL%
