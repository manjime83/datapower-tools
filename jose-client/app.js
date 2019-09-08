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

    fs.readFile(`${profileName}.json`, 'utf8', (err, data) => {
        if (err) {
            console.error(`Error loading ${profileName} profile:`, err.message);
        } else {
            Object.assign(profile, JSON.parse(data));
            loadClientKey();
        }
    });
}

function loadClientKey() {
    fs.readFile(profile['client-private-key'], 'utf8', (err, data) => {
        if (err) {
            console.error('Error loading client private key:', err.message);
        } else {
            jose.JWK.asKey(data, 'pem').then((clientKey) => {
                Object.assign(certificates, { clientKey, pemClientKey: data });
                loadClientCertificate();
            });
        }
    });
}

function loadClientCertificate() {
    fs.readFile(profile['client-public-certificate'], 'utf8', (err, data) => {
        if (err) {
            console.error('Error loading client public certificate:', err.message);
        } else {
            jose.JWK.asKey(data, 'pem').then((clientCertificate) => {
                Object.assign(certificates, { clientCertificate, pemClientCertificate: data });
                loadServerCertificate();
            });
        }
    });
}

function loadServerCertificate() {
    fs.readFile(profile['server-public-certificate'], 'utf8', (err, data) => {
        if (err) {
            console.error('Error loading server public certificate:', err.message);
        } else {
            jose.JWK.asKey(data, 'pem').then((serverCertificate) => {
                Object.assign(certificates, { serverCertificate, pemServerCertificate: data });

                makeLogsDirectory();
            });
        }
    });
}

function makeLogsDirectory() {
    fs.mkdir('logs', (err) => {
        if (err && err.code !== 'EEXIST') {
            console.error('Error creating logs directory:', err.message);
        } else {
            signAndEncrypt();
        }
    });
}

function signAndEncrypt() {
    const signOptions = {
        fields: {
            kid: certificates.clientKey.kid,
            typ: 'json',
            cty: 'application/json',
            alg: 'RS256'
        }
    };

    jose.JWS.createSign(signOptions, certificates.clientKey).update(JSON.stringify(profile['request-body']), 'utf8').final().then((signedBody) => {
        fs.writeFile(`logs/${profileName}-signed.json`, JSON.stringify(signedBody, null, 2), 'utf8', (err) => {
            if (err) {
                return console.error('Error writing signed file:', err.message);
            }
        });

        if (profile['enable-encryption']) {
            const encryptOptions = {
                fields: {
                    kid: certificates.serverCertificate.kid,
                    typ: 'json',
                    cty: 'application/json',
                    alg: 'RSA-OAEP'
                }
            };

            jose.JWE.createEncrypt(encryptOptions, certificates.serverCertificate).update(JSON.stringify(signedBody)).final().then(function (encryptedBody) {
                fs.writeFile(`logs/${profileName}-encrypted.json`, JSON.stringify(encryptedBody, null, 2), 'utf8', (err) => {
                    if (err) {
                        return console.error('Error writing encrypted file:', err.message);
                    }
                });

                sendRequest(encryptedBody);
            });
        } else {
            sendRequest(signedBody);
        }
    });
}

function sendRequest(body) {
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
        headers: { 'Content-Type': 'application/json' },
        auth: { username: profile['request-user'], password: profile['request-password'] },
        timeout: profile['request-timeout'] * 1000
    }).then((response) => {
        console.timeEnd('response time');
        decryptAndVerify(response.data);
    }).catch((error) => {
        console.error('Error on server request:', error.message);
    });
}

function decryptAndVerify(responseData) {
    if (profile['enable-encryption']) {
        const decryptOptions = {
            algorithms: ["*"]
        };

        jose.JWE.createDecrypt(certificates.clientKey, decryptOptions).decrypt(responseData).then((decryptedBody) => {
            const decryptedBodyPayload = JSON.parse(decryptedBody.payload);

            fs.writeFile(`logs/${profileName}-decrypted.json`, JSON.stringify(decryptedBodyPayload, null, 2), 'utf8', (err) => {
                if (err) {
                    return console.error('Error writing decrypted file:', err.message);
                }

                verify(decryptedBodyPayload);
            });
        }).catch((error) => {
            console.error('Error on decrypting response:', error.message);
        });
    } else {
        verify(responseData);
    }
}

function verify(signedBody) {
    const verifyOptions = {
        algorithms: ["*"]
    };

    jose.JWS.createVerify(certificates.serverCertificate, verifyOptions).verify(signedBody).then((verifiedBody) => {
        const verifiedBodyPayload = JSON.parse(verifiedBody.payload);

        fs.writeFile(`logs/${profileName}-verified.json`, JSON.stringify(verifiedBodyPayload, null, 2), 'utf8', (err) => {
            if (err) {
                return console.error('Error writing verified file:', err.message);
            }

            console.log(profile['request-body']);
            console.log(verifiedBodyPayload);
        });
    }).catch((error) => {
        console.error('Error on verifiying response:', error.message);
    });
}