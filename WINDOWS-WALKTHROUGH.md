# FixFlow from ZIP to GitHub on Windows

## 1. Understand what you are building

Imagine a pipe leaks in someone's apartment. The resident needs confirmation that the request was received, and the manager needs to know which repairs to handle first. FixFlow gives them a ticket and a timeline.

The two Spring Boot apps can run independently. The Request Service saves the repair details. The Notification Service reads events from RabbitMQ and creates updates for the resident. RabbitMQ holds messages while the notification app is stopped.

## 2. Open your project

1. Download the project ZIP. Right-click it in File Explorer and choose **Extract All**.
2. Open VS Code. Choose **File → Open Folder** and select the inner `fixflow` folder containing `compose.yaml`, `README.md`, and both service folders.
3. Open **Terminal → New Terminal** in VS Code. Use **PowerShell**. Run:

   ```powershell
   Get-Location
   Get-ChildItem
   git --version
   docker compose version
   ```

If Git or Docker is missing, install [Git for Windows](https://git-scm.com/install/windows) or [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/), then reopen VS Code. Keep Docker Desktop running. The Docker build supplies Java and Maven, so you can run the app without installing them on Windows.

## 3. Run the app

```powershell
docker compose up --build -d
docker compose ps
```

`up` starts the project, `--build` builds the two Java apps into images, and `-d` leaves the containers running in the background. First startup can take several minutes. Open [localhost:8080](http://localhost:8080).

If the page does not open, run `docker compose logs --tail=80 request-service notification-service`. Wait for Spring Boot to report that each app has started. If Docker reports a connection error, check that Docker Desktop's engine is running. If a port is already allocated, stop the other local app using that port and rerun the command.

## 4. Use the browser before reading the code

Submit a test repair, choose a technician, click **Start repair**, and then click **Resolve request**. After a few seconds, click **Refresh updates**. You should see four timeline entries and four notifications for that ticket.

For the outage demo, repeat those actions with a fresh ticket after running:

```powershell
docker compose stop notification-service
```

Show that the timeline still works. Then run:

```powershell
docker compose start notification-service
```

Wait for startup and click **Refresh updates**. The pending updates should appear. For a second exercise, run `docker compose stop rabbitmq`, submit a fresh ticket, then run `docker compose start rabbitmq`. Wait for the broker and consumer to reconnect and verify that the notification arrives. In this case the Request Service can retry from the outbox because the event was already saved in PostgreSQL.

### Optional duplicate-message check

Run this in PowerShell after the notification for your ticket has appeared. Copy the ticket ID from the browser details. This replays an existing event by asking the publisher to send it again:

```powershell
$ticketId = [guid](Read-Host "Paste the ticket ID")
$notificationUrl = "http://localhost:8081/api/notifications/$ticketId"
$before = @(Invoke-RestMethod $notificationUrl)
$eventId = [guid]$before[0].eventId
docker compose exec -T postgres psql -U fixflow -d fixflow -c "UPDATE ticket_events SET published = false WHERE event_id = '$eventId';"
Start-Sleep -Seconds 5
$after = @(Invoke-RestMethod $notificationUrl)
"Before: $($before.Count), After: $($after.Count)"
```

The SQL command should report `UPDATE 1`. The notification count should stay the same. To verify the replay was confirmed, query `SELECT published FROM ticket_events WHERE event_id = '$eventId';` using the same `docker compose exec` command. The value should return to `t` (true).

## 5. Read the code in this order

| File | What to look for |
| --- | --- |
| `compose.yaml` | The four containers, ports, and startup dependencies. |
| `TicketController.java` | How an HTTP request becomes a saved ticket and a timeline event. |
| `EventPublisher.java` | How an unsent event reaches RabbitMQ and when it is marked published. |
| `Notifications.java` | How the consumer inserts a notification and handles a repeated event ID. |
| `app.js` | How the page calls the APIs and shows the notification outage. |

Try changing the sample priority rule or adding a category after you understand the workflow. Keep the Java enum and browser options in sync. Rebuild with `docker compose up --build -d`.

## 6. Publish on GitHub

1. Sign in to [GitHub](https://github.com/new). Create a repository named **fixflow** and choose **Public** so someone opening your resume can see it. Leave the GitHub README, .gitignore, and license initialization options unchecked. Your local folder already contains a README and .gitignore.
2. Copy the repository's HTTPS URL from the Quick Setup screen.
3. In your existing VS Code PowerShell terminal, run the commands below. Replace the placeholder remote URL with the one you copied.

   ```powershell
   git init -b main
   git add .
   git status
   git commit -m "Build FixFlow maintenance desk"
   git remote add origin https://github.com/YOUR-USERNAME/fixflow.git
   git push -u origin main
   ```

`git add` stages the source files. `git commit` saves that version locally. `git push` sends it to GitHub. If Git asks for a name and email, set them for this repo with `git config user.name "Your Name"` and `git config user.email "YOUR-EMAIL"`. You can copy your GitHub-provided noreply address from **Settings → Emails**. Then rerun the commit and continue. If Git Credential Manager opens a browser sign-in, follow the prompts.

Refresh your repo in GitHub. Look for both service folders, `compose.yaml`, and the README below the file list. Open the repo URL in a private browser window to check public access. For your next update, use `git add .`, `git commit -m "Describe your change"`, and `git push`.

## 7. Make the repo easy for a recruiter to review

- Capture a screenshot of the resolved ticket after running the app. Save it as `docs/fixflow-demo.png` and add `![FixFlow maintenance desk](docs/fixflow-demo.png)` under the first paragraph of the README. A 30–60 second clip of the outage and recovery makes a good companion.
- On your GitHub profile's **Overview** tab, pin **fixflow** alongside the Enterprise AI Knowledge Assistant. On the resume, link the project name directly to its repository. GitHub shows the code and README. The live app runs on your computer through Docker.
- Use the resume bullets below once you can run and explain the workflow yourself. Keep the outage claim after you have reproduced it.

**FixFlow | Java, Spring Boot, PostgreSQL, RabbitMQ, Docker Compose**

- Built REST APIs for reporting apartment maintenance issues, assigning technicians, and tracking repair status through a browser-based timeline.
- Saved ticket changes and notification events in one PostgreSQL transaction, then published updates asynchronously through RabbitMQ. Verified that queued notifications arrived after a service restart and that replayed events produced no duplicate notifications.

## 8. Stop for the day

```powershell
docker compose down
```

Your tickets stay in local Docker volumes. Use `docker compose up -d` to bring the existing setup back later.
