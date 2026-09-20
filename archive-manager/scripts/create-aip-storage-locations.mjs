#!/usr/bin/env node
/**
 * For each .7z AIP package file in a directory (named like
 * "3816_R00005A-2629-20260806114003-6b8f129b-845e-4d26-bc90-4be9d442c94f.7z",
 * where "R00005" is the record number), creates one new
 * im:ArchivalInformationPackage entity in the archive, linked to that
 * record's existing im:InformationObject (the one the CSV bulk-upload
 * importer already created via bridge:hasOAISCounterpart) via
 * im:hasContentInformation, and sets bridge:hasStorageLocation on the new
 * AIP to a URL built from STORAGE_BASE_URL + the filename.
 *
 * One new AIP per file, not a shared one per record: some records have more
 * than one .7z (e.g. R00005 has two), representing separate physical
 * packages of the same underlying content -- reusing a single AIP entity
 * per record couldn't hold two independent storage locations cleanly, so
 * each file's own AIP shares the record's InformationObject (via
 * hasContentInformation) rather than sharing an AIP.
 *
 * Safe to re-run: before creating anything for a file, it checks whether an
 * AIP with that file's exact storage location URL already exists (from a
 * previous run of this script, or anything else that happened to set one)
 * and skips it rather than creating a duplicate.
 *
 * Requires ARCHIVE_EDIT_PASSWORD in the environment (never hard-code the
 * password in this file). Talks to the running app's own HTTP API only --
 * POST /login for the session cookie, then the REST endpoints
 * ArchiveApiController exposes (POST /api/entities, POST
 * /api/entities/{id}/relationships) for every write, and the /sparql
 * console (a plain SELECT, scraped from its HTML table -- there's no JSON
 * SPARQL endpoint in this app) to look up each record's existing
 * InformationObject.
 *
 * Usage:
 *   ARCHIVE_EDIT_PASSWORD=... node scripts/create-aip-storage-locations.mjs [options]
 *
 * Options:
 *   --base-url <url>       Archive Manager base URL (default: http://www.oais.info:9090)
 *   --dir <path>           Directory of .7z files (default: the NAM Phase 3 test directory below)
 *   --storage-base <url>   Prefix the stored location URL is built from (default: http://www.oais.info/NAM/)
 *   --limit <n>            Only process the first n files (for a small test run)
 *   --dry-run              Look everything up and print what would happen, but make no writes
 */

import { readdir } from 'node:fs/promises';

const args = parseArgs(process.argv.slice(2));
const BASE_URL = args['base-url'] ?? 'http://www.oais.info:9090';
const DIR = args['dir'] ?? 'C:\\Users\\david\\Dropbox\\Giaretta Associates\\GA-bids\\Maldives National Archives\\PHASE 3\\DR_David_Testes';
const STORAGE_BASE = args['storage-base'] ?? 'http://www.oais.info/NAM/';
const LIMIT = args['limit'] ? parseInt(args['limit'], 10) : Infinity;
const DRY_RUN = Boolean(args['dry-run']);

const PASSWORD = process.env.ARCHIVE_EDIT_PASSWORD;
if (!DRY_RUN && !PASSWORD) {
    console.error('Set ARCHIVE_EDIT_PASSWORD in the environment first (not needed for --dry-run).');
    process.exit(1);
}

const FILENAME_PATTERN = /^\d+_(R\d+)[A-Z]-\d+-\d{14}-[0-9a-f-]+\.7z$/i;

let sessionCookie = null;

