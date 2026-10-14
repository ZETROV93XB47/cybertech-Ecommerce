#!/bin/bash
# One-shot init for the local S3 (RustFS): runs from the s3-init service of docker-compose.yml.
#
# Creates the bucket the backend uploads product photos to, makes its objects publicly readable
# (the frontend loads them by URL) and opens CORS for the Next.js dev origin. Idempotent: safe to
# re-run on every `docker compose up`.

set -euo pipefail

BUCKET_NAME="${BUCKET_NAME:-cybertech-products}"
S3="aws --endpoint-url ${S3_ENDPOINT:-http://rustfs:9000}"

echo "[s3-init] Waiting for the S3 endpoint..."
for _ in $(seq 1 60); do
  if $S3 s3api list-buckets >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[s3-init] Ensuring bucket s3://${BUCKET_NAME} exists..."
$S3 s3api head-bucket --bucket "${BUCKET_NAME}" 2>/dev/null \
  || $S3 s3api create-bucket --bucket "${BUCKET_NAME}"

echo "[s3-init] Setting public-read policy on s3://${BUCKET_NAME}..."
$S3 s3api put-bucket-policy --bucket "${BUCKET_NAME}" --policy '{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::'"${BUCKET_NAME}"'/*"
    }
  ]
}'

echo "[s3-init] Configuring CORS on s3://${BUCKET_NAME}..."
$S3 s3api put-bucket-cors --bucket "${BUCKET_NAME}" --cors-configuration '{
  "CORSRules": [
    {
      "AllowedOrigins": ["http://localhost:3000", "http://localhost:4200", "http://127.0.0.1:3000"],
      "AllowedMethods": ["GET", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
}' || echo "[s3-init] WARNING: CORS not applied (only needed for direct browser fetches)"

echo "[s3-init] Done."
