# Local development setup

The local data flow is:

`MQTT publisher -> Mosquitto -> Spring Boot -> MySQL -> Discord`

## 1. Initialize MySQL once

MySQL 8.0 must be running. From PowerShell in the project directory, run:

```powershell
Get-Content .\scripts\init-local-db.sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p
```

Enter the local MySQL root password when prompted. This creates a database and a localhost-only development user. The credentials are intentionally local development defaults and must not be used on a deployed server.

## 2. Run the application

Start Spring Boot and paste the Discord webhook into the masked prompt. The URL is kept only in the current process and is not written to a file or command history:

```powershell
.\scripts\run-local.ps1 -PromptForWebhook
```

If the environment variable is omitted, the application still starts and logs that Discord delivery was skipped.

The script builds an executable JAR and runs it directly, avoiding Gradle classpath
encoding issues in Windows paths containing Korean characters or spaces.

Open the dashboard at `http://localhost:8081`. On a phone connected to the same
network, open `http://<PC-IP>:8081` instead. Windows Firewall may require allowing
Java on the private network. There is no login in this prototype; use a trusted
local network rather than exposing port 8081 to the Internet.

The **데모 체험** button uses illustrative data and browser-local records. The
**실시간** button uses the same-origin Spring Boot APIs and MySQL. A connection
failure never silently switches to demo data.

## 3. Publish test vibration messages

In another PowerShell window:

```powershell
& 'C:\Program Files\mosquitto\mosquitto_pub.exe' -h 127.0.0.1 -t laundry/vibration/1 -m 1 -q 1
& 'C:\Program Files\mosquitto\mosquitto_pub.exe' -h 127.0.0.1 -t laundry/vibration/1 -m 0 -q 1
```

The local profile uses a 10-second completion threshold. Publish `0` again after at least 10 seconds to trigger completion. The default for non-local environments remains 180 seconds.

## 4. Collect and export vibration telemetry

The NodeMCU publishes one-second aggregate windows to `laundry/telemetry/1` as:

```text
sample_count,vibration_count,window_ms
```

Spring Boot stores each window in the `vibration_telemetry` MySQL table. While the application is running, download all collected data as CSV from:

```text
http://localhost:8081/api/telemetry/export.csv
```

## Security

- Never commit a Discord webhook URL, Wi-Fi password, or production database password.
- The local database user is restricted to this computer.
- Mosquitto currently listens on loopback only. LAN access for Arduino should be configured separately with authentication before opening port 1883.
