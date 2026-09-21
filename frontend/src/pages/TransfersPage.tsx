import { Navigate } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/** /transfers lands on FT; products are chosen from the expandable sidebar. */
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

  return <Navigate to="/transfers/ft" replace />;
}
