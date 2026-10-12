// Small UI helpers shared by all screens: building elements, formatting and dialogs.

// creates an element; text always goes in as text (event names come from uploaded files, never as HTML)
export function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (value == null || value === false) continue;
        if (key === 'class') node.className = value;
        else if (key === 'text') node.textContent = value;
        else if (key.startsWith('on')) node.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === 'style') node.style.cssText = value;
        else node.setAttribute(key, value === true ? '' : value);
    }
    for (const child of children.flat()) {
        if (child == null || child === false) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

export const $ = (id) => document.getElementById(id);

// $12.34, or -$12.34 for negative amounts
export function money(amount) {
    const value = Number(amount) || 0;
    return `${value < 0 ? '-' : ''}$${Math.abs(value).toFixed(2)}`;
}

// +$12.34 / -$12.34
export function signedMoney(amount) {
    return `${amount >= 0 ? '+' : '-'}$${Math.abs(amount).toFixed(2)}`;
}

// ORDER_BOOK -> ORDER BOOK, ON_CLOSE -> ON CLOSE
export const pretty = (value) => String(value ?? '').replaceAll('_', ' ');

export function methodBadge(method) {
    return el('span', { class: `method-badge method-${String(method).toLowerCase()}`, text: pretty(method) });
}

export function statusBadge(status) {
    return el('span', { class: `status-badge status-${String(status).toLowerCase()}`, text: pretty(status) });
}

// one row of a table; cells are text or elements
export function tableRow(cells, props = {}) {
    return el('tr', props, cells.map((cell) => el('td', {}, cell)));
}

export function placeholderRow(colSpan, text) {
    return el('tr', {}, el('td', { class: 'placeholder', colspan: String(colSpan), text }));
}

// ---------- dialogs ----------

const modalRoot = () => document.getElementById('modalRoot');

function openModal(type, title, body, buttons) {
    return new Promise((resolve) => {
        const backdrop = el('div', { class: 'modal-backdrop' });
        const close = (result) => {
            backdrop.remove();
            document.removeEventListener('keydown', onKey);
            resolve(result);
        };
        const onKey = (e) => {
            if (e.key === 'Escape') close(null);
        };
        const foot = el('div', { class: 'modal-foot' },
            buttons.map((b) => el('button', { type: b.submit ? 'submit' : 'button', class: b.class, text: b.text,
                onClick: b.submit ? null : () => close(b.value) })));
        const form = el('form', { class: 'modal', novalidate: true },
            el('div', { class: `modal-head ${type}` }, el('span', { class: 'dot' }), title),
            el('div', { class: 'modal-body' }, body),
            foot);
        backdrop.append(form);
        modalRoot().append(backdrop);
        document.addEventListener('keydown', onKey);
        backdrop._close = close;
        backdrop._form = form;
        (form.querySelector('input, select') || foot.lastChild).focus();
    });
}

// a message with an OK button (type: info | success | warning | error)
export function showAlert(type, title, message) {
    return openModal(type, title, message, [{ text: 'OK', class: 'buy-option-btn', value: true }]);
}

/**
 * A dialog with input fields. Resolves with { fieldName: value } when OK is pressed, or null when cancelled.
 * validate(values) returns an error message to show (and keep the dialog open), or null when the input is fine.
 */
export function showForm({ title, header, fields, okText = 'OK', validate = () => null }) {
    const inputs = {};
    const error = el('div', { class: 'modal-error', hidden: true });
    const body = [
        header ? el('div', { text: header }) : null,
        ...fields.map((f) => {
            const input = f.options
                ? el('select', { name: f.name }, f.options.map((o) => el('option', { value: o, text: o })))
                : el('input', { name: f.name, type: 'text', inputmode: f.inputmode || 'text', placeholder: f.placeholder || '' });
            inputs[f.name] = input;
            return el('label', {}, el('span', { class: 'comm-title-label', text: f.label }), input);
        }),
        error,
    ];

    const promise = openModal('info', title, body, [
        { text: 'Cancel', class: 'modern-filter', value: null },
        { text: okText, class: 'buy-option-btn', submit: true },
    ]);

    // the form is the newest modal; OK only closes it when the input is valid
    const backdrop = modalRoot().lastElementChild;
    backdrop._form.addEventListener('submit', (e) => {
        e.preventDefault();
        const values = Object.fromEntries(Object.entries(inputs).map(([name, input]) => [name, input.value.trim()]));
        const problem = validate(values);
        if (problem) {
            error.textContent = problem;
            error.hidden = false;
            return;
        }
        backdrop._close(values);
    });
    return promise;
}

// reads a positive whole number, or null
export function parsePositiveInt(text) {
    return /^\d+$/.test(text) && Number(text) > 0 ? Number(text) : null;
}

// reads a positive number, or null
export function parsePositiveNumber(text) {
    const value = Number(text);
    return text !== '' && Number.isFinite(value) && value > 0 ? value : null;
}
