/* Shared helpers: CSRF-aware fetch, toasts, tiny DOM builder. All user-supplied text goes through
   textContent / createTextNode, never innerHTML, so answers and filenames cannot inject markup (XSS). */
(function () {
    'use strict';

    const token = document.querySelector('meta[name="_csrf"]').content;
    const headerName = document.querySelector('meta[name="_csrf_header"]').content;

    async function api(url, options) {
        options = options || {};
        const headers = Object.assign({'Accept': 'application/json'}, options.headers || {});
        headers[headerName] = token;
        let body = options.body;
        if (options.json !== undefined) {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify(options.json);
        }
        const res = await fetch(url, {method: options.method || 'GET', headers, body, credentials: 'same-origin'});
        if (res.status === 401) {
            window.location.href = '/login';
            throw new Error('Please sign in again.');
        }
        if (res.status === 204) {
            return null;
        }
        let data = null;
        try {
            data = await res.json();
        } catch (e) { /* empty body */ }
        if (!res.ok) {
            throw new Error((data && data.message) || ('Request failed (' + res.status + ')'));
        }
        return data;
    }

    /* multipart upload with progress (fetch cannot report upload progress) */
    function upload(url, formData, onProgress) {
        return new Promise(function (resolve, reject) {
            const xhr = new XMLHttpRequest();
            xhr.open('POST', url);
            xhr.setRequestHeader(headerName, token);
            xhr.setRequestHeader('Accept', 'application/json');
            xhr.upload.onprogress = function (e) {
                if (e.lengthComputable && onProgress) {
                    onProgress(Math.round(e.loaded * 100 / e.total));
                }
            };
            xhr.onload = function () {
                let data = null;
                try {
                    data = JSON.parse(xhr.responseText);
                } catch (e) { /* empty body */ }
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve(data);
                } else if (xhr.status === 401) {
                    window.location.href = '/login';
                } else {
                    reject(new Error((data && data.message) || ('Upload failed (' + xhr.status + ')')));
                }
            };
            xhr.onerror = function () {
                reject(new Error('Network error during upload.'));
            };
            xhr.send(formData);
        });
    }

    function toast(message, kind) {
        const container = document.getElementById('toasts');
        const t = document.createElement('div');
        t.className = 'toast align-items-center text-bg-' + (kind || 'dark') + ' border-0';
        t.setAttribute('role', 'alert');
        const flex = document.createElement('div');
        flex.className = 'd-flex';
        const bodyEl = document.createElement('div');
        bodyEl.className = 'toast-body';
        bodyEl.textContent = message;
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn-close btn-close-white me-2 m-auto';
        close.setAttribute('data-bs-dismiss', 'toast');
        close.setAttribute('aria-label', 'Close');
        flex.append(bodyEl, close);
        t.append(flex);
        container.append(t);
        t.addEventListener('hidden.bs.toast', function () {
            t.remove();
        });
        new bootstrap.Toast(t, {delay: 4500}).show();
    }

    function h(tag, props) {
        const e = document.createElement(tag);
        Object.entries(props || {}).forEach(function (kv) {
            const k = kv[0], v = kv[1];
            if (v === undefined || v === null || v === false) {
                return;
            }
            if (k === 'class') {
                e.className = v;
            } else if (k === 'text') {
                e.textContent = v;
            } else if (k.indexOf('on') === 0) {
                e.addEventListener(k.slice(2), v);
            } else {
                e.setAttribute(k, v === true ? '' : v);
            }
        });
        for (let i = 2; i < arguments.length; i++) {
            [].concat(arguments[i]).forEach(function (c) {
                if (c === null || c === undefined) {
                    return;
                }
                e.append(c.nodeType ? c : document.createTextNode(String(c)));
            });
        }
        return e;
    }

    function fmtDate(iso) {
        return iso ? new Date(iso).toLocaleString() : '';
    }

    function fmtSize(bytes) {
        if (bytes < 1024) {
            return bytes + ' B';
        }
        if (bytes < 1048576) {
            return (bytes / 1024).toFixed(1) + ' KB';
        }
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    window.App = {api: api, upload: upload, toast: toast, h: h, fmtDate: fmtDate, fmtSize: fmtSize};
})();
