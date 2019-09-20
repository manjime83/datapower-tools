const fs = require('fs');
const jose = require('node-jose');
const axios = require('axios');
const https = require('https');

const profileName = process.argv.slice(2)[0] || 'default';
const profile = {};
const certificates = {};

runProfile(profileName);

function runProfile(profileName) {
    console.log(`running ${profileName} profile...`);

    fs.readFile(`${profileName}.json`, 'utf8', (error, data) => {
        if (error) {
            console.error(`Error loading ${profileName} profile:`, error);
        } else {
            Object.assign(profile, JSON.parse(data));
            loadClientKey();
        }
    });
}

function loadClientKey() {
    fs.readFile(profile['client-private-key'], 'utf8', (error, data) => {
        if (error) {
            console.error('Error loading client private key:', error);
        } else {
            jose.JWK.asKey(data, 'pem').then((clientKey) => {
                Object.assign(certificates, { clientKey, pemClientKey: data });
                loadServerCertificate();
            });
        }
    });
}

function loadServerCertificate() {
    fs.readFile(profile['server-public-certificate'], 'utf8', (error, data) => {
        if (error) {
            console.error('Error loading server public certificate:', error);
        } else {
            jose.JWK.asKey(data, 'pem').then((serverCertificate) => {
                Object.assign(certificates, { serverCertificate, pemServerCertificate: data });

                makeLogsDirectory();
            });
        }
    });
}

function makeLogsDirectory() {
    fs.mkdir('logs', (error) => {
        if (error && error.code !== 'EEXIST') {
            console.error('Error creating logs directory:', error);
        } else {
            loadRequest();
        }
    });
}

function loadRequest() {
    let body = profile['request-body'];
    if (typeof body === 'object') {
        body = JSON.stringify(body);
        signAndEncrypt(body);
    } else {
        fs.readFile(body, 'utf8', (error, data) => {
            if (error) {
                console.error(`Error loading ${body} file:`, error);
            } else {
                signAndEncrypt(data);
            }
        });
    }
}

function signAndEncrypt(body) {
    const signOptions = {
        fields: Object.assign({ alg: 'RS256' }, profile['signature-headers']),
        format: profile['signature-format']
    };

    jose.JWS.createSign(signOptions, certificates.clientKey).update(body, 'utf8').final().then((signedBody) => {
        if (profile['signature-format'] === 'flattened') {
            signedBody = JSON.stringify(signedBody);
        }

        fs.writeFile(`logs/${profileName}-signed.txt`, signedBody, 'utf8', (error) => {
            if (error) {
                return console.error('Error writing signed file:', error);
            }
        });

        if (profile['enable-encryption']) {
            const encryptOptions = {
                fields: { enc: profile['encryption-enc'], alg: profile['encryption-alg'] },
                format: profile['encryption-format']
            };

            jose.JWE.createEncrypt(encryptOptions, certificates.serverCertificate).update(signedBody).final().then(function (encryptedBody) {
                if (profile['encryption-format'] === 'flattened') {
                    encryptedBody = JSON.stringify(encryptedBody);
                }

                fs.writeFile(`logs/${profileName}-encrypted.txt`, encryptedBody, 'utf8', (error) => {
                    if (error) {
                        return console.error('Error writing encrypted file:', error);
                    }
                });

                sendRequest(encryptedBody, profile['encryption-format']);
            });
        } else {
            sendRequest(signedBody, profile['signature-format']);
        }
    });
}

function sendRequest(body, format) {
    let contentType;
    if (format === 'compact') {
        contentType = 'text/plain';
    } else if (format === 'flattened') {
        contentType = 'application/json';
        body = JSON.parse(body);
    }

    console.time('response time');

    axios.post(profile['request-url'], body, {
        httpsAgent: new https.Agent({
            key: certificates.pemClientKey,
            cert: certificates.pemClientCertificate,
            ca: [certificates.pemServerCertificate],
            checkServerIdentity: (hostname, cert) => {
                return undefined;
            }
        }),
        headers: Object.assign({ 'content-type': contentType }, profile['request-headers']),
        auth: { username: profile['request-user'], password: profile['request-password'] },
        timeout: profile['request-timeout'] * 1000
    }).then((response) => {
        console.timeEnd('response time');
        console.log(response.status, response.statusText);

        if (format === 'compact') {
            decryptAndVerify(response.data);
        } else if (format === 'flattened') {
            decryptAndVerify(JSON.stringify(response.data));
        }
    }).catch((error) => {
        console.error('Error on server request:', error.message);
    });
}

function decryptAndVerify(data) {
    if (profile['enable-encryption']) {
        if (profile['encryption-format'] === 'flattened') {
            data = JSON.parse(data);
        }

        const decryptOptions = {
            algorithms: ["A*GCM", "RSA*"]
        };

        jose.JWE.createDecrypt(certificates.clientKey, decryptOptions).decrypt(data).then((decryptedBody) => {
            decryptedBody = decryptedBody.payload.toString();

            fs.writeFile(`logs/${profileName}-decrypted.txt`, decryptedBody, 'utf8', (error) => {
                if (error) {
                    return console.error('Error writing decrypted file:', error);
                }
            });

            verify(decryptedBody);
        }).catch((error) => {
            console.error('Error on decrypting response:', error);
        });
    } else {
        verify(data);
    }
}

function verify(signedBody) {
    if (profile['signature-format'] === 'flattened') {
        signedBody = JSON.parse(signedBody);
    }

    const verifyOptions = {
        algorithms: ["RS*"]
    };

    jose.JWS.createVerify(certificates.serverCertificate, verifyOptions).verify(signedBody).then((verifiedBody) => {
        verifiedBody = verifiedBody.payload.toString()

        fs.writeFile(`logs/${profileName}-verified.txt`, verifiedBody, 'utf8', (error) => {
            if (error) {
                return console.error('Error writing verified file:', error);
            }
        });

        console.log(verifiedBody);
    }).catch((error) => {
        console.error('Error on verifiying response:', error);
    });
}