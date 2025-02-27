# temurin is available under the Apache-2.0-license; the OpenJDK under the GNU GPL v2 license with Classpath Exception
FROM eclipse-temurin:21-jre-jammy

ENV PKI_CHAIN_DOWNLOAD_URL=https://pki.pca.dfn.de/dfn-ca-global-g2/pub/cacert/chain.txt
ENV PKI_CHAIN_SHA256=3faab8a9915b567c25cd982c92daa6dbd65807d14436adec2f1040a78701522a
ENV WAIT_FOR_IT_DOWNLOAD_URL=https://raw.githubusercontent.com/vishnubob/wait-for-it/81b1373f17855a4dc21156cfe1694c31d7d1792e/wait-for-it.sh
ENV WAIT_FOR_IT_SHA256=b7a04f38de1e51e7455ecf63151c8c7e405bd2d45a2d4e16f6419db737a125d6

RUN export DEBIAN_FRONTEND=noninteractive && \
    apt-get -y update && \
    apt-get -y upgrade && \
    apt-get -y dist-upgrade && \
    useradd -Ums /bin/bash spring && \
    curl -Lso /usr/local/share/ca-certificates/chain.txt ${PKI_CHAIN_DOWNLOAD_URL} || (>&2 echo -e "\ndownload failed\n" && exit 1) && \
    sha256sum /usr/local/share/ca-certificates/chain.txt | grep -q ${PKI_CHAIN_SHA256} > /dev/null || (>&2 echo "sha256sum failed $(sha256sum /usr/local/share/ca-certificates/chain.txt)" && exit 1) && \
    cp /usr/local/share/ca-certificates/chain.txt /usr/local/share/ca-certificates/chain.crt && \
    chmod 644 /usr/local/share/ca-certificates/chain.txt && \
    chmod 644 /usr/local/share/ca-certificates/chain.crt && \
    dpkg-reconfigure ca-certificates && \
    update-ca-certificates

USER spring
WORKDIR /home/spring

COPY ../src/main/resources/scripts/prepare.sh prepare.sh
COPY ../src/main/resources/scripts/entrypoint.sh entrypoint.sh
COPY ../src/main/resources/application.yml.template .
ADD ../target/ace*.jar ace.jar

USER root
# permissions
# care about the special chars here, do not use pipes in the name
RUN curl -Lso wait-for-it.sh ${WAIT_FOR_IT_DOWNLOAD_URL} || (>&2 echo -e "\ndownload failed\n" && exit 1) && \
    sha256sum wait-for-it.sh | grep -q ${WAIT_FOR_IT_SHA256} > /dev/null || (>&2 echo "sha256sum failed $(sha256sum wait-for-it.sh)" && exit 1) && \
    chmod 664 -R * && \
    chmod +x prepare.sh && \
    chmod +x entrypoint.sh && \
    chmod +x wait-for-it.sh && \
    chmod 775 ace.jar && \
    chown -R spring:spring *

USER spring

EXPOSE 8080

CMD ["./entrypoint.sh"]