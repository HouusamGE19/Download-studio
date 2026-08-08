# Docker image

The image contains the Angular production bundle, Spring Boot, Java 17, Node.js, `yt-dlp`, and `ffmpeg`. Only Docker is required on the destination computer.

## Build and run on this computer

```bash
cd download-studio
docker compose up --build -d
```

Open <http://127.0.0.1:8080>. Downloads are written automatically to the `downloads/` directory beside `docker-compose.yml`; Compose creates it when needed. There is no server-filesystem browser in the Docker UI.

To use another host directory, create `.env` from the example and set an absolute path:

```bash
cp .env.example .env
```

```dotenv
DOWNLOAD_DIR=/home/your-user/Videos/YouTube
DOWNLOAD_STUDIO_PORT=8080
```

Stop the container with:

```bash
docker compose down
```

## Export for another computer

Create a portable Docker image archive:

```bash
./export-image.sh
```

A ready-to-copy bundle is written to `artifacts/portable/`. Copy these three files from that directory to one directory on the destination computer:

- `download-studio-1.0.0.tar`
- `download-studio-1.0.0.tar.sha256`
- `docker-compose.yml`

On the destination computer:

```bash
sha256sum --check download-studio-1.0.0.tar.sha256
docker load --input download-studio-1.0.0.tar
docker compose up -d
```

Then open <http://127.0.0.1:8080>. Videos will be stored in the `downloads` directory beside the Compose file.

To use a different destination directory on that computer:

```bash
DOWNLOAD_DIR=/absolute/path/to/videos docker compose up -d
```

## Important notes

- The exported image targets the CPU architecture of the computer that builds it. The current machine produces an `amd64` image. Rebuild on the destination computer if it uses ARM.
- The container runs as an unprivileged user and publishes the website only on the destination computer's loopback interface.
- The Docker UI intentionally has no folder picker; all completed files go through `/downloads` to the mapped host directory.
- Temporary `.part` files may exist while a download is active; successful downloads leave only the final `.mkv` files.
