import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';

const rows = [
  { date: '2026-09-14', desc: 'POS settlement — Franchise Karachi', amount: '+12,450.00', type: 'credit' },
  { date: '2026-09-13', desc: 'Commission share — Child L4', amount: '+3,200.00', type: 'credit' },
  { date: '2026-09-12', desc: 'Wallet top-up reversal', amount: '−1,000.00', type: 'debit' },
  { date: '2026-09-11', desc: 'Agent cash-in', amount: '+8,750.00', type: 'credit' },
  { date: '2026-09-10', desc: 'Service fee', amount: '−250.00', type: 'debit' },
];

export function StatementPage() {
  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Account statement"
        subtitle="Parent mini-statement layout. AgentApp miniStatment will populate this grid later."
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Flow', title: 'Credits & debits', body: 'Clear movement history with type chips and mono amounts.' },
          { accent: 'Range', title: '7 / 30 / custom', body: 'Filter pills ready for live date-range queries.' },
          { accent: 'Sync', title: 'AgentApp ready', body: 'Same shape as corporate miniStatment payloads.' },
        ]}
      />

      <section className="glass-panel animate-in animate-in-delay-1">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Recent activity</h2>
            <p className="muted panel-subtitle">Demo rows for layout review</p>
          </div>
          <div className="filter-pills">
            <button type="button" className="pill active">7 days</button>
            <button type="button" className="pill">30 days</button>
            <button type="button" className="pill">Custom</button>
          </div>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Type</th>
                <th className="num">Amount (PKR)</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={`${r.date}-${r.desc}`}>
                  <td className="mono">{r.date}</td>
                  <td>{r.desc}</td>
                  <td>
                    <span className={`chip ${r.type === 'credit' ? 'chip-success' : 'chip-warn'}`}>
                      {r.type}
                    </span>
                  </td>
                  <td className={`num mono ${r.type === 'credit' ? 'text-success' : 'text-danger'}`}>
                    {r.amount}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="api-banner">API integration pending — showing design sample data only.</p>
      </section>
    </div>
  );
}
