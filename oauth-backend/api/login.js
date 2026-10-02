const { required, safeReturnTo, makeState } = require('./_lib');

module.exports = async (req, res) => {
  if (req.method !== 'GET') return res.status(405).send('Method Not Allowed');
  try {
    const clientId = required('GITHUB_CLIENT_ID');
    const returnTo = safeReturnTo(req.query.return_to);
    const state = makeState(returnTo);
    const callback = `https://${req.headers.host}/api/callback`;
    const url = new URL('https://github.com/login/oauth/authorize');
    url.searchParams.set('client_id', clientId);
    url.searchParams.set('redirect_uri', callback);
    url.searchParams.set('state', state);
    url.searchParams.set('prompt', 'select_account');
    res.redirect(302, url.toString());
  } catch (e) {
    res.status(500).send(e.message);
  }
};
