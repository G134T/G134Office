const { cors, sendJson, readBearer, github } = require('./_lib');

module.exports = async (req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  try {
    const session = readBearer(req);
    if (req.method === 'GET') {
      const gists = await github('/gists?per_page=50', session.accessToken);
      return sendJson(res, 200, { items: gists.map(g => ({
        id: g.id,
        description: g.description,
        public: g.public,
        html_url: g.html_url,
        updated_at: g.updated_at
      })) });
    }
    if (req.method === 'POST') {
      const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : (req.body || {});
      const filename = String(body.filename || 'document.html').replace(/[\\/:*?"<>|]/g, '_').slice(0, 120);
      const content = String(body.content || '');
      const description = String(body.description || 'G134Office document').slice(0, 200);
      const gist = await github('/gists', session.accessToken, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description, public: false, files: { [filename]: { content: content || ' ' } } })
      });
      return sendJson(res, 201, { id: gist.id, html_url: gist.html_url });
    }
    return sendJson(res, 405, { error: 'Method Not Allowed' });
  } catch (e) {
    sendJson(res, e.status || 400, { error: e.message });
  }
};
