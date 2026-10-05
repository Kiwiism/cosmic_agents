@echo off
REM Projects TypeSafe (Jev) spend for an expected bot population. Run from the server directory.
REM Example: tools	ypesafe-cost.bat --bots 20 --commands-per-bot-hour 6 --hours-online 12 --live
REM Options: --bots N --commands-per-bot-hour X --hours-online X --miss-rate 0-1 --pq-sessions N --pq-messages N
REM          --offers N --free-form-rate 0-1 --new-characters N --reports N --director-queries N --live
REM --live sends one sample request per judgment kind (needs TYPESAFE_API_KEY); otherwise tokens are estimated offline.
if not exist target\Cosmic.jar (
    echo target\Cosmic.jar not found - run mvnw.cmd package -DskipTests first.
    exit /b 1
)
java -cp target\Cosmic.jar server.agents.integration.typesafe.cost.JevCostProjector %*
