#!/bin/bash -e

# Used to create a truststore for the spring boot application

apt-get install curl

echo "Remove chain.txt in case it exists"
rm -rf chain.txt

PKI_CHAIN_DOWNLOAD_URL=https://pki.pca.dfn.de/dfn-ca-global-g2/pub/cacert/chain.txt
PKI_CHAIN_SHA256=3faab8a9915b567c25cd982c92daa6dbd65807d14436adec2f1040a78701522a

echo "Remove .keystore_password in case it exists"
rm -rf .keystore_password

echo "Generate random keystore password"
KEYSTORE_PASSWORD=$(cat /dev/urandom | tr -dc "a-zA-Z0-9" | fold -w 32 | head -n 1)

echo $KEYSTORE_PASSWORD > .keystore_password

echo "Download pki chain"
curl -Lso chain.txt ${PKI_CHAIN_DOWNLOAD_URL} || (>&2 echo -e "\ndownload failed\n" && exit 1)
sha256sum chain.txt | grep -q ${PKI_CHAIN_SHA256} >  /dev/null || (>&2 echo "sha256sum failed $(sha256sum chain.txt)" && exit 1)

echo "Remove pki_chain.truststore in case it exists"
rm -rf pki_chain.truststore

echo "Generate truststore file"
keytool -import -file chain.txt -alias PKIChain -storepass ${KEYSTORE_PASSWORD} -noprompt -keystore pki_chain.truststore
