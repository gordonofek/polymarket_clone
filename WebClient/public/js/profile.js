// The Profile tab: the logged in user's balance, deposits/withdrawals, balance history, portfolio
// and an overview of a picked event. Mirrors the JavaFX client's ProfileTabController.

import * as api from './api.js';
import { $, el, money, signedMoney, pretty, tableRow, placeholderRow, showAlert, showForm,
    parsePositiveNumber } from './ui.js';

const ACTION_LABELS = {
    DEPOSIT: 'Deposit', WITHDRAW: 'Withdraw', BUY: 'Buy', SELL: 'Sell', EVENT_START: 'Open Event',
    COMMISSION: 'Commission', PAYOUT: 'Payout', POOL_RETURN: 'Pool Return',
};

const timeFormat = new Intl.DateTimeFormat('en-GB', {
    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
});

export class ProfileView {
    constructor(app) {
        this.app = app;
        this.balance = 0;
        this.wasBlocked = false;
        this.viewed = null;           // { eventName, optionName } of the picked portfolio row
        this.shownOverview = null;    // JSON of the overview on screen, so it's only rebuilt when it changed

        $('addFundsBtn').addEventListener('click', () => this.askFunds(true));
        $('withdrawBtn').addEventListener('click', () => this.askFunds(false));
    }

    // called with fresh UserDetailsDTO on every refresh
    show(details) {
        this.balance = details.balance;
        $('profileUsername').textContent = details.name;
        $('profileBalance').replaceChildren(money(details.balance),
            details.blocked ? el('div', { class: 'blocked-note', text: 'Blocked: deposit to get back to $0.00 or more' }) : '');

        this.renderBalanceHistory(details.balanceHistory);
        this.renderPortfolio(details.portfolio);
        this.notifyIfJustBlocked(details);
        if (this.viewed) this.loadOverview();
    }

    // the balance can go negative from someone else's action too (a waiting buy order gets filled),
    // so every refresh checks it, and the user is told once, when it happens
    notifyIfJustBlocked(details) {
        const justBlocked = details.blocked && !this.wasBlocked;
        this.wasBlocked = details.blocked;
        if (justBlocked) {
            showAlert('warning', 'Account Blocked',
                `Your balance went negative (${money(details.balance)}).\nYou can't trade until you deposit enough to bring it back to $0.00 or more.`);
        }
    }

    renderBalanceHistory(history) {
        const rows = history.map((item) => tableRow([
            ACTION_LABELS[item.type] || pretty(item.type),
            el('span', { class: item.amount >= 0 ? 'amount-in' : 'amount-out', text: signedMoney(item.amount) }),
            `${item.details}  ·  Balance: ${money(item.balanceAfter)}  ·  ${timeFormat.format(new Date(item.timestamp))}`,
        ]));
        $('balanceHistoryBody').replaceChildren(...(rows.length ? rows : [placeholderRow(3, 'No balance history yet.')]));
    }

    renderPortfolio(portfolio) {
        const rows = portfolio.map((item) => {
            const isViewed = this.viewed && this.viewed.eventName === item.eventName && this.viewed.optionName === item.optionName;
            return tableRow([item.eventName, item.optionName, String(item.shares), pretty(item.status), money(item.profitLoss)], {
                class: isViewed ? 'selected' : null,
                onClick: () => this.pick(item),
            });
        });
        $('portfolioBody').replaceChildren(...(rows.length ? rows : [placeholderRow(5, "You don't own any shares yet.")]));
    }

    // a click on a portfolio row shows that event's overview
    pick(item) {
        this.viewed = { eventName: item.eventName, optionName: item.optionName };
        this.shownOverview = null;
        $('overviewTitle').textContent = 'Overview for: ' + item.eventName;
        $('overviewBox').replaceChildren();
        for (const row of $('portfolioBody').children) row.classList.remove('selected');
        [...$('portfolioBody').children].find((row) =>
            row.children[0]?.textContent === item.eventName && row.children[1]?.textContent === item.optionName)?.classList.add('selected');
        this.loadOverview();
    }

