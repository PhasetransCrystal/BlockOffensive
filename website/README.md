# BlockOffensive Wiki website

The documentation site is built with Docusaurus. It reads the Markdown files
under `../docs/`; those files are the canonical Wiki sources. The homepage and
theme implementation live under `src/`.

## Requirements

- Node.js 24
- pnpm 10

## Local development

From this directory:

```bash
pnpm install --frozen-lockfile
pnpm start
```

The default local route is
`http://localhost:3000/BlockOffensive/master/`. To serve from `/` instead:

```bash
DOCUSAURUS_BASE_URL=/ pnpm start
```

In PowerShell, set the environment variable with:

```powershell
$env:DOCUSAURUS_BASE_URL = '/'
pnpm start
```

## Checks and production build

```bash
pnpm typecheck
DOCUSAURUS_BASE_URL=/BlockOffensive/master/ pnpm build
pnpm serve
```

The production output is written to `build/`. The GitHub Actions workflow only
checks the site and uploads this directory as an artifact; it does not publish
or push a `gh-pages` branch.
