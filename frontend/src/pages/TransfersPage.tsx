import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { useAuth } from '../auth/AuthContext';
import { TRANSFER_PRODUCTS } from '../lib/transferTypes';

export function TransfersPage() {
  const { session } = useAuth();

  if (!session) {
    return (
      <div className="portal-page">
        <PageHeader
          eyebrow="Transfers"
          title="Login required"
          subtitle="Sign in as an ACTIVE corporate to use transfers."
        />
        <Link className="btn btn-primary" to="/login">
          Login
        </Link>
      </div>
    );
  }

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Transfers"
        subtitle="Choose a payment rail. Each product has its own workspace — single, bulk where supported, and history."
        actions={
          <Link className="btn btn-ghost btn-sm" to="/beneficiaries">
            Beneficiaries
          </Link>
        }
      />

      <div className="alert alert-info" style={{ marginBottom: '1.25rem' }}>
        <strong>Mock mode</strong> — portal demos only. No live AgentApp settlement.
      </div>

      <div className="transfer-hub-grid">
        {TRANSFER_PRODUCTS.map((p) => (
          <Link key={p.id} to={p.path} className="transfer-hub-card">
            <span className="transfer-hub-code">{p.short}</span>
            <h3>{p.title}</h3>
            <p>{p.blurb}</p>
            <span className="transfer-hub-meta">
              {p.bulk ? 'Single · Bulk CSV' : 'Single · QR'}
            </span>
          </Link>
        ))}
        <Link to="/beneficiaries" className="transfer-hub-card transfer-hub-card--accent">
          <span className="transfer-hub-code">BEN</span>
          <h3>Beneficiaries</h3>
          <p>Maintain payees once, then select them on FT, IBFT, and Raast.</p>
          <span className="transfer-hub-meta">Add · Edit · Deactivate</span>
        </Link>
      </div>
    </div>
  );
}
