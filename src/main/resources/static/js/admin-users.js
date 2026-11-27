(function () {
    'use strict';
    const h = App.h;
    const $ = function (s) {
        return document.querySelector(s);
    };

    const body = $('#userBody');
    const emptyEl = $('#userEmpty');
    let myId = null;

    async function load() {
        try {
            if (myId === null) {
                myId = (await App.api('/api/me')).id;
            }
            const q = encodeURIComponent($('#userSearch').value.trim());
            const users = await App.api('/api/admin/users?q=' + q);
            render(users);
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    function render(users) {
        body.replaceChildren();
        emptyEl.classList.toggle('d-none', users.length > 0);
        users.forEach(function (u) {
            const self = u.id === myId;
            const roleSelect = h('select', {class: 'form-select form-select-sm w-auto', disabled: self,
                'aria-label': 'Role for ' + u.name});
            ['STUDENT', 'FACULTY', 'ADMIN'].forEach(function (r) {
                roleSelect.append(h('option', {value: r, text: r, selected: r === u.role}));
            });
            roleSelect.addEventListener('change', function () {
                patch(u.id, {role: roleSelect.value}, 'Role updated.');
            });

            body.append(h('tr', {},
                h('td', {text: u.name + (self ? ' (you)' : '')}),
                h('td', {class: 'truncate-cell', text: u.email}),
                h('td', {}, roleSelect),
                h('td', {}, h('span', {class: 'badge text-bg-' + (u.active ? 'success' : 'secondary'),
                    text: u.active ? 'Active' : 'Disabled'})),
                h('td', {class: 'small text-secondary', text: App.fmtDate(u.createdAt)}),
                h('td', {class: 'text-end'},
                    h('button', {type: 'button', class: 'btn btn-sm btn-outline-secondary', disabled: self,
                        text: u.active ? 'Deactivate' : 'Activate',
                        onclick: function () {
                            patch(u.id, {active: !u.active}, u.active ? 'Account deactivated.' : 'Account activated.');
                        }}))));
        });
    }

    async function patch(id, change, okMessage) {
        try {
            await App.api('/api/admin/users/' + id, {method: 'PATCH', json: change});
            App.toast(okMessage, 'success');
        } catch (e) {
            App.toast(e.message, 'danger');
        }
        load();
    }

    $('#createForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        try {
            await App.api('/api/admin/users', {
                method: 'POST',
                json: {name: $('#cuName').value, email: $('#cuEmail').value, password: $('#cuPassword').value,
                    role: $('#cuRole').value}
            });
            bootstrap.Modal.getOrCreateInstance($('#createModal')).hide();
            $('#createForm').reset();
            App.toast('Account created.', 'success');
            load();
        } catch (err) {
            App.toast(err.message, 'danger');
        }
    });

    let timer = null;
    $('#userSearch').addEventListener('input', function () {
        clearTimeout(timer);
        timer = setTimeout(load, 300);
    });

    load();
})();
