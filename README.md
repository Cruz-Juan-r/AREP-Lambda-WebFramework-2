# Lambda Web Framework: a maintainable, sequential application server

A small Java web framework built on raw sockets. Developers register REST services as **lambda functions** with `get()`, declare where static files live with `staticfiles()`, and start the server with `start()`. The same jar runs locally and in the cloud, configured only through environment variables, and can be stopped gracefully through a development-only `/shutdown` route.

- **Author:** Juan Esteban Cruz
- **Course:** AREP, Escuela Colombiana de Ingeniería Julio Garavito
- **Cloud platform:** AWS (Amazon EC2 + Docker, AWS Academy Learner Lab)
- **Public deployment URL:** <http://ec2-18-234-76-168.compute-1.amazonaws.com> (also reachable at `http://18.234.76.168`)

```java
import static co.edu.escuelaing.webframework.WebFramework.*;

public class Application {
    public static void main(String[] args) throws Exception {
        staticfiles("/webroot");

        get("/hello", (req, resp) -> "Hello " + req.getValueOrDefault("name", "world"));
        get("/pi", (req, resp) -> String.valueOf(Math.PI));

        start(); // reads PORT, defaults to 8080
    }
}
```

---

## Table of contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Architecture metaphor: a restaurant with one waiter](#architecture-metaphor-a-restaurant-with-one-waiter)
4. [Project structure](#project-structure)
5. [Build and run locally](#build-and-run-locally)
6. [Environment variables](#environment-variables)
7. [Endpoints and example URLs](#endpoints-and-example-urls)
8. [Cloud deployment (AWS EC2 + Docker)](#cloud-deployment-aws-ec2--docker)
9. [Evidence](#evidence)
10. [Tests](#tests)
11. [Why this architecture is maintainable](#why-this-architecture-is-maintainable)

---

## Features

| Requirement | Implementation |
|---|---|
| Static HTML, CSS, JS and images | `StaticFileService` reads raw bytes from the classpath (`/webroot`) or from a disk folder, with MIME types and path-traversal protection |
| Lambda GET services | `get(path, (req, resp) -> ...)` registers a `RouteHandler` in the `Router` |
| Query-string extraction | `req.getValue("name")`, `getValueOrDefault`, `getValues` for repeated keys; URL-decoded (UTF-8, `+`, `%20`) |
| Route resolution | Lambda route → static file → `404 Not Found` |
| Error handling | `400` malformed/incomplete requests, `404` unknown resources, `405` non-GET methods, `500` if a lambda throws; the server never dies because of one request |
| Externalized configuration | `PORT`, `APP_ENV`, `GREETING_PREFIX`, `STATIC_FILES_PATH` |
| Cloud-ready binding | Binds to `0.0.0.0`, not only `localhost` |
| Graceful sequential shutdown | `stop()` marks the server as not running; the current response is sent, the connection closed, the loop exits and the `ServerSocket` is closed |
| Sequential | One connection at a time. No threads, pools or async execution on the server |

## Architecture

```mermaid
flowchart TD
    APP["Application<br/>registers routes and configuration"] --> API
    API["WebFramework (public API)<br/>staticfiles() get() start() stop()"] --> SERVER
    API --> ROUTER
    SERVER["HttpServer<br/>sequential accept loop on 0.0.0.0:PORT"] --> PARSER["HttpRequestParser<br/>bytes → Request"]
    SERVER -->|"1. lambda?"| ROUTER["Router<br/>GET + path → RouteHandler"]
    ROUTER --> H1["/hello"]
    ROUTER --> H2["/pi"]
    ROUTER --> H3["/api/sum, /api/info, /shutdown"]
    SERVER -->|"2. file?"| STATIC["StaticFileService<br/>classpath or disk"]
    SERVER -->|"3. neither"| NF["404 Not Found"]
    SERVER --> RESP["HttpResponse<br/>status line, headers, bytes"]
```

Request flow for every connection:

```
accept() ─► parse request line + headers ─► GET?  ── no ─► 405
                     │ malformed                 │ yes
                     ▼                           ▼
                    400                 Router has a lambda? ── yes ─► run lambda ─► 200 / custom status (500 if it throws)
                                                 │ no
                                                 ▼
                                        StaticFileService has the file? ── yes ─► 200 + bytes + MIME type
                                                 │ no
                                                 ▼
                                                404
write response ─► close connection ─► running? ─► accept next (or leave loop and close ServerSocket)
```

### Component responsibilities

| Component | Responsibility | Knows about sockets? |
|---|---|---|
| `Application` (`co.edu.escuelaing.app`) | Example app: reads `AppConfig`, registers static files and lambdas | No |
| `AppConfig` | Reads non-sensitive settings from environment variables with local defaults | No |
| `WebFramework` | Public facade: `staticfiles()`, `get()`, `start()`, `start(port)`, `stop()`, `resolvePort()` | No |
| `Router` / `Route` | Stores `method + path → RouteHandler`; normalizes trailing slashes | No |
| `RouteHandler` | Functional interface implemented by the lambdas: `(Request, Response) -> String` | No |
| `Request` | Immutable method, path, query parameters and headers | No |
| `Response` | Lets a lambda set status, content type and headers | No |
| `HttpRequestParser` | Converts raw bytes into a `Request`; validates the request line, decodes the query string | Reads a stream |
| `StaticFileService` / `MimeTypes` / `StaticResource` | Finds files safely (no `..` escapes, no directories) and returns bytes + MIME type | No |
| `HttpResponse` | Serializes status line, headers and body bytes | Writes a stream |
| `HttpServer` | Sequential accept loop, dispatch order, error isolation, graceful stop | **Yes, the only one** |

## Architecture metaphor: a restaurant with one waiter

Picture a small restaurant that has **exactly one waiter**. That single waiter is what makes the server *sequential*.

| Restaurant | Framework component | Relationship |
|---|---|---|
| The front door and the waiter who seats one table at a time | `HttpServer` | Accepts one connection, takes the order, delivers the dish, says goodbye, and only then opens the door for the next customer |
| The waiter's notepad, where the order is written in a standard format | `HttpRequestParser` → `Request` | Turns what the customer said (raw bytes) into a clear ticket: dish, table, special instructions (`?name=Pedro`). If the customer mumbles nonsense, the waiter politely answers "I didn't understand" (`400`) instead of fainting |
| The ticket rail that says which cook prepares which dish | `Router` | Looks up the dish name (`GET /hello`) and hands the ticket to the right station. The waiter never needs to know how a dish is cooked |
| The cooks at their stations | Lambda handlers | Each cook knows one recipe (`/hello`, `/pi`, `/api/sum`). Hiring a new cook means adding one line to the rail, never rebuilding the dining room |
| The ready-made counter: bread, drinks, desserts already packed | `StaticFileService` | If no cook makes that item, the waiter checks the counter (HTML, CSS, JS, images). If it isn't there either: "Sorry, that's not on the menu" (`404`) |
| The plate and presentation | `HttpResponse` | Every dish leaves the kitchen on a standard plate: status line, headers, `Content-Length`, body |
| The franchise settings posted in the back office | Environment variables | Which door number to use (`PORT`), how to greet (`GREETING_PREFIX`), whether this is the training kitchen or the real restaurant (`APP_ENV`), where the counter is (`STATIC_FILES_PATH`). The same recipes work in any branch |
| Closing time | Graceful `/shutdown` | The manager says "we're closing". The waiter finishes serving the table in front of them, walks them out, then locks the door. In the real restaurant (`APP_ENV=production`) customers can't shout "close!" at all: that button only exists in the training kitchen |

The key idea is the separation between the **stable** parts (door, notepad, ticket rail, plates) and the **changing** part (the recipes). The menu can grow indefinitely without touching how the waiter works.

## Project structure

```
.
├── pom.xml
├── Dockerfile / .dockerignore
├── .env.example / .gitignore
├── scripts/
│   ├── verify.sh          # smoke test (bash) against any base URL
│   └── verify.ps1         # same smoke test for Windows PowerShell
├── docs/evidence/         # local and cloud evidence referenced below
└── src/
    ├── main/java/co/edu/escuelaing/
    │   ├── webframework/  # the framework (reusable)
    │   │   ├── WebFramework.java      HttpServer.java      Router.java
    │   │   ├── Route.java             RouteHandler.java    Request.java
    │   │   ├── Response.java          HttpRequestParser.java
    │   │   ├── HttpResponse.java      StaticFileService.java
    │   │   ├── StaticResource.java    MimeTypes.java       BadRequestException.java
    │   └── app/           # the example application
    │       ├── Application.java       AppConfig.java
    ├── main/resources/webroot/
    │   ├── index.html  app.js  styles.css  images/logo.png
    └── test/java/co/edu/escuelaing/...  # 47 JUnit 5 tests
```

## Build and run locally

**Requirements:** Java 17+ and Maven 3.8+.

```bash
git clone https://github.com/Cruz-Juan-r/AREP-Lambda-WebFramework.git
cd AREP-Lambda-WebFramework
mvn clean package          # compiles and runs the 47 tests
java -jar target/lambda-webframework.jar
```

Open <http://localhost:8080>.

With custom configuration:

```bash
# Linux / macOS / Git Bash
PORT=9090 APP_ENV=development GREETING_PREFIX=Hola java -jar target/lambda-webframework.jar
```

```powershell
# Windows PowerShell
$env:PORT="9090"; $env:APP_ENV="development"; $env:GREETING_PREFIX="Hola"
java -jar target/lambda-webframework.jar
```

Stop it gracefully (development only): <http://localhost:8080/shutdown>.

Run the smoke test against the running server:

```bash
./scripts/verify.sh                       # http://localhost:8080, development
```

```powershell
.\scripts\verify.ps1
```

With Docker (optional):

```bash
docker build -t lambda-webframework .
docker run --rm -p 8080:8080 -e APP_ENV=development lambda-webframework
```

## Environment variables

| Variable | Purpose | Local default | Cloud value |
|---|---|---|---|
| `PORT` | TCP port the server listens on | `8080` | `8080` (container), published on port 80 |
| `APP_ENV` | Execution environment. `/shutdown` is only registered when it is `development` | `development` | `production` |
| `GREETING_PREFIX` | Prefix used by `/hello` | `Hello` | `Hola` |
| `STATIC_FILES_PATH` | Optional folder on disk for static files. If it is not an existing directory, it is read as a classpath folder | `/webroot` (classpath) | not set |

No secrets, tokens or keys are used or committed. `.env` files are ignored by Git; `.env.example` documents the variables. `GET /api/info` returns the non-sensitive configuration currently in effect, which is how the evidence below proves the variables are applied.

## Endpoints and example URLs

| Type | URL | Result |
|---|---|---|
| Static HTML | `/` or `/index.html` | Demo page |
| Static CSS | `/styles.css` | Stylesheet |
| Static JS | `/app.js` | Script that calls the services with `fetch()` |
| Static image | `/images/logo.png` | PNG served as binary |
| Lambda | `/hello?name=Pedro` | `Hello Pedro` (prefix from `GREETING_PREFIX`) |
| Lambda | `/hello` | `Hello world` (missing parameter handled) |
| Lambda | `/pi` | `3.141592653589793` |
| Lambda | `/api/sum?a=2&b=3.5` | `{"a":2.0,"b":3.5,"sum":5.5}` (two parameters) |
| Lambda | `/api/sum?a=2` | `400` with a JSON error message |
| Lambda | `/api/info` | Non-sensitive runtime configuration as JSON |
| Lambda (dev only) | `/shutdown` | Stops the server gracefully; `404` in production |
| Error | `/unknown` | `404 Not Found` |

Cloud versions: prefix any path with `http://ec2-18-234-76-168.compute-1.amazonaws.com`, e.g. <http://ec2-18-234-76-168.compute-1.amazonaws.com/hello?name=Pedro>.

## Cloud deployment (AWS EC2 + Docker)

The deployment builds the **same source code** inside a Docker image (multi-stage `Dockerfile`: Maven builds and tests, then only the jar is copied into a JRE image). These steps work in AWS Academy Learner Lab.

### How the running deployment was created

The live instance above (`i-0fee51cae5c0ed33c`, `t3.micro`, Amazon Linux 2023, `us-east-1`) was launched with the AWS CLI, with a security group allowing HTTP (80) from anywhere and SSH (22) from the deployer's IP only, and an **EC2 user-data script** that runs once at boot as root:

```bash
#!/bin/bash
dnf install -y docker git
systemctl enable --now docker

cd /home/ec2-user
git clone https://github.com/Cruz-Juan-r/AREP-Lambda-WebFramework.git
cd AREP-Lambda-WebFramework
docker build -t lambda-webframework .

docker run -d --name lambda-web --restart unless-stopped \
  -p 80:8080 \
  -e PORT=8080 \
  -e APP_ENV=production \
  -e GREETING_PREFIX=Hola \
  lambda-webframework
```

This is the same build-and-run sequence described step by step below; user-data just runs it unattended during instance boot instead of over an interactive SSH session, cloning the **public** GitHub repository directly (no credentials needed).

### Reproducing it manually

1. **Launch an EC2 instance**: Amazon Linux 2023, `t2.micro` or `t3.micro`, with a key pair.
2. **Security group inbound rules**: SSH (22) from your IP, HTTP (80) from `0.0.0.0/0`.
3. **Connect and install Docker and Git:**
   ```bash
   ssh -i "your-key.pem" ec2-user@<EC2_PUBLIC_DNS>
   sudo dnf install -y docker git
   sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user && newgrp docker
   ```
4. **Build the image from the repository:**
   ```bash
   git clone https://github.com/Cruz-Juan-r/AREP-Lambda-WebFramework.git
   cd AREP-Lambda-WebFramework
   docker build -t lambda-webframework .
   ```
5. **Run it with production configuration:**
   ```bash
   docker run -d --name lambda-web --restart unless-stopped \
     -p 80:8080 \
     -e PORT=8080 \
     -e APP_ENV=production \
     -e GREETING_PREFIX=Hola \
     lambda-webframework
   ```
6. **Check it:**
   ```bash
   docker logs lambda-web            # shows "/shutdown is disabled because APP_ENV=production"
   docker inspect lambda-web --format '{{range .Config.Env}}{{println .}}{{end}}'
   ./scripts/verify.sh http://localhost production
   ```
7. Open `http://<EC2_PUBLIC_DNS>` from your browser.

**Updating the deployment:** `git pull && docker build -t lambda-webframework . && docker rm -f lambda-web` and repeat step 5.

> Alternative platforms that inject `PORT` automatically (Render, Railway, Heroku-style) also work with the same `Dockerfile`: set `APP_ENV=production` and `GREETING_PREFIX` in the service settings and do not set `PORT` yourself.

> AWS Academy Learner Lab credentials are temporary (they expire when the lab session ends), so the instance above will stop being reachable once the lab is stopped/reset. Relaunching it takes the same `aws ec2 run-instances` call with this user-data script against a fresh Learner Lab session.

## Evidence

### Local (development)

All files in [`docs/evidence/`](docs/evidence/) were produced by running the packaged jar.

- Demo page served by the framework, after using every button:

  ![Local demo page](docs/evidence/local-page.png)

- [`local-endpoints.txt`](docs/evidence/local-endpoints.txt): full `curl -i` output for `/hello?name=Pedro`, `/hello`, `/hello?name=Pedro&language=en`, `/pi`, `/api/sum`, `/api/info`, the static HTML/CSS/JS/PNG files, `404` for `/unknown`, `400` for a malformed request and `405` for `POST`.
- [`local-server-log.txt`](docs/evidence/local-server-log.txt): server log showing each request with its status.
- [`verify-development.txt`](docs/evidence/verify-development.txt): smoke test, 12/12 passing.

**`/shutdown` works locally in development** ([`local-shutdown.txt`](docs/evidence/local-shutdown.txt)):

```
$ curl -i http://localhost:8080/shutdown
HTTP/1.1 200 OK
Content-Type: text/plain; charset=UTF-8
Content-Length: 37

Server will stop after this response.

$ curl -i http://localhost:8080/pi   # after shutdown
curl: (7) Failed to connect to localhost port 8080: Connection refused
```

Server log for the same run:

```
[http] Shutdown requested: finishing the current request before closing.
[http] 200 GET /shutdown
[http] Server stopped gracefully.
```

**Production mode locally** ([`local-production-mode.txt`](docs/evidence/local-production-mode.txt), `PORT=9090 APP_ENV=production GREETING_PREFIX=Hola`): `/hello?name=Pedro` returns `Hola Pedro`, `/api/info` reports `"appEnv":"production","shutdownEnabled":false`, and `/shutdown` returns `404` while the server keeps running.

### Cloud (production on AWS)

Public URL: <http://ec2-18-234-76-168.compute-1.amazonaws.com> (`http://18.234.76.168`)

- Deployed page, served by the EC2 instance:

  ![Cloud demo page](docs/evidence/cloud-page.jpg)

- Live `fetch()` calls made from the browser against the public URL — `/api/info` (environment variables, no secrets), `/pi`, `/api/sum?a=2&b=3.5` (two query parameters), `/hello?name=` (missing parameter handled), and `/does-not-exist` (`404`) — each with its real status code and timing, taken from the deployed page's own request log:

  ![Live requests against the cloud deployment](docs/evidence/cloud-requests.jpg)

- Static resource `/images/logo.png` opened directly in the browser:

  ![Cloud static image](docs/evidence/cloud-static-logo.jpg)

- `/shutdown` is **not** available in production — `404 Not Found`:

  ![Cloud shutdown disabled](docs/evidence/cloud-shutdown-404.jpg)

- [`cloud-endpoints.txt`](docs/evidence/cloud-endpoints.txt): `curl -i` output for `/hello?name=Pedro`, `/hello`, `/pi`, `/api/sum?a=2&b=3.5`, `/api/sum?a=2` (`400`), `/api/info`, the static HTML/CSS/JS/PNG files, `/shutdown` (`404` in production) and `/unknown` (`404`), all run against the public URL.
- [`cloud-verify.txt`](docs/evidence/cloud-verify.txt): `./scripts/verify.sh http://18.234.76.168 production`, 13/13 passing.

Environment variables in effect on the instance, with no secrets (from `/api/info`, part of `cloud-endpoints.txt` and visible in the request-log screenshot above):

```json
{"appEnv":"production","greetingPrefix":"Hola","staticFiles":"/webroot","shutdownEnabled":false,"javaVersion":"17.0.20"}
```

## Tests

### Automated (JUnit 5, `mvn test`): 47 tests, all passing

| Test class | What it covers |
|---|---|
| `HttpRequestParserTest` (11) | Method/path/version, single and multiple query params, missing params return `null`, UTF-8 / `+` / `%26` decoding, repeated and valueless keys, bare `\n`, empty connection, malformed request lines, bad percent-encoding, bad headers, oversized lines |
| `RouterTest` (5) | Lookup, unknown path/method, trailing slash, re-registration, invalid registrations |
| `StaticFileServiceTest` (7) | HTML/CSS/JS MIME types, PNG served byte-for-byte, `/` → `index.html`, missing files and directories, path traversal blocked, disk directory mode, MIME fallback |
| `WebFrameworkTest` (3) | `PORT` default, parsing, invalid values |
| `HttpServerIntegrationTest` (12) | Real server on a random port driven through sockets: lambdas with and without params, custom status/type/headers, static HTML/CSS/JS/PNG, `404`, `400` then server still alive, empty connection, lambda exception → `500` then server still alive, `405`, graceful shutdown (response delivered, thread ends, port closed) |
| `AppConfigTest` (4) | Defaults, reading env values, `/shutdown` disabled outside development, blank values |
| `ApplicationHandlersTest` (5) | The example lambdas tested in isolation, without a server |

### Manual requests

| Request | Expected | Result |
|---|---|---|
| `GET /hello?name=Pedro` | 200 `Hello Pedro` | ✅ |
| `GET /hello` | 200 `Hello world` | ✅ |
| `GET /pi` | 200 `3.141592653589793` | ✅ |
| `GET /api/sum?a=2&b=3.5` | 200 JSON with `sum` 5.5 | ✅ |
| `GET /index.html`, `/app.js`, `/styles.css` | 200 with correct `Content-Type` | ✅ |
| `GET /images/logo.png` | 200 `image/png`, 4292 bytes | ✅ |
| `GET /unknown` | 404 `404 Not Found` | ✅ |
| `garbage` request line | 400, server keeps running | ✅ |
| `POST /hello` | 405 with `Allow: GET` | ✅ |
| `GET /shutdown` (development) | 200, then connection refused | ✅ |
| `GET /shutdown` (production) | 404, server keeps running | ✅ |

## Why this architecture is maintainable

| Principle | How it shows up here |
|---|---|
| Separation of concerns | `HttpServer` handles connections; `Router` decides who answers; lambdas implement behavior; `StaticFileService` handles files |
| Modularity | Parsing, routing, static files, response serialization and the app live in separate classes and packages (`webframework` vs `app`) |
| Low coupling | Adding `/api/sum` required one `get(...)` call. `HttpServer` was not modified |
| High cohesion | Each class has one reason to change (e.g. new MIME types only touch `MimeTypes`) |
| Abstraction | Application code never sees a `Socket`; it only uses `get()`, `staticfiles()`, `start()`, `stop()` |
| Externalized configuration | Port, environment, greeting and static folder come from environment variables with safe defaults |
| Extensibility | New services are new lambdas; handlers can set status, content type and headers through `Response` |
| Testability | Parser, router, static files and each lambda are unit-tested without a network; the server is tested end-to-end on a random port |
| Operational maintainability | The same jar/image runs locally and on AWS; production disables `/shutdown` by configuration, not by code changes |
| Robustness | Per-connection error isolation (`400`/`404`/`405`/`500`) and a 5-second read timeout keep one bad client from stopping or freezing the sequential loop |
