#!/bin/bash -e

echo "Creating configuration ..."
rm -rf run.yml
cp application.yml.template run.yml

echo "Reading ENV from file ..."
for VAR_NAME in $(env | grep '^SPRING_[^=]\+__FILE=.\+' | sed -r "s/([^=]*)__FILE=.*/\1/g"); do
    VAR_NAME_FILE="$VAR_NAME"__FILE
    if [ "${!VAR_NAME}" ]; then
        unset "$VAR_NAME"
        echo >&2 "WARNING: Both $VAR_NAME and $VAR_NAME_FILE are set. Preferring FILE over ENV."
    fi
    echo "Getting secret $VAR_NAME from ${!VAR_NAME_FILE}"
    export "$VAR_NAME"="$(< "${!VAR_NAME_FILE}")"
    unset "$VAR_NAME_FILE"
done

echo "Replacing ENV in configurations ..."
# DB configurations

sed -i 's|TRUSTDECK_LOG_LEVEL|'"${TRUSTDECK_LOG_LEVEL:-INFO}"'|g' run.yml
sed -i 's|KEYCLOAK_SERVER_URI|'"${KEYCLOAK_SERVER_URI}"'|g' run.yml
sed -i 's|TRUSTSTORE_PATH|'"${TRUSTSTORE_PATH:-classpath:pki_chain.truststore}"'|g' run.yml
sed -i 's|TRUSTSTORE_PASSWORD|'"${TRUSTSTORE_PASSWORD:-l129VwykBVwp5FlfdPe7qQ9GOaq2rckC}"'|g' run.yml
sed -i 's|KEYCLOAK_CLIENT_ID|'"${KEYCLOAK_CLIENT_ID}"'|g' run.yml
sed -i 's|KEYCLOAK_CLIENT_SECRET|'"${KEYCLOAK_CLIENT_SECRET}"'|g' run.yml
sed -i 's|KEYCLOAK_REALM_NAME|'"${KEYCLOAK_REALM_NAME}"'|g' run.yml
sed -i 's|KEYCLOAK_CACHE_ADMIN_USER|'"${KEYCLOAK_CACHE_ADMIN_USER}"'|g' run.yml
sed -i 's|KEYCLOAK_CACHE_ADMIN_PASSWORD|'"${KEYCLOAK_CACHE_ADMIN_PASSWORD}"'|g' run.yml
sed -i 's|DATABASE_TRUSTDECK_USER|'"${DATABASE_TRUSTDECK_USER}"'|g' run.yml
sed -i 's|DATABASE_TRUSTDECK_PASSWORD|'"${DATABASE_TRUSTDECK_PASSWORD}"'|g' run.yml
sed -i 's|DATABASE_TRUSTDECK_HOST|'"${DATABASE_TRUSTDECK_HOST}"'|g' run.yml
sed -i 's|DATABASE_TRUSTDECK_PORT|'"${DATABASE_TRUSTDECK_PORT}"'|g' run.yml
sed -i 's|DATABASE_TRUSTDECK_MAX_POOLSIZE|'"${DATABASE_TRUSTDECK_MAX_POOLSIZE}"'|g' run.yml