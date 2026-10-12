// The Events tab: filters + event tiles on the left, the selected event's details and trading on the right.
// Mirrors the JavaFX client's EventsTabController.

import * as api from './api.js';
import { $, el, money, pretty, methodBadge, statusBadge, showAlert, showForm,
    parsePositiveInt, parsePositiveNumber } from './ui.js';
import { renderPriceChart } from './chart.js';

export class EventsView {
    constructor(app) {
        this.app = app;
        this.events = [];               // EventSummaryDTO[], in the server's order
        this.details = new Map();       // event name -> EventDetailsDTO (option prices for the tiles)
        this.selectedName = null;
        this.selectedDetails = null;

        for (const id of ['searchField', 'methodFilter', 'statusFilter', 'commissionFilter']) {
            $(id).addEventListener('input', () => this.renderList());
        }
        $('clearFiltersBtn').addEventListener('click', () => {
            $('searchField').value = '';
            for (const id of ['methodFilter', 'statusFilter', 'commissionFilter']) $(id).selectedIndex = 0;
            this.renderList();
        });
        $('startEventBtn').addEventListener('click', () => this.startSelectedEvent());
        $('closeEventBtn').addEventListener('click', () => this.closeSelectedEvent());

        // the graph is drawn for the panel's width
        let resizeTimer;
        window.addEventListener('resize', () => {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => renderPriceChart($('priceChart'), this.selectedDetails), 150);
        });
        this.clearDetails();
    }

    // ---------- data from the server ----------

    // the full list (on login)
    async setEvents(events) {
        this.events = events;
        await this.loadDetails(events.map((e) => e.name));
        this.renderList();
    }

    // events that changed since the last delta fetch: new uploads, trades, started/closed events
    async mergeUpdated(updated) {
        for (const event of updated) {
            const i = this.events.findIndex((e) => e.name === event.name);
            if (i >= 0) this.events[i] = event;
            else this.events.push(event);
        }
        await this.loadDetails(updated.map((e) => e.name));
        this.renderList();
        if (updated.some((e) => e.name === this.selectedName)) {
            await this.showSelected();
        }
    }

    async loadDetails(names) {
        await Promise.all(names.map(async (name) => {
            try {
                this.details.set(name, await api.getEventDetails(name));
            } catch (err) {
                console.warn(`Could not load details of ${name}:`, err.message);
            }
        }));
    }

    findEvent(name) {
        return this.events.find((e) => e.name === name);
    }

    // ---------- left side: filtered tiles ----------

    renderList() {
        const search = $('searchField').value.trim().toLowerCase();
        const method = $('methodFilter').value;
        const status = $('statusFilter').value;
        const commission = $('commissionFilter').value;
        const matches = (value, filter) => filter.startsWith('All') || value.toUpperCase() === filter.toUpperCase().replaceAll(' ', '_');

        const shown = this.events.filter((e) =>
            (!search || e.name.toLowerCase().includes(search))
            && matches(e.tradingMethodType, method)
            && matches(e.status, status)
            && matches(e.commissionType, commission));

        const container = $('eventsContainer');
        if (this.events.length === 0) {
            container.replaceChildren(el('div', { class: 'empty-note',
                text: 'No events yet. Events appear here once someone uploads a file from the desktop client.' }));
        } else if (shown.length === 0) {
            container.replaceChildren(el('div', { class: 'empty-note', text: 'No events match the filters.' }));
        } else {
            container.replaceChildren(...shown.map((e) => this.createTile(e)));
        }
    }

    createTile(event) {
        const details = this.details.get(event.name);
        const options = details
            ? details.options.map((o) => el('div', {
                class: 'option-preview' + (event.status === 'CLOSED' && details.winningOption === o.optionName ? ' winning' : ''),
            }, o.optionName, el('br'), money(o.currentPrice)))
            : event.options.map((name) => el('div', { class: 'option-preview' }, name));

        return el('div', {
            class: 'event-tile' + (event.name === this.selectedName ? ' selected' : ''),
            tabindex: '0',
            onClick: () => this.select(event.name),
            onKeydown: (e) => { if (e.key === 'Enter') this.select(event.name); },
        },
        el('div', { class: 'tile-top' }, el('span', { class: 'event-id-label', text: '#' + event.name }), methodBadge(event.tradingMethodType)),
        el('div', { class: 'align-right' }, statusBadge(event.status)),
        el('div', { class: 'event-title-label', text: event.name }),
        el('div', { class: 'options-header-label align-right', text: 'Available Options:' }),
        el('div', { class: 'event-desc-label tile-desc', text: event.description.trim() }),
        el('div', { class: 'tile-options' }, options),
        el('div', { class: 'tile-bottom' },
            el('span', { class: 'comm-title-label', text: 'Commission:' }),
            el('span', { class: 'comm-val-label', text: `${event.commission}% (${pretty(event.commissionType)})` }),
            el('span', { class: 'comm-title-label', text: 'Account:' }),
            el('span', { class: 'comm-val-label', text: money(event.accountBalance) })));
    }

    // ---------- right side: the selected event ----------

    async select(name) {
        this.selectedName = name;
        this.renderList();
        await this.showSelected();
    }

    clearDetails() {
        $('detailId').textContent = '';
        $('detailMethod').replaceChildren();
        $('detailStatus').replaceChildren();
        $('detailTitle').textContent = 'Select an event to view details';
        $('detailDesc').textContent = 'Click any event tile on the left panel to display full specifications here.';
        $('detailComm').textContent = 'N/A';
        $('detailAccount').textContent = 'N/A';
        $('mmControls').hidden = true;
        $('optionsContainer').replaceChildren();
        $('historyList').replaceChildren();
        $('participantsList').replaceChildren();
        this.selectedDetails = null;
        renderPriceChart($('priceChart'), null);
    }

    async showSelected() {
        const event = this.findEvent(this.selectedName);
        if (!event) return this.clearDetails();
        const isOrderBook = event.tradingMethodType === 'ORDER_BOOK';

        $('detailId').textContent = '#' + event.name;
        $('detailMethod').replaceChildren(methodBadge(event.tradingMethodType));
        $('detailStatus').replaceChildren(statusBadge(event.status));
        $('detailTitle').textContent = event.name;
        $('detailDesc').textContent = event.description.trim();
        $('detailComm').textContent = `${event.commission}% (${pretty(event.commissionType)})`;
        $('detailAccount').textContent = money(event.accountBalance);
        $('historyTitle').textContent = isOrderBook ? 'Order Book Queue' : 'Participation History';

        // only the event's market maker can start or close it (the server checks this too)
        const isMarketMaker = event.marketMaker && event.marketMaker.toLowerCase() === this.app.username.toLowerCase();
        $('mmControls').hidden = !isMarketMaker;
        $('startEventBtn').hidden = !(isMarketMaker && event.status === 'NOT_STARTED');
        $('closeEventBtn').hidden = !(isMarketMaker && event.status === 'OPEN');

        let details, participants;
        try {
            [details, participants] = await Promise.all([api.getEventDetails(event.name), api.getParticipants(event.name)]);
        } catch (err) {
            console.warn('Could not load the event details:', err.message);
            return;
        }
        if (event.name !== this.selectedName) return; // the user picked another event meanwhile

        this.details.set(event.name, details);
        this.selectedDetails = details;
        this.renderOptions(event, details, isOrderBook);
        this.renderHistory(details, isOrderBook);
        this.renderParticipants(participants, details);
        renderPriceChart($('priceChart'), details);
    }

    renderOptions(event, details, isOrderBook) {
        const cards = details.options.map((option) => {
            const price = option.currentPrice;
            const stats = isOrderBook
                ? el('div', { class: 'option-stats',
                    text: `Bid: ${money(option.highestBid)}  |  Ask: ${money(option.lowestAsk)}\nMid: ${money(option.midPrice)}  |  Spread: ${money(option.spread)}` })
                : el('div', { class: 'option-percent-label', text: `${(price * 100).toFixed(1)}%` });
            const buyText = isOrderBook ? 'Buy' : `Buy ${(price * 100).toFixed(2)}¢`;

            return el('div', { class: 'option-card' },
                el('span', { class: 'option-name-label', text: option.optionName }),
                el('span', { class: 'spacer' }),
                stats,
                el('span', { class: 'option-price-label', text: money(price) }),
                el('div', { class: 'option-actions' },
                    el('button', { type: 'button', class: 'buy-option-btn', text: buyText,
                        onClick: () => this.trade(event, option.optionName, 'BUY', isOrderBook) }),
                    isOrderBook ? el('button', { type: 'button', class: 'sell-option-btn', text: 'Sell',
                        onClick: () => this.trade(event, option.optionName, 'SELL', isOrderBook) }) : null));
        });
        $('optionsContainer').replaceChildren(...cards);
    }

    renderHistory(details, isOrderBook) {
        const items = [];
        if (isOrderBook) {
            // the orders waiting in the book
            for (const option of details.options) {
                for (const [queue, isBuy] of [[option.buyQueue, true], [option.sellQueue, false]]) {
                    for (const req of queue) {
                        items.push(el('li', { class: isBuy ? 'history-buy' : 'history-sell',
                            text: `[${isBuy ? 'BUY' : 'SELL'}] User: ${req.username} | Option: ${option.optionName} | Amount: ${req.amount} | Limit: ${money(req.price)}` }));
                    }
                }
            }
            if (items.length === 0) items.push(el('li', { class: 'plain', text: 'The Order Book is currently empty.' }));
        } else {
            // the trades, newest first
            for (const trade of details.tradeHistory) {
                items.push(el('li', { class: trade.action === 'BUY' ? 'history-buy' : 'history-sell',
                    text: `[${trade.action}] Option: ${trade.optionName} | Amount: ${trade.amount} | Paid: ${money(trade.pricePaid)}` }));
            }
            if (items.length === 0) items.push(el('li', { class: 'plain', text: 'No trades recorded yet for this event.' }));
        }
        $('historyList').replaceChildren(...items);
    }

    renderParticipants(participants, details) {
        // a holding is worth its shares times the option's current price (LMSR price, or LAST in an order book)
        const prices = new Map(details.options.map((o) => [o.optionName, o.currentPrice]));
        const items = [];
        for (const participant of participants) {
            for (const [optionName, shares] of Object.entries(participant.shares)) {
                if (shares > 0) {
                    items.push(el('li', { class: 'holding-label',
                        text: `User: ${participant.username} | Option '${optionName}' | Shares: ${shares} | Value: ${money(shares * (prices.get(optionName) || 0))}` }));
                }
            }
        }
        if (items.length === 0) items.push(el('li', { class: 'plain', text: 'No users have taken part in this event yet.' }));
        $('participantsList').replaceChildren(...items);
    }

    // ---------- actions ----------

    async trade(event, optionName, action, isOrderBook) {
        const fields = [{ name: 'amount', label: 'Amount of shares:', placeholder: 'e.g. 10', inputmode: 'numeric' }];
        if (isOrderBook) {
            fields.push({ name: 'price', label: 'Price limit ($) (Price Per Share):', placeholder: 'e.g. 0.50', inputmode: 'decimal' });
        }
        const input = await showForm({
            title: `${action} Shares - ${optionName}`,
            header: `Event: ${event.name}\nOption: ${optionName}`,
            fields,
            okText: action === 'BUY' ? 'Buy' : 'Sell',
            validate: (v) => {
                if (parsePositiveInt(v.amount) == null) return 'The amount must be a whole number greater than 0.';
                if (isOrderBook && parsePositiveNumber(v.price) == null) return 'The price limit must be a number greater than 0.';
                return null;
            },
        });
        if (!input) return;

        try {
            const receipt = await api.placeOrder(this.app.username, event.name, optionName,
                parsePositiveInt(input.amount), action, isOrderBook ? parsePositiveNumber(input.price) : 0);
            if (receipt.isSuccess) {
                const message = receipt.totalCost === 0 && receipt.commission === 0
                    ? 'Trade submitted to the queue.'
                    : `Trade successful!\n${action === 'SELL' ? 'Total Received' : 'Total Paid'}: ${money(receipt.totalCost)}\nCommission: ${money(receipt.commission)}`;
                showAlert('success', 'Success', message);
            } else {
                showAlert('error', 'Trade Failed', receipt.message);
            }
        } catch (err) {
            showAlert('error', 'Trade Failed', err.message);
        }
        this.app.refreshSoon();
    }

    async startSelectedEvent() {
        const event = this.findEvent(this.selectedName);
        if (!event) return;
        try {
            const receipt = await api.startEvent(this.app.username, event.name);
            if (receipt.isSuccess) {
                showAlert('success', 'Event Started', `Event successfully opened!\nCost deducted: ${money(receipt.totalCost)}`);
            } else {
                showAlert('error', 'Cannot start the event', receipt.message);
            }
        } catch (err) {
            showAlert('error', 'Cannot start the event', err.message);
        }
        this.app.refreshSoon();
    }

    async closeSelectedEvent() {
        const event = this.findEvent(this.selectedName);
        if (!event) return;
        const input = await showForm({
            title: 'Close Event',
            header: `Closing: ${event.name}`,
            fields: [{ name: 'winner', label: 'Select the winning option:', options: event.options }],
            okText: 'Close Event',
        });
        if (!input) return;

        try {
            const receipt = await api.closeEvent(this.app.username, event.name, input.winner);
            if (receipt.isSuccess) {
                showAlert('success', 'Event Closed',
                    `Event Closed!\nWinner: ${receipt.winningOption}\nTotal Payout: ${money(receipt.totalPayout)}\n`
                    + `Commission Taken: ${money(receipt.commissionTaken)}\nReturned to MM: ${money(receipt.leftoverPool)}`);
            } else {
                showAlert('error', 'Error Closing Event', receipt.message);
            }
        } catch (err) {
            showAlert('error', 'Error Closing Event', err.message);
        }
        this.app.refreshSoon();
    }

    // the "Active User / Available Cash" strip
    setUser(name, balance, blocked) {
        $('eventsUserName').textContent = name;
        $('eventsUserBalance').textContent = money(balance) + (blocked ? ' (blocked)' : '');
    }
}