    async loadOverview() {
        const eventName = this.viewed.eventName;
        const event = this.app.events.findEvent(eventName);
        let overview;
        try {
            overview = await api.getUserEventOverview(this.app.username, eventName);
        } catch (err) {
            console.warn('Could not load the event overview:', err.message);
            return;
        }
        // a late reply for a row the user already left, or nothing changed: keep the screen as it is
        const json = JSON.stringify([overview, event?.status]);
        if (!this.viewed || this.viewed.eventName !== eventName || json === this.shownOverview) return;
        this.shownOverview = json;
        $('overviewTitle').textContent = 'Overview for: ' + eventName;
        $('overviewBox').replaceChildren(...this.buildOverview(overview, event));
    }

    buildOverview(overview, event) {
        const isOrderBook = event?.tradingMethodType === 'ORDER_BOOK';
        const isClosed = event?.status === 'CLOSED';
        const parts = [el('div', { class: 'overview-comm-label', text: `Total Commission Paid: ${money(overview.totalCommissionPaid)}` })];

        if (isOrderBook) {
            parts.push(el('div', { class: 'overview-header-label', text: 'Order Book Holdings & Costs:' }));
            for (const [option, spent] of Object.entries(overview.optionInvestments)) {
                parts.push(el('div', { text: `• ${option}: Spent ${money(spent)}` }));
            }
            if (isClosed) {
                parts.push(el('div', { class: 'overview-pl-label', text: `Total Profit/Loss: ${money(overview.profitLoss)}` }));
            }
        } else {
            parts.push(el('div', { class: 'overview-header-label', text: 'LMSR Trade History:' }));
            if (overview.userTradeHistory.length === 0) {
                parts.push(el('div', { text: 'No trades recorded yet.' }));
            }
            for (const trade of overview.userTradeHistory) { // newest first
                parts.push(el('div', { class: trade.action === 'BUY' ? 'history-buy' : 'history-sell',
                    text: `[${trade.action}] Option: ${trade.optionName} | Amount: ${trade.amount} | Paid: ${money(trade.pricePaid)}` }));
            }
            if (isClosed) {
                parts.push(el('div', { class: 'overview-header-label', text: 'Total Shares Bought per Option:' }));
                for (const [option, total] of Object.entries(overview.optionTotalShares)) {
                    parts.push(el('div', { text: `• ${option}: ${total} shares` }));
                }
                parts.push(el('div', { class: 'overview-winner-label', text: `Winning Option: ${overview.winningOption}` }));
            }
        }
        return parts;
    }

    async askFunds(isDeposit) {
        const input = await showForm({
            title: isDeposit ? 'Add Funds' : 'Withdraw',
            header: `Current balance: ${money(this.balance)}`,
            fields: [{ name: 'amount', label: 'Amount ($):', placeholder: 'e.g. 100', inputmode: 'decimal' }],
            okText: isDeposit ? 'Deposit' : 'Withdraw',
            validate: (v) => {
                const amount = parsePositiveNumber(v.amount);
                if (amount == null) return 'The amount must be a number greater than 0.';
                if (!isDeposit && amount > this.balance) return `You can withdraw at most ${money(Math.max(this.balance, 0))}.`;
                return null;
            },
        });
        if (!input) return;

        const amount = parsePositiveNumber(input.amount);
        try {
            const newBalance = isDeposit
                ? await api.deposit(this.app.username, amount)
                : await api.withdraw(this.app.username, amount);
            showAlert('success', isDeposit ? 'Deposit' : 'Withdrawal',
                `${isDeposit ? 'Deposited' : 'Withdrew'} ${money(amount)}.\nNew balance: ${money(newBalance)}`);
        } catch (err) {
            showAlert('error', isDeposit ? 'Deposit failed' : 'Withdrawal failed', err.message);
        }
        this.app.refreshSoon();
    }
}
