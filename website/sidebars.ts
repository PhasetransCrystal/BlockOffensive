import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    'README',
    'installation',
    'admin',
    {
      type: 'category',
      label: '战术手册',
      items: ['player', 'game-modes'],
    },
    {
      type: 'category',
      label: '地图与开发',
      items: [
        'mapper',
        {
          type: 'category',
          label: '地图制作教程',
          items: [
            'mapper/getting-started',
            'mapper/create-map',
            'mapper/teams-and-spawns',
            'mapper/regions',
            'mapper/shop',
            'mapper/settings',
            'mapper/tools',
            'mapper/kits',
            'mapper/resources',
            'mapper/room-management',
            'mapper/persistence',
            'mapper/importing',
          ],
        },
        'developer',
      ],
    },
    {
      type: 'category',
      label: '参考与排错',
      items: ['reference/commands', 'reference/configuration', 'developer-guide', 'api', 'compatibility', 'troubleshooting', 'changelog'],
    },
  ],
};

export default sidebars;

