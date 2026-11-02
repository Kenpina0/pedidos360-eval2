import type { AuthProviderProps } from 'react-oidc-context';

/**
 * Configuración OAuth 2.0 / OpenID Connect contra AWS Cognito.
 *
 * - `authority` : dominio del Hosted UI -> https://<dominio>.auth.<region>.amazoncognito.com
 * - `issuer`    : issuer del User Pool  -> https://cognito-idp.<region>.amazonaws.com/<poolId>
 *
 * Se declara `metadata` de forma explícita para que el SPA no dependa del
 * documento de descubrimiento (/.well-known/openid-configuration), que en
 * dominios recién creados puede tardar en propagarse por CloudFront.
 */
const authority = import.meta.env.VITE_COGNITO_AUTHORITY as string;
const issuer = (import.meta.env.VITE_COGNITO_ISSUER as string) || authority;

export const authConfig: AuthProviderProps = {
  authority,
  metadata: ({
    issuer,
    authorization_endpoint: `${authority}/oauth2/authorize`,
    token_endpoint: `${authority}/oauth2/token`,
    userinfo_endpoint: `${authority}/oauth2/userInfo`,
    end_session_endpoint: `${authority}/logout`,
    jwks_uri: `${issuer}/.well-known/jwks.json`,
    response_types_supported: ['code'],
    response_modes_supported: ['query'],
    grant_types_supported: ['authorization_code', 'refresh_token'],
    subject_types_supported: ['public'],
    id_token_signing_alg_values_supported: ['RS256'],
    scopes_supported: ['openid', 'email', 'profile'],
    token_endpoint_auth_methods_supported: ['none'],
    claims_supported: [
      'sub',
      'iss',
      'aud',
      'exp',
      'iat',
      'email',
      'cognito:groups',
      'cognito:username',
    ],
  } as any),
  client_id: import.meta.env.VITE_COGNITO_CLIENT_ID,
  redirect_uri: import.meta.env.VITE_COGNITO_REDIRECT_URI || window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid email profile',
  automaticSilentRenew: true,
  loadUserInfo: true,
  onSigninCallback: () => {
    // Limpia el code/state de la URL después del callback de Cognito.
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
