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
        subtitle="Select a product to continue."
      />

      <div className="transfer-menu" role="menu" aria-label="Transfer products">
        {TRANSFER_PRODUCTS.map((p) => (
          <Link key={p.id} to={p.path} className="transfer-menu-item" role="menuitem">
            <span className="transfer-menu-code">{p.short}</span>
            <span className="transfer-menu-body">
              <span className="transfer-menu-title">{p.title}</span>
              <span className="transfer-menu-blurb">{p.blurb}</span>
            </span>
            <span className="transfer-menu-arrow" aria-hidden>→</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
