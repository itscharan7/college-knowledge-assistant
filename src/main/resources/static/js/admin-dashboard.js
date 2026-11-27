(function () {
    'use strict';
    const h = App.h;

    function statusBadge(status) {
        const cls = {READY: 'success', FAILED: 'danger', PROCESSING: 'warning', UPLOADED: 'secondary'}[status] ||
            'secondary';
        return h('span', {class: 'badge text-bg-' + cls, text: status});
    }

    function card(label, value) {
        return h('div', {class: 'col-6 col-md-4 col-xl'},
            h('div', {class: 'card border-0 shadow-sm stat-card h-100'},
                h('div', {class: 'card-body'},
                    h('div', {class: 'small text-secondary', text: label}),
                    h('div', {class: 'stat-value', text: String(value)}))));
    }

    async function load() {
        try {
            const s = await App.api('/api/admin/stats');
            const cards = document.getElementById('statCards');
            cards.replaceChildren(
                card('Documents', s.documents),
                card('Ready', s.readyDocuments),
                card('Failed', s.failedDocuments),
                card('Users', s.users),
                card('Questions asked', s.questions),
                card('Unanswered', s.unansweredQuestions));

            const recent = document.getElementById('recentBody');
            recent.replaceChildren();
            if (!s.recentUploads.length) {
                recent.append(h('tr', {}, h('td', {colspan: 3, class: 'text-secondary text-center py-4',
                    text: 'No documents uploaded yet.'})));
            }
            s.recentUploads.forEach(function (d) {
                recent.append(h('tr', {},
                    h('td', {class: 'truncate-cell', text: d.title}),
                    h('td', {}, statusBadge(d.status)),
                    h('td', {class: 'small text-secondary', text: App.fmtDate(d.createdAt)})));
            });
        } catch (e) {
            App.toast(e.message, 'danger');
        }

        try {
            const list = await App.api('/api/admin/questions/unanswered?size=10');
            const ul = document.getElementById('unansweredList');
            ul.replaceChildren();
            if (!list.length) {
                ul.append(h('li', {class: 'list-group-item text-secondary text-center py-4',
                    text: 'Nothing unanswered so far.'}));
            }
            list.forEach(function (q) {
                ul.append(h('li', {class: 'list-group-item'},
                    h('div', {text: q.question}),
                    h('div', {class: 'small text-secondary', text: App.fmtDate(q.createdAt)})));
            });
        } catch (e) {
            App.toast(e.message, 'danger');
        }

        try {
            const rows = await App.api('/api/admin/feedback?size=10');
            const body = document.getElementById('feedbackBody');
            body.replaceChildren();
            if (!rows.length) {
                body.append(h('tr', {}, h('td', {colspan: 5, class: 'text-secondary text-center py-4',
                    text: 'No feedback yet.'})));
            }
            rows.forEach(function (f) {
                body.append(h('tr', {},
                    h('td', {text: f.rating === 1 ? '👍' : '👎'}),
                    h('td', {class: 'truncate-cell', text: f.comment || '—'}),
                    h('td', {class: 'truncate-cell', text: f.answer}),
                    h('td', {class: 'small', text: f.userEmail}),
                    h('td', {class: 'small text-secondary', text: App.fmtDate(f.createdAt)})));
            });
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    load();
})();
