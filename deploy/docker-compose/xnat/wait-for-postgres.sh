#!/bin/sh
# wait-for-postgres.sh
# Blocks until PostgreSQL is accepting connections, then execs the given command.

set -e

cmd="$@"

until psql -U "$XNAT_DATASOURCE_USERNAME" -h xnat-db -c '\q' 2>/dev/null; do
  >&2 echo "Postgres is unavailable - sleeping"
  sleep 5
done

>&2 echo "Postgres is up - executing command"
exec $cmd
