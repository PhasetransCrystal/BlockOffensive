import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import type {ReactNode} from 'react';
import {useEffect, useState} from 'react';

import styles from './index.module.css';

const routes = [
  {
    key: 'player',
    code: '01 / PLAYER',
    shortLabel: 'PLAY',
    title: '进入比赛',
    description: '从地图房间、选队和准备开始，了解购买阶段、C4 目标、死斗重生与旁观流程。',
    href: '/docs/player',
    action: '打开玩家手册',
  },
  {
    key: 'mapper',
    code: '02 / MAPPER',
    shortLabel: 'BUILD',
    title: '制作地图',
    description: '把 Minecraft 建筑接入 FPSMatch：边界、T/CT 出生点、A/B 爆破区、商店与房间展示。',
    href: '/docs/mapper',
    action: '打开制图手册',
  },
  {
    key: 'operator',
    code: '03 / DEV OPS',
    shortLabel: 'EXTEND',
    title: '配置与扩展',
    description: '查看模式依赖、服务器排错路径和 CSGameMap、回合规则、网络包与 HUD 的开发边界。',
    href: '/docs/game-modes',
    action: '查看运维入口',
  },
];

const modes = [
  {id: 'CS', label: 'Competitive demolition', detail: 'T / CT · C4 objective', state: 'ONLINE'},
  {id: 'CSDM', label: 'Deathmatch', detail: 'FFA / TEAM · respawn', state: 'ONLINE'},
];

