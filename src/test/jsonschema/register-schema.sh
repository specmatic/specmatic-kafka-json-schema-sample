#!/bin/sh
set -e

SCHEMA_REGISTRY_URL="http://schema-registry:8085"
SCHEMA_REGISTRY_USER="admin"
SCHEMA_REGISTRY_PASSWORD="admin-secret"

echo "Waiting for Schema Registry to be ready..."
until curl -u "$SCHEMA_REGISTRY_USER:$SCHEMA_REGISTRY_PASSWORD" -s "${SCHEMA_REGISTRY_URL}/subjects" > /dev/null; do
  sleep 2
done
echo "Schema Registry is up."

register_schema() {
  subject="$1"
  schema_file="$2"
  curl -u "$SCHEMA_REGISTRY_USER:$SCHEMA_REGISTRY_PASSWORD" \
    -X POST \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    --data "{\"schemaType\":\"JSON\",\"schema\":$(jq -Rs . < "$schema_file")}" \
    "${SCHEMA_REGISTRY_URL}/subjects/${subject}/versions"
}

cd /usr/src/app/schemas
register_schema "new-orders-value" "NewOrders.json"
register_schema "wip-orders-value" "WipOrders.json"

echo "Schemas registered."
