import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'BlockOffensive Wiki',
  tagline: 'Minecraft CS2 风格战术竞技文档',
  favicon: 'img/logo.png',
  future: {v4: true},
  url: 'https://phasetranscrystal.github.io',
  baseUrl: process.env.DOCUSAURUS_BASE_URL || '/BlockOffensive/master/',
  organizationName: 'PhasetransCrystal',
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
          editUrl: 'https://github.com/PhasetransCrystal/BlockOffensive/tree/master/',
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
        {href: 'https://github.com/PhasetransCrystal/FPSMatch', label: 'FPSMatch', position: 'right'},
        {href: 'https://github.com/PhasetransCrystal/BlockOffensive', label: 'GitHub', position: 'right'},
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {title: '文档', items: [{label: 'Wiki 首页', to: '/'}]},
        {
          title: '项目',
          items: [
            {label: 'BlockOffensive', href: 'https://github.com/PhasetransCrystal/BlockOffensive'},
            {label: 'FPSMatch', href: 'https://github.com/PhasetransCrystal/FPSMatch'},
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
