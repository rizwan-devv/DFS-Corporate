import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';

export function BalancePage() {
  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Account balance"
        subtitle="Parent wallet and child agent balances — live AgentApp data will wire here next."
        actions={
          <Link className="btn btn-ghost btn-sm" to="/statement">
            View statement
          </Link>
        }
      />

      <FinanceSlideshow />

      <div className="stat-ribbon animate-in animate-in-delay-1">
        <article className="stat-tile stat-tile--primary">
          <span className="stat-label">Available balance</span>
          <strong className="stat-value">PKR 1,284,500.00</strong>
          <span className="stat-hint">Demo preview · API pending</span>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Commission earned</span>
          <strong className="stat-value">PKR 86,240.00</strong>
          <span className="stat-hint">Ledger wallet</span>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Pending settlement</span>
          <strong className="stat-value">PKR 12,400.00</strong>
          <span className="stat-hint">In review</span>
        </article>
      </div>

      <div className="portal-grid-2 animate-in animate-in-delay-2">
        <section className="glass-panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Parent account</h2>
              <p className="muted panel-subtitle">Your corporate wallet overview</p>
            </div>
            <span className="chip chip-success">Active</span>
          </div>
          <dl className="detail-list">
            <div><dt>Account name</dt><dd>DFS Corporate Merchant</dd></div>
            <div><dt>Account ID</dt><dd className="mono">•••• 4821</dd></div>
            <div><dt>Currency</dt><dd>PKR</dd></div>
            <div><dt>Last synced</dt><dd>Design preview</dd></div>
          </dl>
        </section>

        <section className="glass-panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Quick actions</h2>
              <p className="muted panel-subtitle">Navigate related finance views</p>
            </div>
          </div>
          <div className="action-stack">
            <Link className="action-row" to="/statement">
              <span>Mini statement</span>
              <span className="muted">Recent movements</span>
            </Link>
            <Link className="action-row" to="/transactions">
              <span>Transactions</span>
              <span className="muted">Commission ledger</span>
            </Link>
            <Link className="action-row" to="/cards">
              <span>Linked cards</span>
              <span className="muted">CMS-style details</span>
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
