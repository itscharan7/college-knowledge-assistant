(function () {
    'use strict';
    const h = App.h;
    const $ = function (s) {
        return document.querySelector(s);
    };

    const body = $('#docBody');
    const emptyEl = $('#docEmpty');
    const modal = function (id) {
        return bootstrap.Modal.getOrCreateInstance($(id));
    };
    let docs = [];
    let pollTimer = null;
    let editingId = null;
    let replacingId = null;
    let chosenFile = null;

    function statusBadge(status) {
        const cls = {READY: 'success', FAILED: 'danger', PROCESSING: 'warning', UPLOADED: 'secondary'}[status] ||
            'secondary';
        return h('span', {class: 'badge text-bg-' + cls, text: status});
    }

    /* ---------- list + polling ---------- */
    async function load() {
        try {
            const q = encodeURIComponent($('#docSearch').value.trim());
            const st = encodeURIComponent($('#docStatus').value);
            docs = await App.api('/api/admin/documents?q=' + q + (st ? '&status=' + st : ''));
            render();
            schedulePoll();
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    function schedulePoll() {
        clearTimeout(pollTimer);
        const active = docs.some(function (d) {
            return d.status === 'UPLOADED' || d.status === 'PROCESSING';
        });
        if (active) {
            pollTimer = setTimeout(load, 3000);
        }
    }

    function render() {
        body.replaceChildren();
        emptyEl.classList.toggle('d-none', docs.length > 0);
        docs.forEach(function (d) {
            const busy = d.status === 'PROCESSING';
            const retry = d.status === 'FAILED';
            body.append(h('tr', {},
                h('td', {class: 'truncate-cell', title: d.originalFilename},
                    h('div', {class: 'fw-semibold', text: d.title}),
                    h('div', {class: 'small text-secondary', text: d.originalFilename + ' · v' + d.version + ' · ' +
                            App.fmtSize(d.fileSize)})),
                h('td', {text: d.category}),
                h('td', {}, h('span', {class: 'badge text-bg-light border', text: d.audience})),
                h('td', {text: d.pageCount || '—'}),
                h('td', {text: d.chunkCount || '—'}),
                h('td', {}, statusBadge(d.status)),
                h('td', {class: 'small text-secondary', text: App.fmtDate(d.createdAt)}),
                h('td', {class: 'text-end text-nowrap'},
                    button('Details', 'outline-secondary', function () {
                        showDetails(d.id);
                    }),
                    button('Edit', 'outline-secondary', function () {
                        openEdit(d);
                    }, busy),
                    button('Replace', 'outline-secondary', function () {
                        replacingId = d.id;
                        $('#replaceInput').click();
                    }, busy),
                    button(retry ? 'Retry' : 'Reindex', retry ? 'warning' : 'outline-secondary', function () {
                        act('/api/admin/documents/' + d.id + '/reindex', 'POST', 'Processing started.');
                    }, busy),
                    button('Delete', 'outline-danger', function () {
                        if (window.confirm('Delete "' + d.title + '" and all its indexed content?')) {
                            act('/api/admin/documents/' + d.id, 'DELETE', 'Document deleted.');
                        }
                    }, busy))));
        });
    }

    function button(label, kind, onclick, disabled) {
        return h('button', {type: 'button', class: 'btn btn-sm btn-' + kind + ' ms-1', text: label,
            onclick: onclick, disabled: disabled});
    }

    async function act(url, method, okMessage) {
        try {
            await App.api(url, {method: method});
            App.toast(okMessage, 'success');
            load();
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    /* ---------- details ---------- */
    async function showDetails(id) {
        try {
            const d = await App.api('/api/admin/documents/' + id);
            const s = await App.api('/api/admin/documents/' + id + '/status');
            const dl = $('#detailsList');
            dl.replaceChildren();
            [['Title', d.title], ['File', d.originalFilename], ['Category', d.category], ['Visible to', d.audience],
                ['Status', d.status], ['Pages', d.pageCount], ['Chunks', d.chunkCount], ['Version', d.version],
                ['Size', App.fmtSize(d.fileSize)], ['Uploaded', App.fmtDate(d.createdAt)],
                ['Description', d.description || '—']].forEach(function (kv) {
                dl.append(h('dt', {class: 'col-sm-3', text: kv[0]}), h('dd', {class: 'col-sm-9', text: String(kv[1])}));
            });
            const err = $('#detailsError');
            err.textContent = s.processingError || '';
            err.classList.toggle('d-none', !s.processingError);
            const logs = $('#detailsLogs');
            logs.replaceChildren();
            s.logs.forEach(function (l) {
                const cls = l.status === 'ERROR' ? 'text-danger' : (l.status === 'OK' ? 'text-success' : 'text-secondary');
                logs.append(h('li', {class: 'list-group-item d-flex gap-2'},
                    h('span', {class: 'text-secondary', text: App.fmtDate(l.createdAt)}),
                    h('strong', {class: cls, text: l.stage}),
                    h('span', {text: l.message || ''})));
            });
            modal('#detailsModal').show();
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    /* ---------- edit ---------- */
    function openEdit(d) {
        editingId = d.id;
        $('#edTitle').value = d.title;
        $('#edCategory').value = d.category;
        $('#edAudience').value = d.audience;
        $('#edDesc').value = d.description || '';
        modal('#editModal').show();
    }

    $('#editForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        try {
            await App.api('/api/admin/documents/' + editingId, {
                method: 'PUT',
                json: {title: $('#edTitle').value, category: $('#edCategory').value,
                    audience: $('#edAudience').value, description: $('#edDesc').value}
            });
            modal('#editModal').hide();
            App.toast('Document updated.', 'success');
            load();
        } catch (err) {
            App.toast(err.message, 'danger');
        }
    });

    /* ---------- replace ---------- */
    $('#replaceInput').addEventListener('change', async function (e) {
        const file = e.target.files[0];
        e.target.value = '';
        if (!file || !replacingId) {
            return;
        }
        const fd = new FormData();
        fd.append('file', file);
        try {
            await App.upload('/api/admin/documents/' + replacingId + '/replace', fd);
            App.toast('File replaced; re-processing started.', 'success');
            load();
        } catch (err) {
            App.toast(err.message, 'danger');
        }
    });

    /* ---------- upload (modal + drag and drop) ---------- */
    const dropZone = $('#dropZone');
    const fileInput = $('#fileInput');

    function setFile(f) {
        chosenFile = f;
        $('#dropText').textContent = f ? f.name + ' (' + App.fmtSize(f.size) + ')'
            : 'Drag a PDF here, or click to choose (max 20 MB)';
    }

    dropZone.addEventListener('click', function () {
        fileInput.click();
    });
    dropZone.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            fileInput.click();
        }
    });
    fileInput.addEventListener('change', function () {
        setFile(fileInput.files[0] || null);
    });
    ['dragenter', 'dragover'].forEach(function (ev) {
        dropZone.addEventListener(ev, function (e) {
            e.preventDefault();
            dropZone.classList.add('over');
        });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
        dropZone.addEventListener(ev, function (e) {
            e.preventDefault();
            dropZone.classList.remove('over');
        });
    });
    dropZone.addEventListener('drop', function (e) {
        setFile(e.dataTransfer.files[0] || null);
    });

    $('#uploadForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        if (!chosenFile) {
            App.toast('Please choose a PDF file first.', 'warning');
            return;
        }
        const fd = new FormData();
        fd.append('file', chosenFile);
        fd.append('title', $('#upTitle').value);
        fd.append('category', $('#upCategory').value);
        fd.append('audience', $('#upAudience').value);
        fd.append('description', $('#upDesc').value);

        const wrap = $('#upProgressWrap');
        const bar = $('#upProgress');
        const submit = $('#upSubmit');
        wrap.classList.remove('d-none');
        submit.disabled = true;
        try {
            await App.upload('/api/admin/documents', fd, function (pct) {
                bar.style.width = pct + '%';
            });
            App.toast('Uploaded. Processing has started.', 'success');
            modal('#uploadModal').hide();
            $('#uploadForm').reset();
            setFile(null);
            load();
        } catch (err) {
            App.toast(err.message, 'danger');
        } finally {
            wrap.classList.add('d-none');
            bar.style.width = '0%';
            submit.disabled = false;
        }
    });

    let searchTimer = null;
    $('#docSearch').addEventListener('input', function () {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(load, 300);
    });
    $('#docStatus').addEventListener('change', load);

    load();
})();
