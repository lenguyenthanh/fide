#!/bin/sh

REMOTE=$1
REMOTE_DIR="/home/fide"

echo "Deploy to server $REMOTE:$REMOTE_DIR"

sbt backend/stage

if [ $? != 0 ]; then
  echo "Deploy canceled"
  exit 1
fi

RSYNC_OPTIONS=" \
  --archive \
  --no-o --no-g \
  --force \
  --delete \
  --progress \
  --compress \
  --checksum \
  --verbose \
  --exclude RUNNING_PID \
  --exclude '.git/'"

stage="modules/backend/target/universal/stage"

include="$stage/bin $stage/lib"
rsync_command="rsync $RSYNC_OPTIONS $include $REMOTE:$REMOTE_DIR"
echo "$rsync_command"
$rsync_command
echo "rsync complete"

read -n 1 -p "Press [Enter] to continue."

echo "Restart fide"
ssh $REMOTE "chown -R fide:fide /home/fide && systemctl restart fide"

echo "Deploy complete"
