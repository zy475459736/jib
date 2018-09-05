@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/jib

REM Stops any left-over containers.
FOR /f "tokens=*" %%i IN ('docker ps -aq') DO docker rm -vf %%i

REM Sets the integration testing project.
set JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

set CLOUDSDK_CORE_DISABLE_USAGE_REPORTING=true
call gcloud.cmd components install docker-credential-gcr
@echo on

set GOOGLE_APPLICATION_CREDENTIALS=%KOKORO_KEYSTORE_DIR%\72743_jib_integration_testing_key
docker-credential-gcr configure-docker

cd jib-core && call gradlew.bat clean build integrationTest --info --stacktrace && ^
cd ../jib-plugins-common && call gradlew.bat clean build --info --stacktrace && ^
cd ../jib-maven-plugin && call mvnw.cmd clean install -P integration-tests -B -U -X && ^
cd ../jib-gradle-plugin && call gradlew.bat clean build integrationTest --info --stacktrace

exit /b %ERRORLEVEL%
