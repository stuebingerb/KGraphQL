// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
    site: "https://stuebingerb.github.io/KGraphQL/",
    integrations: [starlight({
        title: 'KGraphQL',
        social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/stuebingerb/KGraphQL' }],
        sidebar: [
            { slug: 'installation' },
            { label: 'Plugins', items: [{ autogenerate: { directory: 'plugins' }}]},
            { label: 'Tutorials', items: [{ autogenerate: { directory: 'tutorials' }}]},
            { label: 'Reference', items: [{ autogenerate: { directory: 'reference' }}]},
            { slug: 'examples' },
        ]}
    )],
});
