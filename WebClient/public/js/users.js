// The Users tab: every user with their balance and whether they are a market maker;
// picking one shows the events they are market maker of. Mirrors the JavaFX client's UsersTabController.

import { $, el, money, pretty, tableRow, placeholderRow } from './ui.js';

export class UsersView {
    constructor(app) {
        this.app = app;
        this.viewedUser = null; // null = the logged in user
    }

    // called with fresh UserSummaryDTO[] on every refresh
    show(users) {
        const events = this.app.events.events;
        const managedBy = (name) => events.filter((e) => (e.marketMaker || '').toLowerCase() === name.toLowerCase());
        const viewed = this.viewedUser ?? this.app.username;

        const rows = users.map((user) => {
            const managed = managedBy(user.name).length;
            return tableRow([user.name, money(user.balance), managed ? `Yes (${managed})` : 'No'], {
                class: user.name.toLowerCase() === viewed.toLowerCase() ? 'selected' : null,
                onClick: () => {
                    this.viewedUser = user.name;
                    this.show(users);
                },
            });
        });
        $('usersBody').replaceChildren(...(rows.length ? rows : [placeholderRow(3, 'No users yet.')]));

        const user = users.find((u) => u.name.toLowerCase() === viewed.toLowerCase());
        $('viewedUserName').textContent = user ? user.name : 'Select a user...';
        $('viewedUserBalance').textContent = money(user ? user.balance : 0);

        // events have no id, so the row number is shown instead
        const managed = user ? managedBy(user.name) : [];
        const eventRows = managed.map((e, i) =>
            tableRow([String(i + 1), e.name, pretty(e.status), pretty(e.tradingMethodType)]));
        $('mmEventsBody').replaceChildren(...(eventRows.length
            ? eventRows
            : [placeholderRow(4, "This user isn't market maker of any event.")]));
    }
}
