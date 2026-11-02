import type { AuthProviderProps } from 'react-oidc-context';

/**
 * Configuración OAuth 2.0 / OpenID Connect contra AWS Cognito.
 * El "authority" es el issuer del User Pool:
 * https://cognito-idp.<region>.amazonaws.com/<userPoolId>
 */
export const authConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_COGNITO_AUTHORITY,
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
