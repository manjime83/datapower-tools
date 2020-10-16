const sysm = require('system-metadata');
const hm = require('header-metadata');

const urlopen = require('urlopen');
const querystring = require('querystring');
const jwt = require('jwt');

const setAuthorizationToken = async ({ contextName, tokenEndpoint, timeout, sslClientProfile, scope }) => {
  return new Promise((resolve, reject) => {
    const options = {
      target: tokenEndpoint,
      method: 'post',
      contentType: 'application/x-www-form-urlencoded',
      timeout,
      sslClientProfile,
    };

    const tokenInfo = sysm[contextName]['token-info'] || {};
    const now = Math.floor(new Date().getTime() / 1000);

    if ((tokenInfo.exp || 0) < now && (tokenInfo.refresh_exp || 0) < now) {
      debugAuthorizationToken(tokenInfo, now, 'client_credentials', now);
      options.data = querystring.stringify({ grant_type: 'client_credentials', scope });
    } else if ((tokenInfo.exp || 0) < now) {
      debugAuthorizationToken(tokenInfo, now, 'refresh_token', now);
      options.data = querystring.stringify({ grant_type: 'refresh_token', refresh_token: tokenInfo.refresh_token });
    } else {
      debugAuthorizationToken(tokenInfo, now, 'system-metadata', now);
      resolve(tokenInfo.access_token);
    }

    if (options.data) {
      urlopen.open(options, (error, response) => {
        if (error) {
          reject(error);
          return;
        }

        response.readAsJSON((error, data) => {
          if (error) {
            reject(error);
            return;
          }

          if (data.error) {
            initAuthorizationToken(contextName);
            reject(new Error(data.error_description));
            return;
          }

          if (data.expires_in && data.refresh_expires_in) {
            sysm[contextName]['token-info'] = {
              access_token: data.access_token,
              exp: now + data.expires_in,
              refresh_token: data.refresh_token,
              refresh_exp: now + data.refresh_expires_in,
            };
          } else if (data.expires_in) {
            new jwt.Decoder(data.refresh_token).decode((error, refresh_claims) => {
              if (error) throw error;

              sysm[contextName]['token-info'] = {
                access_token: data.access_token,
                exp: now + data.expires_in,
                refresh_token: data.refresh_token,
                refresh_exp: refresh_claims.exp,
              };
            });
          } else {
            new jwt.Decoder(data.access_token).decode((error, access_claims) => {
              if (error) throw error;

              new jwt.Decoder(data.refresh_token).decode((error, refresh_claims) => {
                if (error) throw error;

                sysm[contextName]['token-info'] = {
                  access_token: data.access_token,
                  exp: access_claims.exp,
                  refresh_token: data.refresh_token,
                  refresh_exp: refresh_claims.exp,
                };
              });
            });
          }

          resolve(data.access_token);
        });
      });
    }
  });
};

const initAuthorizationToken = (contextName) => {
  sysm[contextName]['token-info'] = {};
};

const debugAuthorizationToken = (tokenInfo, now, tokenSource) => {
  hm.current.set('x-token-source', tokenSource);
  hm.current.set('x-access-token-expires-in', Math.max((tokenInfo.exp || 0) - now, -1));
  hm.current.set('x-refresh-token-expires-in', Math.max((tokenInfo.refresh_exp || 0) - now, -1));
};

module.exports = { setAuthorizationToken, initAuthorizationToken };
