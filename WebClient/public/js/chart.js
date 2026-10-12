// The event's price-history graph, drawn as SVG (no chart library, so nothing to download).
// Same data as the JavaFX LineChart: one line per option, the price per share of each of its trades, oldest to newest.

const COLORS = ['#2f6fed', '#f5a623', '#2e9e5b', '#e6483f', '#9b59b6', '#17a2b8']; // the JavaFX theme's series colors
const SVG_NS = 'http://www.w3.org/2000/svg';

function svg(tag, attrs, text) {
    const node = document.createElementNS(SVG_NS, tag);
    for (const [key, value] of Object.entries(attrs)) node.setAttribute(key, value);
    if (text != null) node.textContent = text;
    return node;
}

export function renderPriceChart(container, details) {
    container.replaceChildren();
    if (!details) return;

    // tradeHistory is newest first; the graph reads left to right in time
    const series = details.options.map((option, i) => ({
        name: option.optionName,
        color: COLORS[i % COLORS.length],
        points: details.tradeHistory
            .filter((t) => t.optionName === option.optionName && t.amount > 0)
            .reverse()
            .map((t) => t.pricePaid / t.amount),
    }));

    const legend = document.createElement('div');
    legend.className = 'chart-legend';
    for (const s of series) {
        const item = document.createElement('span');
        item.style.setProperty('--c', s.color);
        item.textContent = s.name;
        legend.append(item);
    }
    container.append(legend);

    const longest = Math.max(0, ...series.map((s) => s.points.length));
    if (longest === 0) {
        const empty = document.createElement('div');
        empty.className = 'chart-empty';
        empty.textContent = 'No trades yet, so there is no price history to show.';
        container.append(empty);
        return;
    }

    const width = Math.max(container.clientWidth, 280);
    const height = 220;
    const pad = { left: 12, right: 44, top: 10, bottom: 12 };
    const plotW = width - pad.left - pad.right;
    const plotH = height - pad.top - pad.bottom;

    // prices are 0..1 for LMSR and for an order book with d = 1; a bigger d can go higher
    const maxPrice = Math.max(...series.flatMap((s) => s.points));
    const top = maxPrice <= 1 ? 1 : Math.ceil(maxPrice);
    const step = top <= 1 ? 0.25 : top / 4;
    const x = (i) => pad.left + (longest === 1 ? plotW / 2 : (i / (longest - 1)) * plotW);
    const y = (v) => pad.top + plotH - (v / top) * plotH;

    const chart = svg('svg', { viewBox: `0 0 ${width} ${height}`, role: 'img',
        'aria-label': 'Price history of the options' });

    for (let v = 0; v <= top + 1e-9; v += step) {
        chart.append(svg('line', { x1: pad.left, x2: pad.left + plotW, y1: y(v), y2: y(v),
            stroke: '#eceef1', 'stroke-width': 1 }));
        chart.append(svg('text', { x: width - pad.right + 8, y: y(v) + 4, 'font-size': 11, fill: '#666' },
            v.toFixed(2)));
    }

    for (const s of series) {
        if (s.points.length === 0) continue;
        const coords = s.points.map((p, i) => `${x(i)},${y(p)}`).join(' ');
        chart.append(svg('polyline', { points: coords, fill: 'none', stroke: s.color, 'stroke-width': 2.5,
            'stroke-linejoin': 'round', 'stroke-linecap': 'round' }));
        const lastIndex = s.points.length - 1;
        const marker = svg('circle', { cx: x(lastIndex), cy: y(s.points[lastIndex]), r: 4.5,
            fill: s.color, stroke: '#fff', 'stroke-width': 2 });
        marker.append(svg('title', {}, `${s.name}: $${s.points[lastIndex].toFixed(2)} per share`));
        chart.append(marker);
    }

    container.append(chart);
}
