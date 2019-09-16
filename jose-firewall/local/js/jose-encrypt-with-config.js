const jose = require('jose');
const fs = require('fs');
const sm = require('service-metadata');

fs.readAsJSON(`local:///json/service-certs/${sm.processorName}.json`, readFileCallback);

function readFileCallback(error, data) {
    if (error) {
        session.reject(error.errorMessage);
        return;
    }

    session.input.readAsBuffer(readInputCallback.bind(this, data));
}

function readInputCallback(data, error, plaintext) {
    if (error) {
        session.reject(error.errorMessage);
        return;
    }

    const context = session.name('security') || session.createContext('security');
    const certKey = context.getVariable('cert-key');

    const certificate = data.certs[certKey];
    if (certificate) {
        const jweHdr = jose.createJWEHeader(data.config.jwe.enc);
        jweHdr.setProtected('alg', data.config.jwe.alg);

        if (data.config.jwe.format === 'compact') {
            jweHdr.setKey(certificate);
        } else {
            jweHdr.addRecipient(certificate);
        }

        jose.createJWEEncrypter(jweHdr).update(plaintext).encrypt(data.config.jwe.format, encryptCallback);
    } else {
        session.reject('Encryption certificate not found.');
    }
}

function encryptCallback(error, jweObj) {
    if (error) {
        session.reject(error.errorMessage);
        return;
    }

    session.output.write(jweObj);
}