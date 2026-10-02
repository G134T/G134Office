# G134Office GitHub OAuth backend

This directory is a small serverless backend for the GitHub account button in G134Office Lite.

## GitHub App

Create a GitHub App with:

- Homepage URL: `https://g134t.github.io/G134Office/`
- Callback URL: `https://<your-auth-domain>/api/callback`
- User authorization enabled
- Account permission `Gists`: Read and write (for Gist integration)
- Repository access/permissions sufficient for the repositories you want G134Office to display or edit later

Do not put the GitHub client secret into `cpp/web`, GitHub Pages, JavaScript, commits, issues, or Actions logs.

## Vercel

Deploy this `oauth-backend` directory as the Vercel project root.

Environment variables:

- `GITHUB_CLIENT_ID` - GitHub App client ID
- `GITHUB_CLIENT_SECRET` - GitHub App client secret
- `APP_ORIGIN=https://g134t.github.io`
- `APP_PATH_PREFIX=/G134Office/`

After deployment, set this GitHub repository Actions variable:

- `G134_AUTH_BASE_URL=https://<your-auth-domain>`

Then rerun the `Lite in browser` GitHub Actions workflow or push a change under `cpp/web`.

## Security model

The GitHub access token is exchanged only on the server. The browser receives an AES-GCM encrypted, short-lived opaque session value and keeps it in `sessionStorage`. The GitHub client secret never reaches GitHub Pages.
