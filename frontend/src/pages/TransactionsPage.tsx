import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';

const txns = [
  { id: 'TXN-9F21', child: 'Karachi Retail Hub', ref: 'EXT-88421', gross: '45,000', share: '4,500', status: 'Posted' },
  { id: 'TXN-9F18', child: 'Lahore Express', ref: 'EXT-88390', gross: '22,100', share: '2,210', status: 'Posted' },
  { id: 'TXN-9F11', child: 'Islamabad Mart', ref: 'EXT-88201', gross: '18,400', share: '1,840', status: 'Reversed' },
  { id: 'TXN-9F05', child: 'Multan Partners', ref: 'EXT-88112', gross: '31,250', share: '3,125', status: 'Posted' },
];

export function TransactionsPage() {
  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Transactions"
        subtitle="Commission ledger and franchise settlement activity — wired to /api/franchises/transactions later."
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Volume', title: 'Today at a glance', body: 'Posted count, gross volume, and your commission share.' },
          { accent: 'Children', title: 'Franchise settlements', body: 'Each row ties to a child partner and external ref.' },
          { accent: 'Control', title: 'Post & reverse', body: 'Operator actions land here when ledger APIs connect.' },
        ]}
      />

      <div className="stat-ribbon animate-in animate-in-delay-1">
        <article className="stat-tile">
          <span className="stat-label">Posted today</span>
          <strong className="stat-value">12</strong>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Volume</span>
          <strong className="stat-value">PKR 116.7k</strong>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Your share</span>
          <strong className="stat-value">PKR 11.7k</strong>
        </article>
      </div>

      <section className="glass-panel animate-in animate-in-delay-2">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Commission ledger</h2>
            <p className="muted panel-subtitle">Sample transactions for UI validation</p>
          </div>
          <button type="button" className="btn btn-primary btn-sm" disabled>
            Post transaction
          </button>
        </div>

        <div className="txn-list">
          {txns.map((t, i) => (
            <article className="txn-card" key={t.id} style={{ animationDelay: `${0.05 * i}s` }}>
              <div className="txn-card-main">
                <div className="txn-id mono">{t.id}</div>
                <h3>{t.child}</h3>
                <p className="muted">Ref {t.ref}</p>
              </div>
              <div className="txn-card-meta">
                <div>
                  <span className="stat-label">Gross</span>
                  <strong className="mono">PKR {t.gross}</strong>
                </div>
                <div>
                  <span className="stat-label">Share</span>
                  <strong className="mono text-success">PKR {t.share}</strong>
                </div>
                <span className={`chip ${t.status === 'Posted' ? 'chip-success' : 'chip-warn'}`}>
                  {t.status}
                </span>
              </div>
            </article>
          ))}
        </div>
        <p className="api-banner">API integration pending — design preview only.</p>
      </section>
    </div>
  );
}
