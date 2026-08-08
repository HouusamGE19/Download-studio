# Download Studio

A Docker-first Angular + Spring Boot interface for `download_single.sh`.

The downloader script is included in the project root, making the directory self-contained and portable.

## Quick start with Docker

Docker with the Compose plugin is the only requirement for normal use.

```bash
docker compose up --build -d
```

Open <http://127.0.0.1:8080>. Downloads are saved automatically in the
`downloads/` directory beside `docker-compose.yml`; the application does not
browse the server or client filesystem.

## Development

Source development requires Java 17+, Node.js 24+, `yt-dlp`, and `ffmpeg`.
Run `./mvnw spring-boot:run`, then run `npm install && npm start` inside `frontend/`. The development UI at <http://127.0.0.1:4200> proxies API calls to Spring Boot.

Configuration environment variables:

- `PORT` changes the Spring port (default `8080`).
- `DOWNLOAD_SCRIPT` points to a different downloader script.
- `DOWNLOAD_HISTORY` changes the history JSON location.
- `DOWNLOAD_DIRECTORY` changes the native-app output directory.

Recent activity is stored at `~/.download-studio/history.json`.

## Build after code changes

Build the Angular frontend first, then package the Spring Boot backend so the
generated JAR contains the latest frontend files:

```bash
cd frontend
npm ci
npm run build
cd ..

./mvnw clean package
```

The executable JAR is written to `target/download-studio-1.0.0.jar`. Run it
from the project root:

```bash
java -jar target/download-studio-1.0.0.jar
```

For faster development, run `npm start` in `frontend/` for Angular live reload
and run `./mvnw spring-boot:run` in the project root for the backend. Restart
the backend after Java changes.

## Docker

Build the image and start the container:

```bash
docker compose up --build -d
```

Compose creates `downloads/` automatically when it does not exist. To map a
different host folder, set `DOWNLOAD_DIR` in `.env` (which is intentionally not
committed) or in the command environment.

Check or stop the container:

```bash
docker compose ps
docker compose logs --tail=100 app
docker compose down
```

### Rebuild the image after code changes

The Docker build compiles both the latest Angular frontend and Spring Boot
backend. Rebuild the image and replace the running container with:

```bash
docker compose build --pull --no-cache app
docker compose up -d --force-recreate app
```

Verify the recreated container:

```bash
docker compose ps
docker compose logs --tail=100 app
```

The Docker image includes the `download_single.sh` file from the project root.

Container recreation preserves the downloads bind mount and history volume.
Do not run `docker compose down -v` unless you intentionally want to delete the
saved history volume.

## Portable bundle

Create a current image archive and ready-to-copy bundle:

```bash
./export-image.sh
```

Copy the contents of `artifacts/portable/` to the other computer. Its bare
minimum is:

- `download-studio-1.0.0.tar`
- `download-studio-1.0.0.tar.sha256`
- `docker-compose.yml`

On the destination computer, run:

```bash
sha256sum --check download-studio-1.0.0.tar.sha256
docker load --input download-studio-1.0.0.tar
docker compose up -d
```

On Windows 11, use Docker Desktop and run the Docker commands above in
PowerShell. To verify the archive there, compare the output of
`Get-FileHash .\download-studio-1.0.0.tar -Algorithm SHA256` with the hash in
`download-studio-1.0.0.tar.sha256`. The `downloads` directory is created
automatically on first start.

The destination needs Docker with Compose, but it does not need the source
code, Java, Node.js, `yt-dlp`, or `ffmpeg`.

## Documentation

- Technical documentation, diagrams, and the PDF handbook are under [`docs/`](docs/).
- Portable Docker image archives and checksums are generated under `artifacts/portable/`.
- Run `./export-image.sh` when a new portable image archive is required.
