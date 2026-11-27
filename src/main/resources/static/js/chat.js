(function () {
    'use strict';
    const h = App.h;
    const $ = function (s) {
        return document.querySelector(s);
    };

    const messagesEl = $('#messages');
    const listEl = $('#convList');
    const form = $('#composer');
    const input = $('#question');
    const sendBtn = $('#sendBtn');
    const drawer = new bootstrap.Offcanvas($('#sourceDrawer'));

    const SUGGESTIONS = [
        'What is the minimum attendance required to write exams?',
        'When do the end-semester exams start?',
        'What are the placement eligibility criteria?',
        'What is the fee structure for this year?'
    ];

    let currentId = null;
    let busy = false;

    /* ---------- conversations ---------- */
    async function loadConversations() {
        try {
            const list = await App.api('/api/chat/conversations');
            listEl.replaceChildren();
            if (!list.length) {
                listEl.append(h('p', {class: 'text-secondary small px-3 pt-2', text: 'No conversations yet.'}));
            }
            list.forEach(function (c) {
                const row = h('button', {
                        type: 'button', role: 'listitem',
                        class: 'conv-item' + (c.id === currentId ? ' active' : ''),
                        onclick: function () {
                            openConversation(c.id);
                        }
                    },
                    h('span', {class: 'conv-title', text: c.title}),
                    h('span', {
                        class: 'conv-del', role: 'button', title: 'Delete conversation',
                        'aria-label': 'Delete conversation', text: '✕',
                        onclick: function (e) {
                            e.stopPropagation();
                            removeConversation(c.id);
                        }
                    }));
                listEl.append(row);
            });
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    async function openConversation(id) {
        try {
            const msgs = await App.api('/api/chat/conversations/' + id + '/messages');
            currentId = id;
            messagesEl.replaceChildren();
            msgs.forEach(renderMessage);
            scrollDown();
            loadConversations();
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    async function removeConversation(id) {
        if (!window.confirm('Delete this conversation?')) {
            return;
        }
        try {
            await App.api('/api/chat/conversations/' + id, {method: 'DELETE'});
            if (id === currentId) {
                newChat();
            }
            loadConversations();
        } catch (e) {
            App.toast(e.message, 'danger');
        }
    }

    /* A conversation row is only created on the first question, so no empty chats pile up. */
    function newChat() {
        currentId = null;
        messagesEl.replaceChildren(emptyState());
        loadConversations();
        input.focus();
    }

    function emptyState() {
        return h('div', {class: 'empty-state'},
            h('h2', {class: 'h5 text-body', text: 'Ask about your college'}),
            h('p', {class: 'mb-0', text: 'Answers come only from the official documents your college has uploaded, ' +
                    'with the document and page shown as a source. If it is not in the documents, I will say so.'}),
            h('div', {class: 'suggest'}, SUGGESTIONS.map(function (s) {
                return h('button', {
                    type: 'button', text: s, onclick: function () {
                        input.value = s;
                        form.requestSubmit();
                    }
                });
            })));
    }

    /* ---------- rendering ---------- */
    function renderMessage(m) {
        const isUser = m.role === 'USER';
        const notFound = !isUser && m.answerable === false;
        const wrap = h('div', {class: 'msg ' + (isUser ? 'msg-user' : 'msg-assistant')});
        wrap.append(h('div', {class: 'bubble' + (notFound ? ' not-found' : ''), text: m.content}));

        if (!isUser && m.sources && m.sources.length) {
            wrap.append(h('div', {class: 'cite-row', 'aria-label': 'Sources'}, m.sources.map(function (c) {
                return h('button', {
                    type: 'button', class: 'cite-chip', text: c.documentTitle + ' · p.' + c.page,
                    onclick: function () {
                        openSource(c);
                    }
                });
            })));
        }
        if (!isUser && m.id) {
            wrap.append(feedbackRow(m.id));
        }
        messagesEl.append(wrap);
        return wrap;
    }

    function feedbackRow(messageId) {
        const row = h('div', {class: 'fb-row'});
        [['👍', 1, 'Helpful'], ['👎', -1, 'Not helpful']].forEach(function (b) {
            const btn = h('button', {type: 'button', class: 'fb-btn', title: b[2], 'aria-label': b[2], text: b[0]});
            btn.addEventListener('click', async function () {
                let comment = null;
                if (b[1] === -1) {
                    comment = window.prompt('What was wrong? (optional)') || null;
                }
                try {
                    await App.api('/api/feedback', {
                        method: 'POST',
                        json: {messageId: messageId, rating: b[1], comment: comment}
                    });
                    row.querySelectorAll('.fb-btn').forEach(function (x) {
                        x.classList.remove('on');
                    });
                    btn.classList.add('on');
                    App.toast('Thanks for the feedback.', 'success');
                } catch (e) {
                    App.toast(e.message, 'danger');
                }
            });
            row.append(btn);
        });
        return row;
    }

    function openSource(c) {
        $('#sourceTitle').textContent = c.documentTitle;
        $('#sourceMeta').textContent = 'Page ' + c.page;
        $('#sourceExcerpt').textContent = c.excerpt || '';
        const open = $('#sourceOpen');
        const gone = $('#sourceGone');
        if (c.documentId) {
            open.href = '/api/documents/' + c.documentId + '/file#page=' + c.page;
            open.classList.remove('d-none');
            gone.classList.add('d-none');
        } else {
            open.classList.add('d-none');
            gone.classList.remove('d-none');
        }
        drawer.show();
    }

    function scrollDown() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function thinking() {
        const wrap = h('div', {class: 'msg msg-assistant'},
            h('div', {class: 'bubble', 'aria-label': 'Thinking'},
                h('span', {class: 'thinking'}, h('span'), h('span'), h('span'))));
        messagesEl.append(wrap);
        scrollDown();
        return wrap;
    }

    /* ---------- sending ---------- */
    form.addEventListener('submit', async function (e) {
        e.preventDefault();
        const question = input.value.trim();
        if (!question || busy) {
            return;
        }
        busy = true;
        sendBtn.disabled = true;
        input.value = '';
        input.style.height = 'auto';

        const empty = messagesEl.querySelector('.empty-state');
        if (empty) {
            empty.remove();
        }
        renderMessage({role: 'USER', content: question});
        const waiting = thinking();

        try {
            if (!currentId) {
                const created = await App.api('/api/chat/conversations', {method: 'POST'});
                currentId = created.id;
            }
            const res = await App.api('/api/chat/conversations/' + currentId + '/ask',
                {method: 'POST', json: {question: question}});
            waiting.remove();
            renderMessage({
                role: 'ASSISTANT', id: res.messageId, content: res.answer,
                answerable: res.answerable, sources: res.sources
            });
            loadConversations();
        } catch (err) {
            waiting.remove();
            App.toast(err.message, 'danger');
            input.value = question; // let the user retry without retyping
        } finally {
            busy = false;
            sendBtn.disabled = false;
            scrollDown();
            input.focus();
        }
    });

    input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            form.requestSubmit();
        }
    });
    input.addEventListener('input', function () {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 140) + 'px';
    });
    $('#newChat').addEventListener('click', newChat);

    newChat();
})();
