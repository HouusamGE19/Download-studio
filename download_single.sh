#!/usr/bin/env bash
#
# download_single.sh
# Downloads ONE video or ONE playlist (auto-detected from the URL) at
# the best available quality.
#
# USAGE:
#   ./download_single.sh "VIDEO_OR_PLAYLIST_URL"
#   ./download_single.sh "VIDEO_OR_PLAYLIST_URL" /path/to/save
#   ./download_single.sh "VIDEO_OR_PLAYLIST_URL" /path/to/save chrome
#
# - If the URL is a single video, only that video downloads (even if
#   it happens to have a playlist attached in the link).
# - If the URL is a playlist link, every video in it downloads,
#   numbered in order, into its own folder.
#
# The 3rd argument is optional, only needed for private/unlisted/
# age-restricted content — pass the browser you're logged into
# YouTube with (firefox, chrome, chromium, brave, edge...).
#
# REQUIREMENTS: yt-dlp and ffmpeg installed and on your PATH.

set -uo pipefail

URL="${1:?Usage: $0 <video_or_playlist_url> [output_dir] [browser_for_cookies]}"
OUTDIR="${2:-./YouTube_Backup}"
COOKIE_BROWSER="${3:-}"

command -v yt-dlp >/dev/null 2>&1 || { echo "yt-dlp not found. Install it first."; exit 1; }
command -v ffmpeg >/dev/null 2>&1 || { echo "ffmpeg not found. Install it first."; exit 1; }

mkdir -p "$OUTDIR"

COOKIE_ARGS=()
if [[ -n "$COOKIE_BROWSER" ]]; then
    COOKIE_ARGS=(--cookies-from-browser "$COOKIE_BROWSER")
fi

# Decide the output layout based on the URL itself rather than a fragile
# yt-dlp template trick: a real playlist link gets a subfolder + numbering,
# a plain video link just lands directly in OUTDIR.
PLAYLIST_ARGS=(--no-playlist)
if [[ "$URL" == *"/playlist?"* ]]; then
    OUTTMPL="$OUTDIR/%(playlist)s/%(playlist_index)03d - %(title)s.%(ext)s"
    PLAYLIST_ARGS=(--yes-playlist)
else
    OUTTMPL="$OUTDIR/%(title)s.%(ext)s"
fi

echo "============================================================"
echo " URL:       $URL"
echo " Saving to: $OUTDIR"
echo " Cookies:   ${COOKIE_BROWSER:-none (public content only)}"
echo "============================================================"

yt-dlp \
    -f "bestvideo+bestaudio/best" \
    --merge-output-format mkv \
    --js-runtimes node \
    "${PLAYLIST_ARGS[@]}" \
    "${COOKIE_ARGS[@]}" \
    --continue \
    --ignore-errors \
    --retries 10 \
    --fragment-retries 10 \
    -r 3M \
    --sleep-requests 1 \
    --embed-thumbnail \
    --embed-metadata \
    --newline \
    --progress \
    --progress-template "download:DL_PROGRESS|%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s|%(info.title)s" \
    --progress-template "postprocess:DL_PROCESSING|%(info.title)s" \
    --print "after_move:DL_FILE|%(filepath)s" \
    -o "$OUTTMPL" \
    "$URL" 2>&1

DOWNLOAD_STATUS=$?

echo "============================================================"
if [[ $DOWNLOAD_STATUS -eq 0 ]]; then
    echo " Done."
else
    echo " Download failed (exit code $DOWNLOAD_STATUS)."
fi
echo "============================================================"

exit "$DOWNLOAD_STATUS"
