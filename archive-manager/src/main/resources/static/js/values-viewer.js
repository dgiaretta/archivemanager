/*
 * Prints a Data Object's data values in the page, a page at a time: each
 * page is fetched from /api/data-objects/{id}/values.json, which decodes the
 * data on the server through its Representation Information once and then
 * serves pages of what it decoded, so a large file never reaches the browser
 * whole.
 */
(function () {
    const root = document.querySelector('.values-viewer');
    if (!root) {
        return;
    }
    const q = name => root.querySelector('[data-values-' + name + ']');
    const show = q('show'), panel = q('panel'), error = q('error'), pager = q('pager'), table = q('table');
    const rowsBody = q('rows'), pageInput = q('page'), pagesLabel = q('pages'), status = q('status');
    const language = q('language'), size = q('size');
    let page = 1, pages = 1, loading = false;

    show.addEventListener('click', () => load(1));
    q('first').addEventListener('click', () => load(1));
    q('previous').addEventListener('click', () => load(page - 1));
    q('next').addEventListener('click', () => load(page + 1));
    q('last').addEventListener('click', () => load(pages));
    pageInput.addEventListener('change', () => load(parseInt(pageInput.value, 10) || 1));
    size.addEventListener('change', () => { if (!panel.hidden) { load(1); } });
    if (language) {
        language.addEventListener('change', () => { if (!panel.hidden) { load(1); } });
    }

    function load(wanted) {
        if (loading) {
            return;
        }
        loading = true;
        const target = Math.max(1, Math.min(wanted, pages || 1)) || 1;
        const params = new URLSearchParams({page: String(panel.hidden ? 1 : target), size: size.value});
        if (language) {
            params.set('language', language.value);
        }
        show.disabled = true;
        show.textContent = 'Decoding…';
        status.textContent = 'Loading…';
        fetch(root.dataset.values + '?' + params)
            .then(r => r.ok ? r.json() : r.text().then(t => { throw new Error(t); }))
            .then(render)
            .catch(e => render({error: e.message}))
            .finally(() => {
                loading = false;
                show.disabled = false;
                show.textContent = 'Show the values';
            });
    }

    function render(data) {
        panel.hidden = false;
        error.hidden = !data.error;
        error.textContent = data.error || '';
        if (data.error) {
            pager.hidden = true;
            table.hidden = true;
            return;
        }
        page = data.page;
        pages = data.totalPages;
        pageInput.value = page;
        pageInput.max = pages;
        pagesLabel.textContent = pages.toLocaleString();
        const first = (page - 1) * data.pageSize + 1;
        const last = first + data.rows.length - 1;
        status.textContent = 'elements ' + first.toLocaleString() + '–' + last.toLocaleString() + ' of '
            + data.totalRows.toLocaleString() + (data.truncated ? ' (the first ones only: there are more)' : '')
            + (data.language ? ', decoded with its ' + data.language + ' description' : '')
            + (data.trailingBytes ? '; ' + data.trailingBytes + ' bytes at the end aren’t described' : '');
        q('first').disabled = q('previous').disabled = page <= 1;
        q('next').disabled = q('last').disabled = page >= pages;
        pager.hidden = false;
        table.hidden = false;
        rowsBody.replaceChildren(...data.rows.map(row));
    }

    function row(r) {
        const tr = document.createElement('tr');
        const name = document.createElement('td');
        name.style.paddingLeft = (r.depth * 1.25 + 0.5) + 'em';
        const code = document.createElement('code');
        code.textContent = r.name;
        name.append(code);
        tr.append(name, cell(r.kind), cell(r.value), cell(r.meaning), cell(r.byteRange, 'muted'));
        return tr;
    }

    function cell(text, className) {
        const td = document.createElement('td');
        td.textContent = text || '';
        if (className) {
            td.className = className;
        }
        return td;
    }
})();
