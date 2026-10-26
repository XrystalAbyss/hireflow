# HireFlow Windows launcher troubleshooting

Run `Check-HireFlow.cmd` after a LIVE start; the final READY line is the gate. Logs and state are under gitignored `.run/logs/`, `.run/evidence/`, and `.run/state.json`.

| Symptom | Scoped action |
| --- | --- |
| Docker engine unavailable | Open Docker Desktop normally and rerun `Launch-HireFlow.cmd`. The launcher may start the installed desktop app and wait up to 60 seconds. If WSL reports a missing `ext4.vhdx`, stop; do not unregister, reset, reinstall, move virtual disks, or elevate without separate authorization. |
| Container identity or database guard fails | Leave the container/database untouched. Inspect `.run/logs/docker-check.log` and `.run/logs/database-check.log`. The launcher requires `postgres:16`, the exact compose project/volume, loopback port 55432, restricted role, and isolation sentinel. |
| Port 8081 or 5174 occupied | Run `Stop-HireFlow.cmd` only for this launcher's owned processes. If a foreign process holds the port, identify its owner; the launcher refuses to kill or adopt it. |
| Browser does not open | Open `http://127.0.0.1:5174/auth/login` manually after the READY line. |
| Login fails | Confirm LIVE mode and run `Check-HireFlow.cmd`. The three standard demo account credentials live in `.run/demo.local.json`; `student` is the separate simple demo login. |
| Need another fresh live journey | Run `scripts/demo/Prepare-HireFlowJourney.ps1 -Round <new short uppercase ID>` while LIVE is healthy. It adds fictional records without deleting or changing previous applications. |
| Need to stop | Double-click `Stop-HireFlow.cmd`; it preserves the Docker volume and records. |

The host PostgreSQL service on port 5432 is never an acceptable fallback. `Check-HireFlow.cmd` includes authentication requests and can update audit timestamps, even though it does not reset the demo scenario.
