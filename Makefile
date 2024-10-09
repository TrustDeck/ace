# Makefile only works under unix
restore-test: kdb-restore
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres -c 'DROP DATABASE ace WITH (FORCE);'"
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres -c 'CREATE DATABASE ace;'"
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres -c 'ALTER DATABASE ace OWNER TO \"ace-manager\";'"
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager ace" < src/test/resources/sql/ace-schema-test.sql

kdb-dump:
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! pg_dump --clean --create --column-inserts --username ace-manager keycloak" > src/main/resources/db/keycloak-example.sql

kdb-restore:
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres -c 'DROP DATABASE keycloak WITH (FORCE);'"
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres" < src/main/resources/db/keycloak-example.sql
	docker compose --project-name ace-dev --file docker-compose.dev.yml restart ace-keycloak
	sleep 7

acedb-dump:
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! pg_dump --clean --create --column-inserts --username ace-manager ace" > src/test/resources/sql/ace-dump.sql

acedb-dump-schema:
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! pg_dump --clean --create --schema-only --no-comments --username ace-manager ace" > examples/db/ace-schema.sql

acedb-restore-schema:
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres -c 'DROP DATABASE ace WITH (FORCE);'"
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres -c 'CREATE DATABASE ace;'"
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager postgres -c 'ALTER DATABASE ace OWNER TO \"ace-manager\";'"
	docker exec -i ace-postgresql /bin/bash -c "PGPASSWORD=ps3udoNym1zation! psql --username ace-manager ace" < src/main/resources/db/ace-schema.sql

start-ace-kc-dev:
	docker compose --project-name ace-dev --file docker-compose.dev.yml up -d ace-keycloak

start-ace-ps-dev:
	docker compose --project-name ace-dev --file docker-compose.dev.yml up -d ace-postgresql

wait:
	sleep 7

reset: clean-dev start-ace-ps-dev wait start-ace-kc-dev

clean-dev:
	docker compose --project-name ace-dev --file docker-compose.dev.yml down --volumes
	sleep 5

#used to test docker build works on a pipeline
docker-build:
	docker buildx build --no-cache -t ace:latest --file Dockerfile .

build:
	mvn clean install -DskipTests=true -Djooq.skip=true -f pom.xml
	mvn clean package -DskipTests=true -Djooq.skip=true -f pom.xml

jooq:
	mvn jooq-codegen:generate -f pom.xml

.PHONY: kdb-dump kdb-restore acedb-dump acedb-dump-schema acedb-restore-schema start-ace-kc-dev start-ace-ps-dev wait reset clean-dev docker-build build jooq restore-test