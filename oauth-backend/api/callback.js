const { required, readState, sealSession, github } = require('./_lib');

module.exports = async (req, res) => {
  if (req.method !== 'GET') return res.status(405).send('Method Not Allowed');
  let returnTo = 'https://g134t.github.io/G134Office/';
  try {
    const state = readState(req.query.state);
    returnTo = state.returnTo;
    if (!req.query.code) throw new Error('GitHub did not return an authorization code');

    const callback = `https://${req.headers.host}/api/callback`;
    const tokenResponse = await fetch('https://github.com/login/oauth/access_token', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({
        client_id: required('GITHUB_CLIENT_ID'),
        client_secret: required('GITHUB_CLIENT_SECRET'),
        code: req.query.code,
        redirect_uri: callback
      })
    });
    const tokenData = await tokenResponse.json();
    if (!tokenResponse.ok || !tokenData.access_token) throw new Error(tokenData.error_description || tokenData.error || 'Token exchange failed');

    const user = await github('/user', tokenData.access_token);
    const ttl = Math.min(Number(tokenData.expires_in || 28800), 28800) * 1000;
    const session = sealSession({ accessToken: tokenData.access_token, login: user.login, exp: Date.now() + ttl });
    res.redirect(302, `${returnTo}#github_session=${encodeURIComponent(session)}`);
  } catch (e) {
    res.redirect(302, `${returnTo}#github_error=${encodeURIComponent(e.message || 'GitHub authorization failed')}`);
  }
};
