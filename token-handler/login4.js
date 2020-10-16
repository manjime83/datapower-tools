const hm = require('header-metadata');
const { initAuthorizationToken, setAuthorizationToken } = require('./authorizationToken');

// const now = Math.floor(new Date().getTime() / 1000);
const contextName = 'colpatria';

// initAuthorizationToken(contextName);

setAuthorizationToken({
  contextName,
  tokenEndpoint: 'http://ubuntu:8080/auth/realms/colpatria/protocol/openid-connect/token',
  scope: 'openid',
  sslClientProfile: 'initToken',
  timeout: 10,
}) // asignar el clientId y el clientSecret en el UserAgent
  .then((access_token) => {
    hm.current.set('Authorization', `Bearer ${access_token}`);
  })
  .catch((error) => {
    session.output.write({ error });
  });
