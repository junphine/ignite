::
:: Copyright 2019 Ignite Systems, Inc. and Contributors.
:: 
:: Licensed under the Ignite Community Edition License (the "License");
:: you may not use this file except in compliance with the License.
:: You may obtain a copy of the License at
:: 
::     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
:: 
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.
::
::
:: SSL certificates generation.
::

::
:: Preconditions:
::  1. If needed, download Open SSL for Windows from "https://wiki.openssl.org/index.php/Binaries".
::   and unpack it to some folder.
::  2. If needed, install JDK 8 or newer. We need "keytool" from "JDK/bin."
::  3. Create "openssl.cnf" in some folder.
::     You may use "https://github.com/openssl/openssl/blob/master/apps/openssl.cnf" as template.
::  4. If needed, add "opensll" & "keytool" to PATH variable.
::
::  NOTE: In case of custom SERVER_DOMAIN_NAME you may need to tweak your "etc/hosts" file.
::

:: Set paths.
set PATH_TO_OPEN_SSL=_path_where_open_ssl_was_unpacked_
set PATH_TO_JDK_KEYTOOL=_path_to_JDK_keytool_

:: Set Open SSL variables.
set RANDFILE=%PATH_TO_OPEN_SSL%\.rnd
set OPENSSL_CONF=%PATH_TO_OPEN_SSL%\openssl.cnf

:: Certificates password.
set PWD=123456

:: Server.
set SERVER_DOMAIN_NAME=localhost
set SERVER_EMAIL=support@test.com

:: Client.
set CLIENT_DOMAIN_NAME=localhost
set CLIENT_EMAIL=client@test.com

:: Cleanup.
del server.*
del client.*
del ca.*

:: Generate server config.
(
echo [req]
echo prompt                 = no
echo distinguished_name     = dn
echo req_extensions         = req_ext

echo [ dn ]
echo countryName            = RU
echo stateOrProvinceName    = Test
echo localityName           = Test
echo organizationName       = Apache
echo commonName             = %SERVER_DOMAIN_NAME%
echo organizationalUnitName = IT
echo emailAddress           = %SERVER_EMAIL%

echo [ req_ext ]
echo subjectAltName         = @alt_names

echo [ alt_names ]
echo DNS.1                  = %SERVER_DOMAIN_NAME%
) > "server.cnf"

:: Generate client config.
(
echo [req]
echo prompt                 = no
echo distinguished_name     = dn
echo req_extensions         = req_ext

echo [ dn ]
echo countryName            = RU
echo stateOrProvinceName    = Test
echo localityName           = Test
echo organizationName       = Apache
echo commonName             = %CLIENT_DOMAIN_NAME%
echo organizationalUnitName = IT
echo emailAddress           = %CLIENT_EMAIL%

echo [ req_ext ]
echo subjectAltName         = @alt_names

echo [ alt_names ]
echo DNS.1                  = %CLIENT_DOMAIN_NAME%
) > "client.cnf"

:: Generate certificates.
%PATH_TO_OPEN_SSL%\openssl genrsa -des3 -passout pass:%PWD% -out server.key 1024
%PATH_TO_OPEN_SSL%\openssl req -new -passin pass:%PWD% -key server.key -config server.cnf -out server.csr

%PATH_TO_OPEN_SSL%\openssl req -new -newkey rsa:1024 -nodes -keyout ca.key -x509 -days 365 -config server.cnf -out ca.crt

%PATH_TO_OPEN_SSL%\openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key -set_serial 01 -extensions req_ext -extfile server.cnf -out server.crt
%PATH_TO_OPEN_SSL%\openssl rsa -passin pass:%PWD% -in server.key -out server.nopass.key

%PATH_TO_OPEN_SSL%\openssl req -new -utf8 -nameopt multiline,utf8 -newkey rsa:1024 -nodes -keyout client.key -config client.cnf -out client.csr
%PATH_TO_OPEN_SSL%\openssl x509 -req -days 365 -in client.csr -CA ca.crt -CAkey ca.key -set_serial 02 -out client.crt

%PATH_TO_OPEN_SSL%\openssl pkcs12 -export -in server.crt -inkey server.key -certfile server.crt -out server.p12 -passin pass:%PWD% -passout pass:%PWD%
%PATH_TO_OPEN_SSL%\openssl pkcs12 -export -in client.crt -inkey client.key -certfile ca.crt -out client.p12 -passout pass:%PWD%
%PATH_TO_OPEN_SSL%\openssl pkcs12 -export -in ca.crt -inkey ca.key -certfile ca.crt -out ca.p12 -passout pass:%PWD%

%PATH_TO_JDK_KEYTOOL% -importkeystore -srckeystore server.p12 -srcstoretype PKCS12 -destkeystore server.jks -deststoretype JKS -noprompt -srcstorepass %PWD% -deststorepass %PWD%
%PATH_TO_JDK_KEYTOOL% -importkeystore -srckeystore client.p12 -srcstoretype PKCS12 -destkeystore client.jks -deststoretype JKS -noprompt -srcstorepass %PWD% -deststorepass %PWD%
%PATH_TO_JDK_KEYTOOL% -importkeystore -srckeystore ca.p12 -srcstoretype PKCS12 -destkeystore ca.jks -deststoretype JKS -noprompt -srcstorepass %PWD% -deststorepass %PWD%

%PATH_TO_OPEN_SSL%\openssl x509 -text -noout -in server.crt > server.txt
%PATH_TO_OPEN_SSL%\openssl x509 -text -noout -in client.crt > client.txt
%PATH_TO_OPEN_SSL%\openssl x509 -text -noout -in ca.crt > ca.txt
