#!/bin/sh
set -eu
cd "$(dirname "$0")"
printf 'HTTPS LensDrop server origin: '
read -r server
printf 'Full watch folder path: '
read -r folder
printf 'Queue state folder outside the watch folder: '
read -r state
exec python3 lensdrop_agent.py --server "$server" --folder "$folder" --state "$state"
