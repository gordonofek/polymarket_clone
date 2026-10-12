@echo off
rem Starts the Guess Market web client and opens it in the browser (http://localhost:3000).
rem Needs Node.js. The Guess Market server (guess-market.war on Tomcat, port 8080) must be running.
cd /d "%~dp0"
node server.js
pause
