const { cors, sendJson, readBearer, github } = require('./_lib');

module.exports = async (req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'GET') return sendJson(res, 405, { error: 'Method Not Allowed' });
  try {
    const session = readBearer(req);
    const repos = await github('/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member', session.accessToken);
    const items = repos.map(r => ({
      id: r.id,
      name: r.name,
      full_name: r.full_name,
      private: r.private,
      language: r.language,
      html_url: r.html_url,
      default_branch: r.default_branch,
      permissions: r.permissions || null
    }));
    sendJson(res, 200, { items });
  } catch (e) {
    sendJson(res, e.status || 401, { error: e.message });
  }
};
