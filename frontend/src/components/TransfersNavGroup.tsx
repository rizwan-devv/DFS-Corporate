import { useEffect, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { TRANSFER_PRODUCTS } from '../lib/transferTypes';

type Props = {
  /** Show Beneficiaries under the transfers group */
  includeBeneficiaries?: boolean;
};

export function TransfersNavGroup({ includeBeneficiaries = true }: Props) {
  const location = useLocation();
  const navigate = useNavigate();
  const inTransfers =
    location.pathname === '/transfers'
    || location.pathname.startsWith('/transfers/')
    || location.pathname === '/beneficiaries';
  const [open, setOpen] = useState(inTransfers);

  useEffect(() => {
    if (inTransfers) setOpen(true);
  }, [inTransfers]);

  return (
    <div className={`portal-nav-group ${open ? 'is-open' : ''} ${inTransfers ? 'is-active' : ''}`}>
      <button
        type="button"
        className={`portal-link portal-link--parent ${inTransfers ? 'active' : ''}`}
        aria-expanded={open}
        onClick={() => {
          if (!open) {
            setOpen(true);
            if (!inTransfers) navigate('/transfers');
            return;
          }
          if (location.pathname !== '/transfers') {
            navigate('/transfers');
            return;
          }
          setOpen(false);
        }}
      >
        <span className="portal-link-icon">↗</span>
        <span className="portal-link-label">Transfers</span>
        <span className="portal-link-chevron" aria-hidden>{open ? '▾' : '▸'}</span>
      </button>

      {open && (
        <div className="portal-nav-sub" role="group" aria-label="Transfer products">
          <NavLink to="/transfers" end className="portal-link portal-link--sub">
            <span className="portal-link-icon">☰</span>
            Menu
          </NavLink>
          {TRANSFER_PRODUCTS.map((p) => (
            <NavLink key={p.id} to={p.path} className="portal-link portal-link--sub">
              <span className="portal-link-icon">{p.short.slice(0, 1)}</span>
              {p.short}
            </NavLink>
          ))}
          {includeBeneficiaries && (
            <NavLink to="/beneficiaries" className="portal-link portal-link--sub">
              <span className="portal-link-icon">◎</span>
              Beneficiaries
            </NavLink>
          )}
        </div>
      )}
    </div>
  );
}
