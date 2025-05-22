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
sed -i 's|SPRING_DATASOURCE_NAME|'"${SPRING_DATASOURCE_NAME}"'|g' run.yml
sed -i 's|SPRING_DATASOURCE_HOST|'"${SPRING_DATASOURCE_HOST}"'|g' run.yml
sed -i 's|SPRING_DATASOURCE_PORT|'"${SPRING_DATASOURCE_PORT}"'|g' run.yml
sed -i 's|SPRING_DATASOURCE_USERNAME|'"${SPRING_DATASOURCE_USERNAME}"'|g' run.yml
sed -i 's|SPRING_DATASOURCE_PASSWORD|'"${SPRING_DATASOURCE_PASSWORD}"'|g' run.yml
sed -i 's|SPRING_DATASOURCE_MAX_POOLSIZE|'"${SPRING_DATASOURCE_MAX_POOLSIZE}"'|g' run.yml
sed -i 's|SPRING_DATASOURCE_CONNECTION_TIMEOUT|'"${SPRING_DATASOURCE_CONNECTION_TIMEOUT}"'|g' run.yml

# OIDC configurations
sed -i 's|SPRING_KEYCLOAK_REALM|'"${SPRING_KEYCLOAK_REALM}"'|g' run.yml
sed -i 's|SPRING_KEYCLOAK_AUTH_SERVER_URL|'"${SPRING_KEYCLOAK_AUTH_SERVER_URL}"'|g' run.yml
sed -i 's|SPRING_KEYCLOAK_CLIENT_ID|'"${SPRING_KEYCLOAK_CLIENT_ID}"'|g' run.yml
sed -i 's|SPRING_KEYCLOAK_CLIENT_SECRET|'"${SPRING_KEYCLOAK_CLIENT_SECRET}"'|g' run.yml
sed -i 's|SPRING_KEYCLOAK_ADMIN_USERNAME|'"${SPRING_KEYCLOAK_ADMIN_USERNAME}"'|g' run.yml
sed -i 's|SPRING_KEYCLOAK_ADMIN_PASSWORD|'"${SPRING_KEYCLOAK_ADMIN_PASSWORD}"'|g' run.yml
sed -i 's|SPRING_GROUP_MAPPER_NAME|'"${SPRING_GROUP_MAPPER_NAME}"'|g' run.yml
sed -i 's|LOG_LEVEL_ROOT|'"${LOG_LEVEL_ROOT}"'|g' run.yml
sed -i 's|LOG_LEVEL_ACE|'"${LOG_LEVEL_ACE}"'|g' run.yml
