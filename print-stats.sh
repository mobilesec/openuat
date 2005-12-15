#!/bin/bash
while true; do 
  echo "success: `expr \`egrep '^\+' reports/statistics.log | wc -l\` / 2`, start-of-auth: `grep start-of-auth reports/statistics.log | wc -l`, delays: `grep delays reports/statistics.log | wc -l`, missed: `grep 'Did not receive' reports/debug.log | wc -l`, failed: `grep failed reports/statistics.log | wc -l`"; sleep 10s
done