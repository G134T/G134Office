const { cors, sendJson, readBearer, github } = require('./_lib');

module.exports = async (req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'GET') return sendJson(res, 405, { error: 'Method Not Allowed' });
  try {
    const session = readBearer(req);
    const user = await github('/user', session.accessToken);
    sendJson(res, 200, {
      login: user.login,
      name: user.name,
      email: user.email,
      avatar_url: user.avatar_url,
      html_url: user.html_url
    });
  } catch (e) {
    sendJson(res, e.status || 401, { error: e.message });
  }
};