export default function Home(): ReactNode {
  const [activeRole, setActiveRole] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(true);

  useEffect(() => {
    const saved = window.localStorage.getItem('blockoffensive-wiki-role');
    if (routes.some((route) => route.key === saved)) {
      setActiveRole(saved);
      setPickerOpen(false);
    }
  }, []);

  useEffect(() => {
    if (!pickerOpen) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setPickerOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [pickerOpen]);

  const chooseRole = (key: string) => {
    setActiveRole(key);
    window.localStorage.setItem('blockoffensive-wiki-role', key);
    setPickerOpen(false);
  };

  const selected = routes.find((route) => route.key === activeRole);

  return (
    <Layout
      title="BlockOffensive Wiki"
      description="CS2 风格的 Minecraft Forge 战术竞技文档。"
    >
      <main className={styles.main}>
        <section className={styles.hero}>
          <div className={styles.heroGrid}>
            <div className={styles.heroCopy}>
              <div className={styles.metaLine}>
                <span className={styles.signalDot} aria-hidden="true" />
                <span>OPERATOR DOSSIER</span>
                <span className={styles.metaRule} />
                <span>BO / 1.3.0-SNAPSHOT</span>
              </div>
              <h1>Block<span>Offensive</span></h1>
              <p className={styles.heroLead}>
                Minecraft Forge 上的回合制战术竞技。以 FPSMatch 为骨架，加入 CS2 式的经济、爆破目标、死斗和比赛演出。
              </p>
            <div className={styles.heroActions}>
                <button className={styles.primaryAction} type="button" onClick={() => setPickerOpen(true)}>
                  选择阅读身份 <span aria-hidden="true">→</span>
                </button>
                <Link className={styles.textAction} to={selected ? selected.href : '/docs/'}>
                  {selected ? '继续：' + selected.title : '浏览总览'} <span aria-hidden="true">→</span>
                </Link>
              </div>
              <div className={styles.specLine}>
                <span>MINECRAFT <b>1.20.1</b></span>
                <span>FORGE <b>47.4.10</b></span>
                <span>JAVA <b>17</b></span>
              </div>
            </div>

            <aside className={styles.readout} aria-label="当前模式状态">
              <div className={styles.readoutTop}>
                <span>LIVE MATCH INDEX</span>
                <span className={styles.readoutTime}>SYNC / READY</span>
              </div>
              <div className={styles.emblemWrap}>
                <img src="img/logo.png" alt="BlockOffensive 标志" className={styles.emblem} />
                <div>
                  <span className={styles.kicker}>FRAMEWORK</span>
                  <strong>FPSMatch</strong>
                  <span className={styles.muted}>round infrastructure / team state</span>
                </div>
              </div>
              <div className={styles.modeList}>
                {modes.map((mode) => (
                  <div className={styles.modeRow} key={mode.id}>
                    <span className={styles.modeId}>{mode.id}</span>
                    <span className={styles.modeInfo}>
                      <strong>{mode.label}</strong>
                      <small>{mode.detail}</small>
                    </span>
                    <span className={styles.modeState}><i />{mode.state}</span>
                  </div>
                ))}
              </div>
              <div className={styles.readoutFoot}>
                <span>REQUIRED CLIENT</span>
                <b>MODERN UI 3.12.0.1</b>
              </div>
            </aside>
          </div>
          <div className={styles.heroIndex} aria-hidden="true">CS / 01</div>
        </section>

        <section className={styles.routeSection} aria-labelledby="route-heading">
          <div className={styles.sectionHeader}>
            <div>
              <span className={styles.sectionKicker}>FIELD MANUAL / START HERE</span>
              <h2 id="route-heading">选择你的任务</h2>
            </div>
            <p>FPSMatch 的三条工作路线，针对 BlockOffensive 的实际模式与服务器流程。</p>
          </div>
          <div className={styles.routeGrid}>
            {routes.map((route) => (
              <article className={styles.routeItem} key={route.code}>
                <div className={styles.routeTop}><span>{route.code}</span><span aria-hidden="true">↗</span></div>
                <h3>{route.title}</h3>
                <p>{route.description}</p>
                <Link to={route.href} className={styles.routeLink} onClick={() => chooseRole(route.key)}>{route.action} <span aria-hidden="true">→</span></Link>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.protocolSection} aria-labelledby="protocol-heading">
          <div className={styles.protocolLabel}>PROTOCOL / 02</div>
          <div className={styles.protocolBody}>
            <h2 id="protocol-heading">每一回合，<br /><em>都有清晰的目标。</em></h2>
            <p>购买阶段决定资源，路线与信息决定空间，C4 决定最后的计时。BlockOffensive 将这些状态交给服务器权威管理，再用 HUD、击杀反馈、旁观镜头和 MVP 演出把比赛读出来。</p>
            <Link className={styles.protocolLink} to="/docs/game-modes">查看模式协议 <span aria-hidden="true">↗</span></Link>
          </div>
          <div className={styles.protocolStamp}>
            <span>ROUND</span>
            <strong>13</strong>
            <span>OBJECTIVE / C4</span>
          </div>
        </section>

        <section className={styles.sourceSection}>
          <div>
            <span className={styles.sectionKicker}>OPEN SOURCE / GPL-3.0</span>
            <h2>准备好部署下一场比赛。</h2>
          </div>
          <Link className={styles.sourceLink} href="https://github.com/PhasetransCrystal/BlockOffensive">
            查看 GitHub 源码 <span aria-hidden="true">↗</span>
          </Link>
        </section>

        {pickerOpen && (
          <div className={styles.pickerBackdrop} role="presentation" onMouseDown={() => setPickerOpen(false)}>
            <section className={styles.picker} role="dialog" aria-modal="true" aria-labelledby="picker-title" onMouseDown={(event) => event.stopPropagation()}>
              <button className={styles.closePicker} type="button" aria-label="关闭身份选择" onClick={() => setPickerOpen(false)} autoFocus>
                <span aria-hidden="true">×</span>
              </button>
              <div className={styles.pickerHeader}>
                <p className={styles.eyebrow}>WELCOME / ROUTE SELECT</p>
                <span className={styles.pickerCode}>BO-WIKI / 00</span>
              </div>
              <h2 id="picker-title">你准备如何使用 BlockOffensive？</h2>
              <p className={styles.pickerIntro}>选择一个身份，Wiki 会把最相关的章节放在第一位。之后可以随时切换。</p>
              <div className={styles.pickerOptions}>
                {routes.map((route) => (
                  <Link className={styles.pickerOption} key={route.key} to={route.href} onClick={() => chooseRole(route.key)}>
                    <span className={styles.pickerOptionIndex}>{route.code.slice(0, 2)}</span>
                    <span className={styles.pickerOptionBody}>
                      <strong>{route.title}</strong>
                      <span>{route.shortLabel} / {route.description}</span>
                    </span>
                    <span className={styles.pickerOptionArrow} aria-hidden="true">↗</span>
                  </Link>
                ))}
              </div>
              <button className={styles.dismissPicker} type="button" onClick={() => setPickerOpen(false)}>
                不选择身份，先看总览
              </button>
            </section>
          </div>
        )}
      </main>
    </Layout>
  );
}

