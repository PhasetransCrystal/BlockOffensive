import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'BlockOffensive Wiki',
  tagline: 'Minecraft CS2 风格战术竞技文档',
  favicon: 'img/logo.png',
  // The default VCS strategy uses Docusaurus' hard-coded 2018 timestamp in
  // development. Read the real Git timestamp so the document footer is not
  // misleading while running the local site.
  future: {v4: true, experimental_vcs: 'git-ad-hoc'},
  url: 'https://ssorangecaty.github.io',
  baseUrl: process.env.DOCUSAURUS_BASE_URL || '/BlockOffensive/master/',
  organizationName: 'SSOrangeCATY',
  projectName: 'BlockOffensive',
  trailingSlash: true,
  onBrokenLinks: 'throw',
  i18n: {defaultLocale: 'zh-Hans', locales: ['zh-Hans']},
  markdown: {mermaid: true},
  themes: ['@docusaurus/theme-mermaid'],
  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs',
          include: ['**/*.md'],
          routeBasePath: 'docs',
          sidebarPath: './sidebars.ts',
          editUrl: ({docPath}) => `https://github.com/SSOrangeCATY/BlockOffensive/edit/master/docs/${docPath}`,
          showLastUpdateTime: true,
        },
        blog: false,
        theme: {customCss: './src/css/custom.css'},
      } satisfies Preset.Options,
    ],
  ],
  themeConfig: {
    metadata: [{name: 'keywords', content: 'BlockOffensive, CS2, Minecraft, Forge, FPSMatch'}],
    image: 'img/logo.png',
    colorMode: {respectPrefersColorScheme: true},
    navbar: {
      title: 'BlockOffensive',
      logo: {alt: 'BlockOffensive logo', src: 'img/logo.png'},
      items: [
        {to: '/docs/', label: '文档', position: 'left'},
        {href: 'https://github.com/SSOrangeCATY/FPSMatch', label: 'FPSMatch', position: 'right'},
        {href: 'https://github.com/SSOrangeCATY/BlockOffensive', label: 'GitHub', position: 'right'},
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {title: '文档', items: [{label: 'Wiki 首页', to: '/'}]},
        {
          title: '项目',
          items: [
            {label: 'BlockOffensive', href: 'https://github.com/SSOrangeCATY/BlockOffensive'},
            {label: 'FPSMatch', href: 'https://github.com/SSOrangeCATY/FPSMatch'},
            {label: 'Modrinth', href: 'https://modrinth.com/mod/blockoffensive'},
          ],
        },
      ],
      copyright: 'Copyright (c) ' + new Date().getFullYear() + ' BlockOffensive contributors. Built with Docusaurus.',
    },
    prism: {theme: prismThemes.github, darkTheme: prismThemes.dracula},
  } satisfies Preset.ThemeConfig,
};

export default config;