async function login() {
    const res = await fetch(`${BASE_URL}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ password: PASSWORD }),
        redirect: 'manual',
    });
    const setCookie = res.headers.get('set-cookie');
    if (!setCookie) {
        throw new Error(`Login did not return a session cookie (status ${res.status}) -- check the password and BASE_URL.`);
    }
    sessionCookie = setCookie.split(';')[0];
}

function authHeaders(extra = {}) {
    return { ...extra, ...(sessionCookie ? { Cookie: sessionCookie } : {}) };
}

/** Runs a SPARQL SELECT via the /sparql console and scrapes its results table (no JSON endpoint exists). */
async function sparqlSelect(queryText, columns) {
    const res = await fetch(`${BASE_URL}/sparql`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
        body: new URLSearchParams({ queryText }),
    });
    const html = await res.text();
    const bodyMatch = html.match(/<tbody>([\s\S]*?)<\/tbody>/);
    if (!bodyMatch) {
        return [];
    }
    const cells = [...bodyMatch[1].matchAll(/<td>([^<]*)<\/td>/g)].map((m) => m[1]);
    const rows = [];
    for (let i = 0; i < cells.length; i += columns.length) {
        const row = {};
        columns.forEach((col, j) => { row[col] = cells[i + j] ?? ''; });
        rows.push(row);
    }
    return rows;
}

/** True if some AIP already has this exact storage location -- so a re-run (or a file someone already handled by hand) doesn't create a duplicate. */
async function storageLocationAlreadyExists(storageUrl) {
    const query = `PREFIX bridge: <https://oais.info/bridge#>
SELECT ?aip WHERE {
  ?aip bridge:hasStorageLocation <${storageUrl}> .
}`;
    const rows = await sparqlSelect(query, ['aip']);
    return rows.length > 0;
}

/** The record's existing InformationObject IRI (created by the CSV bulk-upload importer), or null if not found. */
async function findInfoObject(recordNumber) {
    const query = `PREFIX nam: <https://oais.info/nam#>
PREFIX bridge: <https://oais.info/bridge#>
SELECT ?infoObject WHERE {
  ?r nam:recordNumber "${recordNumber}" .
  ?r bridge:hasOAISCounterpart ?infoObject .
}`;
    const rows = await sparqlSelect(query, ['infoObject']);
    return rows.length > 0 ? rows[0].infoObject : null;
}

async function createAip() {
    const res = await fetch(`${BASE_URL}/api/entities`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ classIri: 'http://ontology.oais.org/im/ArchivalInformationPackage' }),
    });
    if (!res.ok) {
        throw new Error(`Create AIP failed: ${res.status} ${await res.text()}`);
    }
    return res.json();
}

async function addRelationship(entityId, customProperty, customTarget) {
    const res = await fetch(`${BASE_URL}/api/entities/${entityId}/relationships`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ customProperty, customTarget }),
    });
    if (!res.ok) {
        throw new Error(`Add relationship (${customProperty}) failed: ${res.status} ${await res.text()}`);
    }
}

function parseArgs(argv) {
    const out = {};
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (a.startsWith('--')) {
            const key = a.slice(2);
            const next = argv[i + 1];
            if (next !== undefined && !next.startsWith('--')) {
                out[key] = next;
                i++;
            } else {
                out[key] = true;
            }
        }
    }
    return out;
}

async function main() {
    const allFiles = (await readdir(DIR)).filter((f) => f.toLowerCase().endsWith('.7z'));
    const files = allFiles.slice(0, LIMIT);
    console.log(`${allFiles.length} .7z files found` + (files.length < allFiles.length ? `, processing first ${files.length} (--limit)` : '') + '.');
    console.log(DRY_RUN ? '--dry-run: no writes will be made.\n' : '');

    if (!DRY_RUN) {
        await login();
    }

    let ok = 0;
    let skipped = 0;
    let failed = 0;

    for (const filename of files) {
        const match = filename.match(FILENAME_PATTERN);
        if (!match) {
            console.warn(`SKIP  ${filename}  (doesn't match the expected naming pattern)`);
            skipped++;
            continue;
        }
        const recordNumber = match[1];
        const storageUrl = STORAGE_BASE + filename;

        try {
            if (await storageLocationAlreadyExists(storageUrl)) {
                console.log(`SKIP  ${filename}  (an AIP with this exact storage location already exists)`);
                skipped++;
                continue;
            }

            const infoObject = await findInfoObject(recordNumber);
            if (!infoObject) {
                console.warn(`SKIP  ${filename}  (no record/InformationObject found for ${recordNumber})`);
                skipped++;
                continue;
            }

            if (DRY_RUN) {
                console.log(`WOULD CREATE  ${filename}  ->  record ${recordNumber}  (infoObject ${infoObject})  storageLocation=${storageUrl}`);
                ok++;
                continue;
            }

            const aip = await createAip();
            await addRelationship(aip.id, 'im:hasContentInformation', infoObject);
            await addRelationship(aip.id, 'bridge:hasStorageLocation', storageUrl);
            console.log(`OK    ${filename}  ->  record ${recordNumber}  ->  new AIP ${aip.iri}`);
            ok++;
        } catch (err) {
            console.error(`FAIL  ${filename}  ->  record ${recordNumber}: ${err.message}`);
            failed++;
        }
    }

    console.log(`\nDone. ${ok} ${DRY_RUN ? 'would be created' : 'created'}, ${skipped} skipped, ${failed} failed.`);
    if (failed > 0) {
        process.exitCode = 1;
    }
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
