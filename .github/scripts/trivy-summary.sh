#!/bin/sh
# Prints a Trivy table report in the log and writes it to the summary of the run, the totals as
# a headline and the report folded under it. A summary holds one mebibyte a step at most.
#
# Usage: trivy-summary.sh <report file> <name of the scan>
set -eu

report=$1
name=$2

if [ ! -s "$report" ]; then
  echo "### $name: not scanned" >> "$GITHUB_STEP_SUMMARY"
  exit 0
fi

cat "$report"

totals=$(grep -E '^(Total|Failures):' "$report" | paste -sd ';' - | sed 's/;/; /g')
if [ -z "$totals" ]; then
  echo "### $name: nothing reported" >> "$GITHUB_STEP_SUMMARY"
  exit 0
fi

{
  echo "### $name: $totals"
  echo
  echo '<details><summary>Report</summary>'
  echo
  echo '```text'
  head -c 900000 "$report"
  echo '```'
  echo
  echo '</details>'
} >> "$GITHUB_STEP_SUMMARY"
