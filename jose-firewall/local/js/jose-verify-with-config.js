const jose = require('jose');
const fs = require('fs');
const sm = require('service-metadata');
const hm = require('header-metadata');

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

    const jwsObj = jose.parse(plaintext);

    let certKey;
    if (data.config['http-header']) {
        certKey = hm.current.get(data.config['http-header']);
    } else if (data.config['jws-header']) {
        certKey = jwsObj.getProtected(data.config['http-header']);
    } else if (data.config['context-variable']) {
        const contextParts = data.config['context-variable'].substring(6).split('/').slice(1);
        const name = contextParts.shift();
        const variable = contextParts.join('/');

        certKey = session.name(name).getVariable(variable);
    } else {
        session.reject('Certificate key not specified.');
        return;
    }

    const context = session.name('security') || session.createContext('security');
    context.setVariable('cert-key', certKey);

    const certificate = data.certs[certKey];
    if (certificate) {
        jwsObj.getSignatures().forEach((signature) => {
            signature.setKey(certificate);
        });

        jose.createJWSVerifier(jwsObj).validate(validateCallback.bind(this, jwsObj));
    } else {
        session.reject('Verification certificate not found.');
    }
}

function validateCallback(jwsObj, error) {
    if (error) {
        session.reject(error.errorMessage);
        return;
    }

    session.output.write(JSON.parse(jwsObj.getPayload()));
}