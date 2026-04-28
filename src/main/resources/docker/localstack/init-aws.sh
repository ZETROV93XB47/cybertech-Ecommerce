#!/bin/bash
# LocalStack init hook — runs once at boot when LocalStack reaches "ready"
# state (mounted into /etc/localstack/init/ready.d/ inside the container).
#
# Creates the S3 bucket the backend uploads product photos to and opens
# CORS so the Next.js dev server (http://localhost:3000) can <Image src>
# fetch them directly. Idempotent: safe to re-run on every container start.
#
# Reference:
#   https://docs.localstack.cloud/references/init-hooks/

set -euo pipefail

BUCKET_NAME="${BUCKET_NAME:-cybertech-products}"

echo "[init-aws] Ensuring bucket s3://${BUCKET_NAME} exists..."
awslocal s3api head-bucket --bucket "${BUCKET_NAME}" 2>/dev/null \
  || awslocal s3 mb "s3://${BUCKET_NAME}"

echo "[init-aws] Setting public-read object ACL policy on s3://${BUCKET_NAME}..."
awslocal s3api put-bucket-policy --bucket "${BUCKET_NAME}" --policy '{
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

echo "[init-aws] Configuring CORS on s3://${BUCKET_NAME}..."
awslocal s3api put-bucket-cors --bucket "${BUCKET_NAME}" --cors-configuration '{
  "CORSRules": [
    {
      "AllowedOrigins": ["http://localhost:3000", "http://localhost:4200", "http://127.0.0.1:3000"],
      "AllowedMethods": ["GET", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
}'

echo "[init-aws] Done."
