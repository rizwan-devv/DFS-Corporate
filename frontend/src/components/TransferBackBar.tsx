import { Link } from 'react-router-dom';

/** Back control used on product transfer pages */
export function TransferBackBar({ label = 'Back to Transfers' }: { label?: string }) {
  return (
    <div className="transfer-back-bar">
      <Link to="/transfers" className="transfer-back-link">
        <span aria-hidden>←</span> {label}
      </Link>
    </div>
  );
}
