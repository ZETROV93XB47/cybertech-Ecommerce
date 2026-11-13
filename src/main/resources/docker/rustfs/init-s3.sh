#!/bin/sh
set -eu

BUCKET_NAME="${BUCKET_NAME:-cybertech-products}"
S3_ENDPOINT="${S3_ENDPOINT:-http://rustfs:9000}"
MAX_ATTEMPTS=60
ATTEMPT=1

echo "[rustfs-init] Waiting for RustFS at ${S3_ENDPOINT}..."
until aws --endpoint-url "${S3_ENDPOINT}" s3api list-buckets >/dev/null 2>&1; do
  if [ "${ATTEMPT}" -ge "${MAX_ATTEMPTS}" ]; then
    echo "[rustfs-init] RustFS did not become ready after ${MAX_ATTEMPTS} attempts." >&2
    exit 1
  fi
  ATTEMPT=$((ATTEMPT + 1))
  sleep 2
done

if ! aws --endpoint-url "${S3_ENDPOINT}" s3api head-bucket --bucket "${BUCKET_NAME}" >/dev/null 2>&1; then
  echo "[rustfs-init] Creating bucket s3://${BUCKET_NAME}..."
  aws --endpoint-url "${S3_ENDPOINT}" s3api create-bucket --bucket "${BUCKET_NAME}"
fi

echo "[rustfs-init] Applying public-read policy to s3://${BUCKET_NAME}..."
aws --endpoint-url "${S3_ENDPOINT}" s3api put-bucket-policy \
  --bucket "${BUCKET_NAME}" \
  --policy "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"PublicReadGetObject\",\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::${BUCKET_NAME}/*\"}]}"

echo "[rustfs-init] Configuring browser CORS on s3://${BUCKET_NAME}..."
aws --endpoint-url "${S3_ENDPOINT}" s3api put-bucket-cors \
  --bucket "${BUCKET_NAME}" \
  --cors-configuration '{"CORSRules":[{"AllowedOrigins":["http://localhost:3000","http://localhost:4200","http://127.0.0.1:3000"],"AllowedMethods":["GET","HEAD"],"AllowedHeaders":["*"],"ExposeHeaders":["ETag"],"MaxAgeSeconds":3000}]}'

echo "[rustfs-init] Bucket ${BUCKET_NAME} is ready."
