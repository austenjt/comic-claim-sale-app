#!/usr/bin/env node
// Fetches every comic + set from the live API and writes a per-route sitemap to src/sitemap.xml.
// Run before `npm run build` to keep search engines current. Safe to skip if the API is unreachable —
// the existing static sitemap (with the main routes) is left in place.

import { writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ORIGIN = 'https://lightningcomics.rocks';
const API_BASE = process.env.API_BASE ?? 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';

const STATIC_ROUTES = [
  { path: '/',              priority: '1.0', changefreq: 'daily'   },
  { path: '/dashboard',     priority: '1.0', changefreq: 'daily'   },
  { path: '/sales',         priority: '0.9', changefreq: 'daily'   },
  { path: '/trade',         priority: '0.8', changefreq: 'weekly'  },
  { path: '/documentation', priority: '0.8', changefreq: 'monthly' },
  { path: '/contact',       priority: '0.6', changefreq: 'monthly' },
];

async function fetchPaged() {
  const items = [];
  let page = 1;
  while (true) {
    const res = await fetch(`${API_BASE}/comics?pageNumber=${page}&sort=oldest-first`);
    if (!res.ok) throw new Error(`Bad response: ${res.status}`);
    const data = await res.json();
    for (const c of data.items ?? []) items.push(c);
    if (page >= (data.totalPages ?? 0)) break;
    page++;
  }
  return items;
}

function buildXml(comicEntries) {
  const urls = [
    ...STATIC_ROUTES.map(r => ({ loc: `${ORIGIN}${r.path}`, priority: r.priority, changefreq: r.changefreq })),
    ...comicEntries,
  ];
  const body = urls.map(u =>
    `  <url>\n    <loc>${u.loc}</loc>\n    <changefreq>${u.changefreq}</changefreq>\n    <priority>${u.priority}</priority>\n  </url>`
  ).join('\n');
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${body}\n</urlset>\n`;
}

async function main() {
  try {
    const comics = await fetchPaged();
    const entries = comics
      .filter(c => c.id != null)
      .map(c => ({
        loc: `${ORIGIN}/${c.docType === 'SET' ? 'set' : 'detail'}/${c.id}`,
        changefreq: 'weekly',
        priority: '0.7',
      }));
    const xml = buildXml(entries);
    await writeFile(resolve(ROOT, 'src/sitemap.xml'), xml, 'utf8');
    console.log(`sitemap.xml: wrote ${STATIC_ROUTES.length} static + ${entries.length} comic URLs`);
  } catch (err) {
    console.warn(`sitemap generation skipped: ${err.message}`);
    process.exit(0);
  }
}

main();
